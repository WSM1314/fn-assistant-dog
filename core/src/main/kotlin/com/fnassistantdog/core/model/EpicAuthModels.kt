package com.fnassistantdog.core.model

import com.google.gson.annotations.SerializedName

/**
 * Epic OAuth 与账号侧 DTO。
 *
 * 字段名宽容处理：同一个值在 Epic 不同端点上出现过 snake_case 与 camelCase 两种写法
 * （原版 dex 里 `account_id`/`accountId`、`display_name`/`displayName` 两组字符串同时存在），
 * 因此每对写法都建字段，再用一个 resolved 取值，避免臆测单一命名。
 */
data class EpicTokenResponse(
    @SerializedName("access_token") val accessTokenSnake: String? = null,
    @SerializedName("accessToken") val accessTokenCamel: String? = null,
    @SerializedName("token_type") val tokenTypeSnake: String? = null,
    @SerializedName("tokenType") val tokenTypeCamel: String? = null,
    @SerializedName("expires_in") val expiresIn: Long? = null,
    @SerializedName("expiresAt") val expiresAt: String? = null,
    @SerializedName("refresh_token") val refreshTokenSnake: String? = null,
    @SerializedName("refreshToken") val refreshTokenCamel: String? = null,
    @SerializedName("refresh_expires_in") val refreshExpiresIn: Long? = null,
    @SerializedName("account_id") val accountIdSnake: String? = null,
    @SerializedName("accountId") val accountIdCamel: String? = null,
    @SerializedName("display_name") val displayNameSnake: String? = null,
    @SerializedName("displayName") val displayNameCamel: String? = null,
    @SerializedName("client_id") val clientId: String? = null,
    @SerializedName("granted_scopes") val grantedScopes: List<String>? = null,
) {
    val accessToken: String? get() = accessTokenSnake ?: accessTokenCamel
    val tokenType: String? get() = tokenTypeSnake ?: tokenTypeCamel
    val refreshToken: String? get() = refreshTokenSnake ?: refreshTokenCamel
    val accountId: String? get() = accountIdSnake ?: accountIdCamel
    val displayName: String? get() = displayNameCamel ?: displayNameSnake
}

/** POST public/account/{accountId}/deviceAuth 的响应；secret 字段两种写法都收。 */
data class EpicDeviceAuthResponse(
    @SerializedName("deviceId") val deviceId: String? = null,
    @SerializedName("device_id") val deviceIdSnake: String? = null,
    @SerializedName("secret") val secret: String? = null,
    @SerializedName("deviceSecret") val deviceSecret: String? = null,
    @SerializedName("note") val note: String? = null,
    @SerializedName("created") val created: String? = null,
    @SerializedName("clientId") val clientId: String? = null,
) {
    val resolvedDeviceId: String? get() = deviceId ?: deviceIdSnake
    val resolvedSecret: String? get() = secret ?: deviceSecret
}

/** GET public/account/{accountId} */
data class EpicAccountResponse(
    @SerializedName("id") val id: String? = null,
    @SerializedName("accountId") val accountId: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("created") val created: String? = null,
    @SerializedName("lastLogin") val lastLogin: String? = null,
    @SerializedName("canReconnect") val canReconnect: Boolean? = null,
) {
    val resolvedAccountId: String? get() = id ?: accountId
}
