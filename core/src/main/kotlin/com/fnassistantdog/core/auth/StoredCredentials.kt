package com.fnassistantdog.core.auth

import com.google.gson.annotations.SerializedName

/**
 * 加密进 accounts.encryptedCredentials BLOB 的内容（AES/GCM 密文的明文形态只存在于内存）。
 * 结构与原 App 取证字段一致：accessToken / refreshToken / deviceId / deviceSecret / 过期时间。
 */
data class StoredCredentials(
    @SerializedName("accountId") val accountId: String,
    @SerializedName("accessToken") val accessToken: String,
    @SerializedName("refreshToken") val refreshToken: String? = null,
    @SerializedName("tokenType") val tokenType: String? = null,
    @SerializedName("deviceId") val deviceId: String? = null,
    @SerializedName("deviceSecret") val deviceSecret: String? = null,
    @SerializedName("accessTokenExpiresAt") val accessTokenExpiresAt: Long = 0L,
    @SerializedName("refreshTokenExpiresAt") val refreshTokenExpiresAt: Long? = null,
    @SerializedName("savedAt") val savedAt: Long = 0L,
) {
    fun bearerHeader(): String = "Bearer $accessToken"

    fun hasDeviceAuth(): Boolean = !deviceId.isNullOrBlank() && !deviceSecret.isNullOrBlank()
}
