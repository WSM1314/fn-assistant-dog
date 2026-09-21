package com.fnassistantdog.core.friends

import com.fnassistantdog.core.api.EpicFriendsApi
import com.fnassistantdog.core.api.EpicUserSearchApi
import com.fnassistantdog.core.http.apiCall
import com.fnassistantdog.core.http.apiCallEmpty
import com.fnassistantdog.core.model.FriendDto
import com.fnassistantdog.core.model.UserSearchEntity

/**
 * 好友（F1：Epic friends 端点 + 用户搜索）。
 * 语义照 Glow 已跑通实现：加好友与接受请求同为 POST，移除/拒绝/取消同为 DELETE
 * （Glow-Launcher-2.4.1/src/main/helpers/epic/friends.ts:200-300）。
 */
class FriendsRepository(
    private val api: EpicFriendsApi,
    private val searchApi: EpicUserSearchApi,
) {
    enum class Kind { FRIEND, INCOMING, OUTGOING, BLOCKED }

    data class FriendRow(
        val accountId: String,
        val name: String,
        val favorite: Boolean,
        val created: String?,
        val kind: Kind,
    )

    data class Overview(
        val friends: List<FriendRow> = emptyList(),
        val incoming: List<FriendRow> = emptyList(),
        val outgoing: List<FriendRow> = emptyList(),
        val blocked: List<FriendRow> = emptyList(),
    ) {
        val total: Int get() = friends.size + incoming.size + outgoing.size + blocked.size
    }

    suspend fun load(accountId: String): Overview {
        val data = apiCall("好友列表") { api.summary(accountId) }
        return Overview(
            friends = data.friends.orEmpty().toRows(Kind.FRIEND),
            incoming = data.incoming.orEmpty().toRows(Kind.INCOMING),
            outgoing = data.outgoing.orEmpty().toRows(Kind.OUTGOING),
            blocked = data.blocked.orEmpty().toRows(Kind.BLOCKED),
        )
    }

    /** 发出好友请求 */
    suspend fun sendRequest(accountId: String, targetAccountId: String) {
        apiCallEmpty("发送好友请求") { api.addOrUpdate(accountId, targetAccountId, emptyBody) }
    }

    /** 接受收到的请求（与发请求同一个 POST，语义不同） */
    suspend fun acceptRequest(accountId: String, targetAccountId: String) {
        apiCallEmpty("接受好友请求") { api.addOrUpdate(accountId, targetAccountId, emptyBody) }
    }

    /** 移除好友 / 拒绝收到的请求 / 取消发出的请求，同一个 DELETE */
    suspend fun remove(accountId: String, targetAccountId: String) {
        apiCallEmpty("移除好友") { api.removeOrReject(accountId, targetAccountId) }
    }

    suspend fun search(query: String): List<UserSearchEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val response = apiCall("用户搜索") {
            searchApi.search(
                platform = PLATFORM_EPIC,
                prefix = trimmed,
                displayNamePrefix = trimmed,
                maxResults = MAX_SEARCH_RESULTS,
            )
        }
        return response.entities.orEmpty().filter { !it.accountId.isNullOrBlank() }
    }

    private fun List<FriendDto>.toRows(kind: Kind): List<FriendRow> =
        mapNotNull { dto ->
            val id = dto.accountId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            FriendRow(
                accountId = id,
                name = dto.shownName.ifBlank { id },
                favorite = dto.favorite,
                created = dto.created,
                kind = kind,
            )
        }

    companion object {
        const val PLATFORM_EPIC = "epic"
        const val MAX_SEARCH_RESULTS = 8
        private val emptyBody: Map<String, String> = emptyMap()
    }
}
