package com.fnassistantdog.core.auth

import com.fnassistantdog.core.EpicClient
import com.fnassistantdog.core.api.EpicAccountApi
import com.fnassistantdog.core.error.EpicException
import com.fnassistantdog.core.error.EpicFailure
import com.fnassistantdog.core.error.LocalMessages
import com.fnassistantdog.core.http.apiCall
import com.fnassistantdog.core.http.sharedGson
import com.google.gson.Gson

/**
 * 认证链路（F1 全套复刻）：
 * 浏览器取授权码 → POST oauth/token(grant_type=authorization_code) →
 * POST public/account/{id}/deviceAuth 拿 deviceId/deviceSecret →
 * 之后一律 grant_type=device_auth 刷新；凭据 JSON 经 [CredentialCipher] 加密后才进 AccountStore。
 *
 * 授权码本身：只在内存里存在，不落盘、不进日志（约束 5）。
 */
class EpicAuthRepository(
    private val api: EpicAccountApi,
    private val store: AccountStore,
    private val cipher: CredentialCipher,
    private val clock: Clock,
    private val gson: Gson = sharedGson,
) {
    /** 第 N 个授权码失败的回显（措辞对齐原 App："第 N 个授权码不能包含空格"） */
    data class ItemError(val index: Int, val message: String) {
        val display: String get() = "第 ${index + 1} 个$message"
    }

    data class BindOutcome(
        val bound: List<AccountRecord> = emptyList(),
        val errors: List<ItemError> = emptyList(),
    )

    suspend fun accounts(): List<AccountRecord> = store.all()

    /**
     * 批量绑定（原 App 交互：一个多行输入框，每行一个授权码，一次最多 20 个）。
     * 本地先做格式/去重校验，再逐条走网络；单条失败不影响其余（不做整批回滚）。
     */
    suspend fun bind(rawLines: List<String>): BindOutcome {
        val accepted = ArrayList<Pair<Int, String>>()
        val errors = ArrayList<ItemError>()
        val seen = HashSet<String>()
        rawLines.forEachIndexed { index, raw ->
            val code = raw.trim()
            if (code.isEmpty()) return@forEachIndexed
            val reason = when {
                code.any { it.isWhitespace() } -> LocalMessages.CODE_HAS_SPACE
                code.length < MIN_CODE_LEN || code.length > MAX_CODE_LEN -> LocalMessages.CODE_LENGTH_INVALID
                !seen.add(code) -> LocalMessages.DUPLICATE_CODE
                else -> null
            }
            if (reason == null) accepted += index to code else errors += ItemError(index, reason)
        }
        if (accepted.isEmpty() && errors.isEmpty()) {
            throw EpicException(EpicFailure.Local(LocalMessages.AT_LEAST_ONE_CODE))
        }
        if (accepted.size > MAX_ACCOUNTS) {
            throw EpicException(EpicFailure.Local(LocalMessages.TOO_MANY_CODES))
        }
        val existing = store.all()
        if (existing.size + accepted.size > MAX_ACCOUNTS) {
            throw EpicException(EpicFailure.Local(LocalMessages.ACCOUNT_LIMIT))
        }

        val bound = ArrayList<AccountRecord>()
        val bindErrors = ArrayList(errors)
        for ((index, code) in accepted) {
            try {
                bound += bindOne(code)
            } catch (failure: EpicException) {
                bindErrors += ItemError(index, failure.failure.displayMessage())
            }
        }
        return BindOutcome(bound = bound, errors = bindErrors)
    }

    private suspend fun bindOne(code: String): AccountRecord {
        val token = apiCall("授权码换取令牌") {
            api.tokenByAuthorizationCode(
                basicAuth = EpicClient.basicAuthHeader(),
                grantType = GRANT_AUTHORIZATION_CODE,
                code = code,
            )
        }
        val accountId = token.accountId ?: throw EpicException(EpicFailure.BadPayload(LocalMessages.MISSING_ACCOUNT_ID))
        val accessToken = token.accessToken ?: throw EpicException(EpicFailure.BadPayload(LocalMessages.MISSING_ACCESS_TOKEN))
        val bearer = "Bearer $accessToken"

        val device = apiCall("创建设备凭据") {
            api.createDeviceAuth(bearer, accountId, mapOf("note" to DEVICE_AUTH_NOTE))
        }
        val deviceId = device.resolvedDeviceId
        val secret = device.resolvedSecret
        if (deviceId.isNullOrBlank() || secret.isNullOrBlank()) {
            throw EpicException(EpicFailure.BadPayload(LocalMessages.INCOMPLETE_DEVICE_AUTH))
        }

        val now = clock.nowMillis()
        val expiresAt = expiresAtOf(now, token.expiresIn)
        val credentials = StoredCredentials(
            accountId = accountId,
            accessToken = accessToken,
            refreshToken = token.refreshToken,
            tokenType = token.tokenType,
            deviceId = deviceId,
            deviceSecret = secret,
            accessTokenExpiresAt = expiresAt,
            savedAt = now,
        )
        val displayName = token.displayName?.takeIf { it.isNotBlank() }
            ?: runCatching { apiCall("账号资料") { api.account(bearer, accountId) }.displayName }
                .getOrNull()
            ?: "账号 ${accountId.takeLast(4)}"

        val record = AccountRecord(
            accountId = accountId,
            displayName = displayName,
            encryptedCredentials = encrypt(credentials),
            accountStatus = AccountStatus.ACTIVE,
            accessTokenExpiresAt = expiresAt,
            refreshTokenExpiresAt = null,
            lastRefreshAt = now,
            lastError = null,
            vbucksBalance = null,
            createdAt = now,
            updatedAt = now,
        )
        store.upsert(record)
        return record
    }

    /** 取用凭据：距过期不足 [REFRESH_MARGIN_MILLIS] 时先用 device_auth 刷新（401 不在此处重试扣款类请求）。 */
    suspend fun validCredentials(accountId: String): StoredCredentials {
        val record = store.find(accountId)
            ?: throw EpicException(EpicFailure.Local(LocalMessages.NO_LOCAL_CREDENTIALS))
        val current = decrypt(record.encryptedCredentials)
        val now = clock.nowMillis()
        return if (current.accessTokenExpiresAt - now <= REFRESH_MARGIN_MILLIS) refresh(record, current) else current
    }

    suspend fun refresh(accountId: String): StoredCredentials {
        val record = store.find(accountId)
            ?: throw EpicException(EpicFailure.Local(LocalMessages.NO_LOCAL_CREDENTIALS))
        return refresh(record, decrypt(record.encryptedCredentials))
    }

    private suspend fun refresh(record: AccountRecord, current: StoredCredentials): StoredCredentials {
        val deviceId = current.deviceId
        val secret = current.deviceSecret
        if (deviceId.isNullOrBlank() || secret.isNullOrBlank()) {
            store.updateStatus(record.accountId, AccountStatus.EXPIRED, LocalMessages.NO_DEVICE_AUTH, clock.nowMillis())
            throw EpicException(EpicFailure.Local(LocalMessages.NO_DEVICE_AUTH))
        }
        val token = apiCall("设备凭据刷新") {
            api.tokenByDeviceAuth(
                basicAuth = EpicClient.basicAuthHeader(),
                grantType = GRANT_DEVICE_AUTH,
                deviceId = deviceId,
                deviceSecret = secret,
            )
        }
        val accessToken = token.accessToken ?: throw EpicException(
            EpicFailure.BadPayload("${LocalMessages.MISSING_ACCESS_TOKEN}（device_auth）"),
        )
        val now = clock.nowMillis()
        val expiresAt = expiresAtOf(now, token.expiresIn)
        val updated = current.copy(
            accessToken = accessToken,
            refreshToken = token.refreshToken ?: current.refreshToken,
            tokenType = token.tokenType ?: current.tokenType,
            accessTokenExpiresAt = expiresAt,
            savedAt = now,
        )
        store.upsert(
            record.copy(
                encryptedCredentials = encrypt(updated),
                accountStatus = AccountStatus.ACTIVE,
                accessTokenExpiresAt = expiresAt,
                lastRefreshAt = now,
                lastError = null,
                updatedAt = now,
            ),
        )
        return updated
    }

    /** 删除账号：尽力回收远端设备凭据（失败不阻塞本地删除），再删本地行。 */
    suspend fun remove(accountId: String) {
        val record = store.find(accountId)
        store.delete(accountId)
        if (record == null) return
        runCatching {
            val credentials = decrypt(record.encryptedCredentials)
            val deviceId = credentials.deviceId
            if (!deviceId.isNullOrBlank()) {
                api.deleteDeviceAuth(
                    bearerAuth = credentials.bearerHeader(),
                    accountId = accountId,
                    deviceId = deviceId,
                )
            }
        }
    }

    suspend fun markError(accountId: String, message: String) {
        store.updateStatus(accountId, AccountStatus.ERROR, message, clock.nowMillis())
    }

    suspend fun markNeedsReauth(accountId: String, message: String) {
        store.updateStatus(accountId, AccountStatus.EXPIRED, message, clock.nowMillis())
    }

    fun decrypt(blob: ByteArray): StoredCredentials =
        runCatching {
            gson.fromJson(String(cipher.decrypt(blob), Charsets.UTF_8), StoredCredentials::class.java)
        }.getOrNull() ?: throw EpicException(EpicFailure.Local(LocalMessages.CORRUPTED_CREDENTIALS))

    private fun encrypt(credentials: StoredCredentials): ByteArray =
        cipher.encrypt(gson.toJson(credentials).toByteArray(Charsets.UTF_8))

    private fun expiresAtOf(now: Long, expiresInSeconds: Long?): Long =
        if (expiresInSeconds != null && expiresInSeconds > 0) now + expiresInSeconds * 1000L else now

    companion object {
        const val MAX_ACCOUNTS = 20
        const val MIN_CODE_LEN = 8
        const val MAX_CODE_LEN = 256

        /** 过期前 5 分钟即视为该刷新 */
        const val REFRESH_MARGIN_MILLIS = 5 * 60_000L

        const val GRANT_AUTHORIZATION_CODE = "authorization_code"
        const val GRANT_DEVICE_AUTH = "device_auth"
        const val DEVICE_AUTH_NOTE = "FN助手狗"
    }
}
