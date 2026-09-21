package com.fnassistantdog.core.api

import com.fnassistantdog.core.model.FriendsSummaryResponse
import com.fnassistantdog.core.model.UserSearchResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Epic 好友服务。基址 .../friends/api/v1/，路径与方法语义照 Glow 已跑通的实现平移。
 */
interface EpicFriendsApi {
    /** 好友 / 收到的请求 / 发出的请求 / 黑名单，一次拿全 */
    @GET("{accountId}/summary")
    suspend fun summary(@Path("accountId") accountId: String): Response<FriendsSummaryResponse>

    /** 发请求与"接受收到的请求"是同一个 POST（Glow addFriend 与 acceptFriend 走同一行代码） */
    @POST("{accountId}/friends/{targetAccountId}")
    suspend fun addOrUpdate(
        @Path("accountId") accountId: String,
        @Path("targetAccountId") targetAccountId: String,
        @Body body: Map<String, String>,
    ): Response<Void>

    /** 移除好友 / 拒绝收到的请求 / 取消发出的请求，同一个 DELETE */
    @DELETE("{accountId}/friends/{targetAccountId}")
    suspend fun removeOrReject(
        @Path("accountId") accountId: String,
        @Path("targetAccountId") targetAccountId: String,
    ): Response<Void>
}

/** 基址 .../api/v1/search/，path 段是平台（原 App 用 epic）。 */
interface EpicUserSearchApi {
    @GET("{platform}")
    suspend fun search(
        @Path("platform") platform: String,
        @Query("prefix") prefix: String,
        @Query("displayNamePrefix") displayNamePrefix: String,
        @Query("maxResults") maxResults: Int,
    ): Response<UserSearchResponse>
}
