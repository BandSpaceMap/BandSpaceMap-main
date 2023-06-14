package io.beatmaps.util

import io.beatmaps.api.ReviewUpdateInfo
import io.beatmaps.api.UserDetail
import io.beatmaps.api.reviewToComplex
import io.beatmaps.common.Config
import io.beatmaps.common.client
import io.beatmaps.common.consumeAck
import io.beatmaps.common.db.avgWithFilter
import io.beatmaps.common.db.countWithFilter
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Review
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.VersionsDao
import io.beatmaps.common.dbo.joinCurator
import io.beatmaps.common.dbo.joinUploader
import io.beatmaps.common.dbo.joinVersions
import io.beatmaps.common.dbo.reviewerAlias
import io.beatmaps.common.rabbitOptional
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.userAgent
import io.ktor.server.application.Application
import org.jetbrains.exposed.sql.ExpressionWithColumnType
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.decimalLiteral
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal

data class DiscordWebhookBody(
    val content: String? = null,
    val username: String? = null,
    val avatar_url: String? = null,
    val tts: Boolean? = false,
    val embeds: List<DiscordEmbed>? = null
)

data class DiscordEmbed(
    val title: String? = null,
    val description: String? = null,
    val url: String? = null,
    val color: Int? = null,
    val footer: Footer? = null,
    val thumbnail: HasUrl? = null,
    val image: HasUrl? = null,
    val author: Author? = null,
    val fields: List<Field>? = null
) {
    data class Footer(
        val text: String? = null,
        val icon_url: String? = null
    )
    data class HasUrl(
        val url: String? = null
    )
    data class Author(
        val name: String? = null,
        val url: String? = null,
        val icon_url: String? = null
    ) {
        constructor(user: UserDetail) : this(user.name, user.profileLink(absolute = true), user.avatar)
    }
    data class Field(
        val name: String? = null,
        val value: String? = null,
        val inline: Boolean? = null
    )
}

val discordWebhookUrl: String? = System.getenv("DISCORD_WEBHOOK_URL")

fun truncateWithEllipsis(text: String, maxLength: Int, ellipsis: String = "...") =
    if (text.length > maxLength) {
        text.take(maxLength - ellipsis.length) + ellipsis
    } else text

fun Application.reviewListeners() {
    rabbitOptional {
        val avg = Review.sentiment.avgWithFilter(Review.deletedAt.isNull(), 3).alias("sentiment")
        val count = Review.sentiment.countWithFilter(Review.deletedAt.isNull()).alias("reviews")
        val reviewSubquery = Review.slice(avg, count, Review.mapId).selectAll().groupBy(Review.mapId).alias("r")

        consumeAck("bm.sentiment", ReviewUpdateInfo::class) { _, r ->
            transaction {
                Beatmap
                    .join(reviewSubquery, JoinType.INNER, Beatmap.id, reviewSubquery[Review.mapId])
                    .update({ Beatmap.id eq r.mapId }) {
                        it[Beatmap.sentiment] = SqlExpressionBuilder.coalesce(reviewSubquery[avg] as ExpressionWithColumnType<BigDecimal?>, decimalLiteral(BigDecimal.ZERO))
                        it[Beatmap.reviews] = reviewSubquery[count]
                    }
            }
        }

        val maxTitleLen = 100 // 256 max
        val maxReviewLen = 1024 // 1024 max
        discordWebhookUrl?.let { webhookUrl ->
            consumeAck("bm.reviewDiscordHook", ReviewUpdateInfo::class) { _, r ->
                transaction {
                    Review
                        .join(reviewerAlias, JoinType.INNER, Review.userId, reviewerAlias[User.id])
                        .join(Beatmap, JoinType.INNER, Review.mapId, Beatmap.id)
                        .joinVersions()
                        .joinUploader()
                        .joinCurator()
                        .select {
                            Review.mapId eq r.mapId and (Review.userId eq r.userId)
                        }.singleOrNull()?.let { row ->
                            reviewToComplex(row, "") to VersionsDao.wrapRow(row)
                        }
                }?.let { (review, version) ->
                    client.post(webhookUrl) {
                        contentType(ContentType.Application.Json)
                        userAgent("BeatSaver")

                        setBody(
                            DiscordWebhookBody(
                                username = "BeatSaver",
                                avatar_url = "https://avatars.githubusercontent.com/u/83342266",
                                embeds = listOf(
                                    DiscordEmbed(
                                        author = review.creator?.let { user -> DiscordEmbed.Author(user) },
                                        title = review.map?.name?.let {
                                            truncateWithEllipsis(it, maxTitleLen)
                                        },
                                        url = review.map?.let {
                                            "${Config.siteBase()}/maps/${it.id}"
                                        },
                                        thumbnail = DiscordEmbed.HasUrl("${Config.cdnBase("", true)}/${version.hash}.jpg"),
                                        fields = listOf(
                                            DiscordEmbed.Field(
                                                "Review",
                                                truncateWithEllipsis(review.text, maxReviewLen)
                                            ),
                                            DiscordEmbed.Field(
                                                "Sentiment",
                                                review.sentiment.let { s -> "${s.emoji} ${s.name}" },
                                                true
                                            )
                                        ),
                                        color = 6973358
                                    )
                                )
                            )
                        )
                    }
                }
            }
        }
    }
}
