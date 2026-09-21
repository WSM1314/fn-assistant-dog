package com.example.fn_app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 字段顺序与可空性逐字复刻原版 Room DDL（dex 取证 line 14060-14061），
 * 便于与原 App 的数据形状对照；导出 schema 见 app/schemas/。
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey @ColumnInfo(name = "accountId") val accountId: String,
    @ColumnInfo(name = "displayName") val displayName: String,
    @ColumnInfo(name = "encryptedCredentials") val encryptedCredentials: ByteArray,
    @ColumnInfo(name = "accountStatus") val accountStatus: String,
    @ColumnInfo(name = "accessTokenExpiresAt") val accessTokenExpiresAt: Long,
    @ColumnInfo(name = "refreshTokenExpiresAt") val refreshTokenExpiresAt: Long?,
    @ColumnInfo(name = "lastRefreshAt") val lastRefreshAt: Long,
    @ColumnInfo(name = "lastError") val lastError: String?,
    @ColumnInfo(name = "vbucksBalance") val vbucksBalance: Int?,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
    @ColumnInfo(name = "updatedAt") val updatedAt: Long,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is AccountEntity && other.accountId == accountId)

    override fun hashCode(): Int = accountId.hashCode()
}
