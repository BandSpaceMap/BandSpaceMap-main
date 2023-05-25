package io.beatmaps.controllers

import ch.compile.recaptcha.ReCaptchaVerify
import com.fasterxml.jackson.databind.ObjectWriter
import io.beatmaps.api.FailedUploadResponse
import io.beatmaps.api.handleMultipart
import io.beatmaps.api.requireAuthorization
import io.beatmaps.common.BSPrettyPrinter
import io.beatmaps.common.Config
import io.beatmaps.common.CopyException
import io.beatmaps.common.MapTag
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.beatsaber.MapInfo
import io.beatmaps.common.copyToSuspend
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.db.updateReturning
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Difficulty
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.VersionsDao
import io.beatmaps.common.jackson
import io.beatmaps.common.localAudioFolder
import io.beatmaps.common.localAvatarFolder
import io.beatmaps.common.localCoverFolder
import io.beatmaps.common.localFolder
import io.beatmaps.common.pub
import io.beatmaps.common.zip.ExtractedInfo
import io.beatmaps.common.zip.RarException
import io.beatmaps.common.zip.ZipHelper
import io.beatmaps.common.zip.ZipHelper.Companion.openZip
import io.beatmaps.common.zip.ZipHelperException
import io.beatmaps.common.zip.sharedInsert
import io.beatmaps.genericPage
import io.beatmaps.login.Session
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.locations.post
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.datetime.Clock
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.SerializationException
import net.coobird.thumbnailator.Thumbnails
import net.coobird.thumbnailator.tasks.UnsupportedFormatException
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.lang.Integer.toHexString
import java.math.BigInteger
import java.nio.file.Files
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.logging.Logger
import kotlin.collections.set
import kotlin.math.roundToInt

val jsonWriter: ObjectWriter = jackson.writer(BSPrettyPrinter())

val uploadDir = File(System.getenv("UPLOAD_DIR") ?: "S:\\A")
val allowUploads = System.getenv("ALLOW_UPLOADS") != "false"
val reCaptchaVerify = System.getenv("RECAPTCHA_SECRET")?.let { ReCaptchaVerify(it) }

@Location("/upload")
class UploadMap
@Location("/avatar")
class UploadAvatar

private val uploadLogger = Logger.getLogger("bmio.Upload")

fun Route.uploadController() {
    get<UploadMap> {
        if (call.sessions.get<Session>().let { it == null || it.suspended }) {
            call.respondRedirect("/")
        } else {
            genericPage()
        }
    }

    post<UploadAvatar> {
        requireAuthorization { sess ->
            try {
                val multipart = call.receiveMultipart()
                val dataMap = mutableMapOf<String, String>()

                val filename = "${sess.userId}.jpg"
                val localFile = File(localAvatarFolder(), filename)

                multipart.forEachPart { part ->
                    if (part is PartData.FormItem) {
                        dataMap[part.name.toString()] = part.value
                    } else if (part is PartData.FileItem) {
                        part.streamProvider().use { its ->
                            Thumbnails
                                .of(its)
                                .size(128, 128)
                                .outputFormat("JPEG")
                                .outputQuality(0.8)
                                .toFile(localFile)

                            transaction {
                                User.update({ User.id eq sess.userId }) {
                                    it[avatar] = "${Config.cdnBase("", true)}/avatar/$filename"
                                }
                            }
                        }
                    }
                }

                call.respond(HttpStatusCode.OK)
            } catch (e: UnsupportedFormatException) {
                throw UploadException("Bad or unknown image format")
            }
        }
    }

    post<UploadMap> {
        val session = call.sessions.get<Session>() ?: throw UploadException("Not logged in")
        val (currentLimit, nameSet, suspended) = transaction {
            val user = UserDao[session.userId]
            Triple(user.uploadLimit, (user.active && user.uniqueName != null), user.suspendedAt != null)
        }

        // Throw error if user is missing a username
        nameSet || throw UploadException("Please pick a username to complete your account")

        !suspended || throw UploadException("Suspended account")

        val file = File(
            uploadDir,
            "upload-${System.currentTimeMillis()}-${session.userId.hashCode()}.zip"
        )

        val md = MessageDigest.getInstance("SHA1")
        var extractedInfoTmp: ExtractedInfo? = null

        val multipart = call.handleMultipart { part ->
            uploadLogger.info("Upload of '${part.originalFileName}' started by '${session.uniqueName}' (${session.userId})")
            extractedInfoTmp = part.streamProvider().use { its ->
                try {
                    file.outputStream().buffered().use {
                        its.copyToSuspend(it, sizeLimit = currentLimit * 1024 * 1024)
                    }.run {
                        // Check if the file exists
                        DigestOutputStream(OutputStream.nullOutputStream(), md).use { dos ->
                            openZip(file) {
                                validateFiles(dos)
                            }
                        }
                    }
                } catch (e: RarException) {
                    file.delete()
                    throw UploadException("Don't upload rar files. Use the package button in your map editor.")
                } catch (e: SerializationException) {
                    e.printStackTrace()
                    file.delete()
                    throw UploadException("Could not parse json")
                } catch (e: ZipHelperException) {
                    file.delete()
                    throw UploadException(e.msg)
                } catch (e: CopyException) {
                    file.delete()
                    throw UploadException("Zip file too big")
                } catch (e: Exception) {
                    file.delete()
                    throw e
                }
            }
        }

        multipart.recaptchaSuccess || throw UploadException("Missing recaptcha?")
        val newMapId = transaction {
            // Process upload
            val fx = "%0" + md.digestLength * 2 + "x"
            val digest = String.format(fx, BigInteger(1, md.digest()))
            val newFile = File(localFolder(digest), "$digest.zip")
            val newImageFile = File(localCoverFolder(digest), "$digest.jpg")
            val newAudioFile = File(localAudioFolder(digest), "$digest.mp3")

            val existsAlready = Versions.select {
                Versions.hash eq digest
            }.count() > 0

            val extractedInfo = extractedInfoTmp ?: throw UploadException("Internal error 1")

            if (existsAlready) {
                file.delete()
                throw UploadException("Map already uploaded")
            }

            if (!session.testplay && !allowUploads) {
                file.delete()
                throw UploadException("Your map is fine but we're not accepting uploads yet")
            }

            fun setBasicMapInfo(
                setFloat: (column: Column<Float>, value: Float) -> Unit,
                setInt: (column: Column<Int>, value: Int) -> Unit,
                setString: (column: Column<String>, value: String) -> Unit
            ) {
                setFloat(Beatmap.bpm, extractedInfo.mapInfo._beatsPerMinute)
                setInt(Beatmap.duration, extractedInfo.duration.roundToInt())
                setString(Beatmap.songName, extractedInfo.mapInfo._songName)
                setString(Beatmap.songSubName, extractedInfo.mapInfo._songSubName)
                setString(Beatmap.levelAuthorName, extractedInfo.mapInfo._levelAuthorName)
                setString(Beatmap.songAuthorName, extractedInfo.mapInfo._songAuthorName)
            }

            val newMap = try {
                fun insertOrUpdate() =
                    multipart.dataMap["mapId"]?.toInt()?.let { mapId ->
                        fun updateIt() = Beatmap.updateReturning(
                            {
                                (Beatmap.id eq mapId) and (Beatmap.uploader eq session.userId)
                            },
                            {
                                setBasicMapInfo({ a, b -> it[a] = b }, { a, b -> it[a] = b }, { a, b -> it[a] = b })
                                it[updatedAt] = NowExpression(updatedAt.columnType)
                            },
                            Beatmap.id
                        )?.firstOrNull()?.let { it[Beatmap.id] } ?: throw UploadException("Map doesn't exist to add version")

                        updateIt().also {
                            val latestVersions = VersionsDao.wrapRows(
                                Versions.select {
                                    (Versions.mapId eq mapId)
                                }.orderBy(Versions.uploaded, SortOrder.DESC).limit(2)
                            ).toList()

                            if (latestVersions.size > 1) {
                                // Check time since one before previous upload = 2 uploads / day / map
                                val hoursUntilNext = 12 - Clock.System.now().minus(latestVersions[1].uploaded.toKotlinInstant()).inWholeHours
                                if (hoursUntilNext > 0) {
                                    throw UploadException("Please wait another $hoursUntilNext hours before uploading another version")
                                }
                            }
                        }
                    } ?: Beatmap.insertAndGetId {
                        it[name] = (multipart.dataMap["title"] ?: "").take(1000)
                        it[description] = (multipart.dataMap["description"] ?: "").take(10000)

                        val tagsList = (multipart.dataMap["tags"] ?: "").split(',').mapNotNull { t -> MapTag.fromSlug(t) }.toSet()
                        val tooMany = tagsList.groupBy { t -> t.type }.mapValues { t -> t.value.size }.withDefault { 0 }.let { byType ->
                            MapTag.maxPerType.any { type -> byType.getValue(type.key) > type.value }
                        }

                        if (!tooMany) {
                            it[tags] = tagsList.filter { t -> t != MapTag.None }.map { t -> t.slug }.toTypedArray()
                        }
                        it[uploader] = EntityID(session.userId, User)

                        setBasicMapInfo({ a, b -> it[a] = b }, { a, b -> it[a] = b }, { a, b -> it[a] = b })

                        val declaredAsAI = (multipart.dataMap["beatsage"] ?: "").isNotEmpty()
                        it[automapper] = declaredAsAI || extractedInfo.score < -4
                        it[ai] = declaredAsAI

                        it[plays] = 0
                    }

                insertOrUpdate().also {
                    // How is a file here if it hasn't been uploaded before?
                    if (newFile.exists()) {
                        newFile.delete()
                    }

                    newFile.parentFile?.mkdirs()
                    Files.move(file.toPath(), newFile.toPath())
                }
            } catch (e: Exception) {
                file.delete()
                throw e
            }
            try {
                extractedInfo.thumbnail?.let {
                    newImageFile.parentFile?.mkdirs()
                    newImageFile.writeBytes(it.toByteArray())
                } ?: throw UploadException("Internal error 2")

                extractedInfo.preview?.let {
                    newAudioFile.parentFile?.mkdirs()
                    newAudioFile.writeBytes(it.toByteArray())
                } ?: throw UploadException("Internal error 3")

                // Pretty much guaranteed to be set
                val sli = extractedInfo.songLengthInfo ?: throw UploadException("Couldn't determine song length")
                val newVersion = Versions.insertAndGetId {
                    it[mapId] = newMap
                    it[key64] = null
                    it[hash] = digest
                    it[state] = EMapState.Uploaded
                    it[sageScore] = extractedInfo.score
                }

                extractedInfo.diffs.forEach { cLoop ->
                    cLoop.value.forEach { dLoop ->
                        val diffInfo = dLoop.key
                        val bsdiff = dLoop.value

                        Difficulty.insertAndGetId {
                            it[mapId] = newMap
                            it[versionId] = newVersion

                            sharedInsert(it, diffInfo, bsdiff, extractedInfo.mapInfo, sli)
                            it[characteristic] = cLoop.key.enumValue()
                            it[difficulty] = dLoop.key.enumValue()
                        }
                    }
                }
                newMap.value
            } catch (e: Exception) {
                if (newFile.exists()) newFile.delete()
                if (newImageFile.exists()) newImageFile.delete()
                if (newAudioFile.exists()) newAudioFile.delete()
                throw e
            }
        }
        call.pub("beatmaps", "maps.$newMapId.updated.upload", null, newMapId)
        call.respond(toHexString(newMapId))
    }
}

fun ZipHelper.validateFiles(dos: DigestOutputStream) =
    info.let {
        // Add files referenced in info.dat to whitelist
        ExtractedInfo(findAllowedFiles(it), dos, it, scoreMap())
    }.also { p ->
        // Rename audio file if it ends in .ogg
        val (newFiles, newFilesOriginalCase) = oggToEgg(p)

        // Ensure it ends in a slash
        val prefix = infoPrefix()
        val withoutPrefix = newFiles.map { its -> its.removePrefix(prefix.lowercase()) }.toSet()

        jsonWriter.writeValue(dos, p.mapInfo)
        // Validate info.dat
        p.mapInfo.validate(withoutPrefix, p, audioFile, ::fromInfo)
        // Generate 10 second preview
        p.preview = ByteArrayOutputStream().also {
            it.writeBytes(generatePreview())
        }
        // Write updated info.dat back to zip
        infoPath.deleteIfExists()
        getPathDirect("/Info.dat").outputStream().use {
            jsonWriter.writeValue(it, p.mapInfo)
        }
        // Delete any extra files in the zip (like autosaves)
        val paritioned = newFilesOriginalCase.filter { !it.endsWith("/Info.dat", true) }.partition {
            val originalWithoutPrefix = it.lowercase().removePrefix(prefix.lowercase())
            !p.allowedFiles.contains(originalWithoutPrefix)
        }

        paritioned.first.forEach {
            getPathDirect(it).deleteIfExists()
        }
        // Move files to root
        if (prefix.length > 1) {
            // Files in subfolder!
            paritioned.second.forEach {
                moveFile(getPathDirect(it), "/" + it.removePrefix(prefix))
            }
            directories.filter { it.startsWith(prefix) }.sortedBy { it.length }.forEach {
                getPathDirect(it).deleteIfExists()
            }
        }
    }

fun findAllowedFiles(info: MapInfo) = (
    listOf("info.dat", "bpminfo.dat", "cinema-video.json", info._coverImageFilename, info._songFilename) +
        (info._customData?._contributors?.mapNotNull { it._iconPath } ?: listOf()) +
        info._difficultyBeatmapSets.flatMap { set -> set._difficultyBeatmaps.map { it._beatmapFilename } }
    ).map { it.lowercase() }

fun ZipHelper.oggToEgg(info: ExtractedInfo) =
    fromInfo(info.mapInfo._songFilename.lowercase())?.let { path ->
        if (info.mapInfo._songFilename.endsWith(".ogg")) {
            val originalAudioName = info.mapInfo._songFilename
            info.mapInfo = info.mapInfo.copy(_songFilename = originalAudioName.replace(Regex("\\.ogg$"), ".egg"))
            moveFile(path, "/${info.mapInfo._songFilename}")
            files.minus((infoPrefix() + originalAudioName).lowercase())
                .plus((infoPrefix() + info.mapInfo._songFilename).lowercase()) to filesOriginalCase.minus(infoPrefix() + originalAudioName)
        } else {
            null
        }
    } ?: (files to filesOriginalCase)

class UploadException(private val msg: String) : RuntimeException() {
    fun toResponse() = FailedUploadResponse(listOf(msg))
}
