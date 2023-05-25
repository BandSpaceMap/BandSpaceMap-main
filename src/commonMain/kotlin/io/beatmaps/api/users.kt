@file:UseSerializers(InstantAsStringSerializer::class)
package io.beatmaps.api

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

enum class AccountType {
    DISCORD, SIMPLE, DUAL
}

@Serializable
data class UserDetail(
    val id: Int,
    val name: String,
    val description: String? = null,
    val uniqueSet: Boolean = true,
    val hash: String? = null,
    val testplay: Boolean? = null,
    val avatar: String,
    val stats: UserStats? = null,
    val followData: UserFollowData? = null,
    val type: AccountType,
    val email: String? = null,
    val uploadLimit: Int? = null,
    val admin: Boolean? = null,
    val curator: Boolean? = null,
    val verifiedMapper: Boolean = false,
    val suspendedAt: Instant? = null,
    val playlistUrl: String? = null
) {
    fun profileLink(tab: String? = null, absolute: Boolean = false) = UserDetailHelper.profileLink(this, tab, absolute)
    companion object
}
expect object UserDetailHelper {
    fun profileLink(userDetail: UserDetail, tab: String?, absolute: Boolean): String
}

@Serializable
data class UserStats(
    val totalUpvotes: Int = 0,
    val totalDownvotes: Int = 0,
    val totalMaps: Int = 0,
    val rankedMaps: Int = 0,
    val avgBpm: Float = 0f,
    val avgScore: Float = 0f,
    val avgDuration: Float = 0f,
    val firstUpload: Instant? = null,
    val lastUpload: Instant? = null,
    val diffStats: UserDiffStats? = null
)
@Serializable
data class UserFollowData(
    val followers: Int,
    val follows: Int?,
    val following: Boolean?
)
@Serializable
data class UserDiffStats(val total: Int, val easy: Int, val normal: Int, val hard: Int, val expert: Int, val expertPlus: Int)
@Serializable
data class BeatsaverLink(val linked: Boolean) { companion object }
@Serializable
data class BeatsaverLinkReq(val user: String, val password: String, val useOldName: Boolean = true)
@Serializable
data class AccountDetailReq(val textContent: String)
@Serializable
data class RegisterRequest(val captcha: String, val username: String, val email: String, val password: String, val password2: String)
@Serializable
data class ActionResponse(val success: Boolean, val errors: List<String> = listOf())
@Serializable
data class ForgotRequest(val captcha: String, val email: String)
@Serializable
data class ResetRequest(val jwt: String, val password: String, val password2: String)
@Serializable
data class AccountRequest(val currentPassword: String? = null, val password: String? = null, val password2: String? = null)
@Serializable
data class UserAdminRequest(val userId: Int, val maxUploadSize: Int, val curator: Boolean, val verifiedMapper: Boolean)
@Serializable
data class UserSuspendRequest(val userId: Int, val suspended: Boolean, val reason: String?)
@Serializable
data class UserFollowRequest(val userId: Int, val followed: Boolean)
