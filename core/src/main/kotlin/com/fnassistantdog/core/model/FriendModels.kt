package com.fnassistantdog.core.model

import com.google.gson.annotations.SerializedName

/**
 * 好友域 DTO。字段形状取证自 Glow 的既有实现
 * （Glow-Launcher-2.4.1/src/main/helpers/epic/friends.ts:105-140 读 /summary；
 * :200-224 加好友；:247-271 接受；:224/:271 移除与拒绝）。
 */
data class FriendsSummaryResponse(
    @SerializedName("friends") val friends: List<FriendDto>? = null,
    @SerializedName("incoming") val incoming: List<FriendDto>? = null,
    @SerializedName("outgoing") val outgoing: List<FriendDto>? = null,
    @SerializedName("blocked") val blocked: List<FriendDto>? = null,
    @SerializedName("suggestion") val suggestion: List<FriendDto>? = null,
)

/** /summary 里的条目：accountId + displayName，别名优先显示（Glow 用 alias || displayName）。 */
data class FriendDto(
    @SerializedName("accountId") val accountId: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("alias") val alias: String? = null,
    @SerializedName("favorite") val favorite: Boolean = false,
    @SerializedName("created") val created: String? = null,
) {
    val shownName: String get() = alias?.takeIf { it.isNotBlank() }
        ?: displayName?.takeIf { it.isNotBlank() }
        ?: accountId.orEmpty()
}

/** 用户搜索响应（user-search-service）。 */
data class UserSearchResponse(
    @SerializedName("entities") val entities: List<UserSearchEntity>? = null,
)

data class UserSearchEntity(
    @SerializedName("accountId") val accountId: String? = null,
    @SerializedName("namespace") val namespace: String? = null,
    @SerializedName("subType") val subType: String? = null,
    @SerializedName("description") val description: String? = null,
) {
    val shownName: String get() = description?.takeIf { it.isNotBlank() } ?: accountId.orEmpty()
}
