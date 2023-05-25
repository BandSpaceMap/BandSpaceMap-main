package io.beatmaps.api.scores

import io.beatmaps.api.LeaderboardData
import io.beatmaps.api.LeaderboardScore
import io.beatmaps.common.SSGameMode
import io.beatmaps.common.api.EDifficulty
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Instant

class ScoreSaberScores : RemoteScores {
    private suspend fun getLeaderboardInfo(hash: String, diff: EDifficulty = EDifficulty.ExpertPlus, mode: SSGameMode = SSGameMode.SoloStandard) =
        ssTry {
            scoresClient.get(
                "https://scoresaber.com/api/leaderboard/by-hash/$hash/info"
            ) {
                parameter("difficulty", diff.idx)
                parameter("gameMode", mode)
            }.body<SSLeaderboardInfo>()
        }

    private suspend fun getScores(hash: String, diff: EDifficulty = EDifficulty.ExpertPlus, mode: SSGameMode = SSGameMode.SoloStandard, page: Int = 1) =
        ssTry {
            scoresClient.get(
                "https://scoresaber.com/api/leaderboard/by-hash/$hash/scores"
            ) {
                parameter("difficulty", diff.idx)
                parameter("gameMode", mode)
                parameter("page", page)
            }.body<SSPaged>().scores
        }

    override suspend fun getLeaderboard(hash: String, diff: EDifficulty, mode: SSGameMode, page: Int) =
        coroutineScope {
            val leaderboard = async {
                getLeaderboardInfo(hash, diff, mode)
            }
            val scores = async {
                getScores(hash, diff, mode, page)
            }

            val (al, ascores) = leaderboard.await() to scores.await()
            if (al != null && ascores != null) {
                al.toLeaderboardData(ascores)
            } else {
                LeaderboardData.EMPTY
            }
        }
}

val tagsRegex = Regex("(<([^>]+)>)")

data class SSLeaderboardPlayer(
    val id: String,
    val name: String,
    val profilePicture: String,
    val country: String,
    val permissions: Int,
    val badges: String?,
    val role: String?
)

data class SSLeaderboardInfo(
    val id: Int,
    val songHash: String,
    val songName: String,
    val songSubName: String,
    val songAuthorName: String,
    val levelAuthorName: String,
    val difficulty: SSLeaderboardInfoDiff,
    val maxScore: Int,
    val createdDate: Instant,
    val rankedDate: Instant?,
    val qualifiedDate: Instant?,
    val lovedDate: Instant?,
    val ranked: Boolean,
    val qualified: Boolean,
    val loved: Boolean,
    val maxPP: Int,
    val stars: Float,
    val plays: Int,
    val dailyPlays: Int,
    val positiveModifiers: Boolean,
    val playerScore: SSLeaderboardScore?,
    val difficulties: List<SSLeaderboardInfoDiff>
) {
    fun toLeaderboardData(scores: List<SSLeaderboardScore>) = LeaderboardData(
        ranked, id.toString(),
        scores.map(SSLeaderboardScore::toLeaderboardScore).filter { it.playerId > 9000 },
        positiveModifiers, true
    )
}

data class SSPaged(val metadata: SSPagedMetadata, val scores: List<SSLeaderboardScore>)
data class SSPagedMetadata(val total: Int, val page: Int, val itemsPerPage: Int)

data class SSLeaderboardInfoDiff(val leaderboardId: Int, val difficulty: Int, val gameMode: String, val difficultyRaw: String)
data class SSLeaderboardScore(
    val id: Long,
    val leaderboardPlayerInfo: SSLeaderboardPlayer,
    val rank: Int,
    val baseScore: Int,
    val modifiedScore: Int,
    val pp: Double,
    val weight: Double,
    val modifiers: String,
    val multiplier: Float,
    val badCuts: Int,
    val missedNotes: Int,
    val maxCombo: Int,
    val fullCombo: Boolean,
    val hmd: Int,
    val timeSet: String,
    val hasReplay: Boolean
) {
    fun toLeaderboardScore() = LeaderboardScore(leaderboardPlayerInfo.id.toLong(), leaderboardPlayerInfo.name.replace(tagsRegex, ""), rank, modifiedScore, pp, modifiers.split(',').filter { it.isNotEmpty() })
}
