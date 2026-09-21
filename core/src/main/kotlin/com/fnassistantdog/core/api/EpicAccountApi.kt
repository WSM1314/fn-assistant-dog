package com.fnassistantdog.core.api

import com.fnassistantdog.core.model.EpicAccountResponse
import com.fnassistantdog.core.model.EpicDeviceAuthResponse
import com.fnassistantdog.core.model.EpicTokenResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Epic 账号与 OAuth。路径模板取自 dex 常量池：
 * `oauth/token`、`public/account/{accountId}/deviceAuth`、`.../deviceAuth/{deviceId}`。
 */
interface EpicAccountApi {
    /** 授权码换 token（用户在浏览器拿到 code 后粘贴进 App） */
    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun tokenByAuthorizationCode(
        @Header("Authorization") basicAuth: String,
        @Field("grant_type") grantType: String,
        @Field("code") code: String,
    ): Response<EpicTokenResponse>

    /** 设备凭据刷新 token（grant_type=device_auth，原 App 的常驻刷新方式） */
    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun tokenByDeviceAuth(
        @Header("Authorization") basicAuth: String,
        @Field("grant_type") grantType: String,
        @Field("device_id") deviceId: String,
        @Field("device_secret") deviceSecret: String,
    ): Response<EpicTokenResponse>

    @GET("public/account/{accountId}")
    suspend fun account(
        @Header("Authorization") bearerAuth: String,
        @Path("accountId") accountId: String,
    ): Response<EpicAccountResponse>

    /** 创建设备凭据；响应里 secret 字段两种写法都收（见 EpicDeviceAuthResponse） */
    @POST("public/account/{accountId}/deviceAuth")
    suspend fun createDeviceAuth(
        @Header("Authorization") bearerAuth: String,
        @Path("accountId") accountId: String,
        @Body body: Map<String, String>,
    ): Response<EpicDeviceAuthResponse>

    /** 删除设备凭据（本地删号时尽量回收，失败不阻塞删除） */
    @DELETE("public/account/{accountId}/deviceAuth/{deviceId}")
    suspend fun deleteDeviceAuth(
        @Header("Authorization") bearerAuth: String,
        @Path("accountId") accountId: String,
        @Path("deviceId") deviceId: String,
    ): Response<Void>
}
