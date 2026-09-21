package com.example.fn_app.data

import com.fnassistantdog.core.auth.AccountRecord
import com.fnassistantdog.core.auth.AccountStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** :core 的 AccountStore ↔ Room。密文 BLOB 原样搬运，不在此处解密。 */
class AccountStoreImpl(private val dao: AccountDao) : AccountStore {
    override fun observe(): Flow<List<AccountRecord>> = dao.observeAll().map { list -> list.map { it.toRecord() } }

    override suspend fun all(): List<AccountRecord> = dao.all().map { it.toRecord() }

    override suspend fun find(accountId: String): AccountRecord? = dao.find(accountId)?.toRecord()

    override suspend fun upsert(record: AccountRecord) = dao.upsert(record.toEntity())

    override suspend fun delete(accountId: String) = dao.delete(accountId)

    override suspend fun updateStatus(
        accountId: String,
        status: String,
        lastError: String?,
        nowMillis: Long,
    ) = dao.updateStatus(accountId, status, lastError, nowMillis)
}

private fun AccountEntity.toRecord(): AccountRecord = AccountRecord(
    accountId = accountId,
    displayName = displayName,
    encryptedCredentials = encryptedCredentials,
    accountStatus = accountStatus,
    accessTokenExpiresAt = accessTokenExpiresAt,
    refreshTokenExpiresAt = refreshTokenExpiresAt,
    lastRefreshAt = lastRefreshAt,
    lastError = lastError,
    vbucksBalance = vbucksBalance,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun AccountRecord.toEntity(): AccountEntity = AccountEntity(
    accountId = accountId,
    displayName = displayName,
    encryptedCredentials = encryptedCredentials,
    accountStatus = accountStatus,
    accessTokenExpiresAt = accessTokenExpiresAt,
    refreshTokenExpiresAt = refreshTokenExpiresAt,
    lastRefreshAt = lastRefreshAt,
    lastError = lastError,
    vbucksBalance = vbucksBalance,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
