package com.fnassistantdog.core.auth

import kotlinx.coroutines.flow.Flow

/**
 * 平台端口：:core 只依赖这些接口，Android 侧给实现。
 * Phase 4 走 KMP 时，:core 整体搬进 commonMain，这些接口在 iOS 侧另实现（Keychain 等）。
 */

/** 凭据加解密（app 侧 = AndroidKeyStore AES/GCM/NoPadding）。 */
interface CredentialCipher {
    fun encrypt(plain: ByteArray): ByteArray

    fun decrypt(blob: ByteArray): ByteArray
}

/** 可注入时钟，便于单测。 */
interface Clock {
    fun nowMillis(): Long
}

/** 账号状态取值与原 App 一致（字符串在 dex 常量池取证：ACTIVE / PENDING / ERROR / EXPIRED / UNKNOWN）。 */
object AccountStatus {
    const val ACTIVE = "ACTIVE"
    const val PENDING = "PENDING"
    const val ERROR = "ERROR"
    const val EXPIRED = "EXPIRED"
    const val UNKNOWN = "UNKNOWN"
}

/** accounts 表的一条记录（encryptedCredentials 已是密文 BLOB，:core 不持有明文凭据的持久形态）。 */
data class AccountRecord(
    val accountId: String,
    val displayName: String,
    val encryptedCredentials: ByteArray,
    val accountStatus: String,
    val accessTokenExpiresAt: Long,
    val refreshTokenExpiresAt: Long?,
    val lastRefreshAt: Long,
    val lastError: String?,
    val vbucksBalance: Int?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is AccountRecord &&
                other.accountId == accountId &&
                other.displayName == displayName &&
                other.accountStatus == accountStatus &&
                other.accessTokenExpiresAt == accessTokenExpiresAt &&
                other.refreshTokenExpiresAt == refreshTokenExpiresAt &&
                other.lastRefreshAt == lastRefreshAt &&
                other.lastError == lastError &&
                other.vbucksBalance == vbucksBalance &&
                other.createdAt == createdAt &&
                other.updatedAt == updatedAt &&
                other.encryptedCredentials.contentEquals(encryptedCredentials))

    override fun hashCode(): Int {
        var result = accountId.hashCode()
        result = 31 * result + encryptedCredentials.contentHashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + accountStatus.hashCode()
        result = 31 * result + accessTokenExpiresAt.hashCode()
        result = 31 * result + (refreshTokenExpiresAt?.hashCode() ?: 0)
        result = 31 * result + lastRefreshAt.hashCode()
        result = 31 * result + (lastError?.hashCode() ?: 0)
        result = 31 * result + (vbucksBalance ?: 0)
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}

/** Room `accounts` 表的抽象，:core 通过它读写，不碰 SQLite。 */
interface AccountStore {
    fun observe(): kotlinx.coroutines.flow.Flow<List<AccountRecord>>

    suspend fun all(): List<AccountRecord>

    suspend fun find(accountId: String): AccountRecord?

    suspend fun upsert(record: AccountRecord)

    suspend fun delete(accountId: String)

    suspend fun updateStatus(accountId: String, status: String, lastError: String?, nowMillis: Long)
}
