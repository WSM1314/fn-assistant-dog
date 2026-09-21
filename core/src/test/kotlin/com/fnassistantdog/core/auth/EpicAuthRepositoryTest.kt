package com.fnassistantdog.core.auth

import com.fnassistantdog.core.api.EpicAccountApi
import com.fnassistantdog.core.error.EpicException
import com.fnassistantdog.core.error.LocalMessages
import com.fnassistantdog.core.model.EpicAccountResponse
import com.fnassistantdog.core.model.EpicDeviceAuthResponse
import com.fnassistantdog.core.model.EpicTokenResponse
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

private const val NOW = 1_789_864_200_000L
private const val CODE_A = "aaaaaaaaaaaaaaaaaaaa"
private const val CODE_B = "bbbbbbbbbbbbbbbbbbbb"
private const val ACCOUNT_A = "ACCOUNT-a"
private const val ACCESS_TOKEN = "ACCESS-TOKEN-ORIGINAL"
private const val REFRESHED_TOKEN = "ACCESS-TOKEN-REFRESHED"

/** 认证链路行为测试：假网络 + 假加密 + 假存储，只验编排与"密文入库"。 */
class EpicAuthRepositoryTest {
    private val clock = object : Clock {
        override fun nowMillis(): Long = NOW
    }
    private val cipher = ReverseCipher()
    private val store = InMemoryStore()
    private val api = FakeAccountApi()
    private val repo = EpicAuthRepository(api, store, cipher, clock)

    @Test
    fun `全空行输入时报缺少授权码`() = runTest {
        val thrown = runCatching { repo.bind(listOf("", "   ")) }.exceptionOrNull()
        assertTrue(thrown is EpicException)
        assertEquals(LocalMessages.AT_LEAST_ONE_CODE, (thrown as EpicException).failure.displayMessage())
        assertEquals(0, api.codeExchanges)
    }

    @Test
    fun `含空格与长度不足的授权码逐条回显且不联网`() = runTest {
        val outcome = repo.bind(listOf("ab cd efgh", "short"))
        assertEquals(2, outcome.errors.size)
        assertTrue(outcome.errors[0].display.contains(LocalMessages.CODE_HAS_SPACE))
        assertTrue(outcome.errors[0].display.startsWith("第 1 个"))
        assertTrue(outcome.errors[1].display.contains(LocalMessages.CODE_LENGTH_INVALID))
        assertEquals(0, api.codeExchanges)
    }

    @Test
    fun `重复授权码只绑一次并报告重复项`() = runTest {
        val outcome = repo.bind(listOf(CODE_A, CODE_B, CODE_A))
        assertEquals(1, outcome.errors.size)
        assertTrue(outcome.errors[0].display.contains(LocalMessages.DUPLICATE_CODE))
        assertEquals(2, api.codeExchanges)
        assertEquals(2, store.all().size)
        assertEquals(1, outcome.bound.count { it.accountId == ACCOUNT_A })
    }

    @Test
    fun `一次超过 20 个直接拒绝`() = runTest {
        val codes = (1..21).map { ("c" + it).padEnd(20, 'x') }
        val thrown = runCatching { repo.bind(codes) }.exceptionOrNull()
        assertEquals(LocalMessages.TOO_MANY_CODES, (thrown as EpicException).failure.displayMessage())
        assertEquals(0, api.codeExchanges)
    }

    @Test
    fun `绑定成功后凭据以密文入库并可解回设备凭据`() = runTest {
        val outcome = repo.bind(listOf(CODE_A))
        assertEquals(1, outcome.bound.size)
        val record = store.all().single()

        assertEquals(ACCOUNT_A, record.accountId)
        assertEquals("测试昵称", record.displayName)
        assertEquals(AccountStatus.ACTIVE, record.accountStatus)
        assertEquals(NOW + 7200_000L, record.accessTokenExpiresAt)
        assertEquals(NOW, record.createdAt)
        assertFalse(String(record.encryptedCredentials, Charsets.UTF_8).contains(ACCESS_TOKEN))

        val decrypted = repo.decrypt(record.encryptedCredentials)
        assertEquals(ACCESS_TOKEN, decrypted.accessToken)
        assertEquals("DEV-1", decrypted.deviceId)
        assertEquals("DEVICE-SECRET-1", decrypted.deviceSecret)
        assertTrue(decrypted.hasDeviceAuth())
        assertEquals(1, api.deviceAuthCreates)
    }

    @Test
    fun `设备凭据缺失时刷新失败并把账号标成 EXPIRED`() = runTest {
        repo.bind(listOf(CODE_A))
        val record = store.all().single()
        val noDevice = repo.decrypt(record.encryptedCredentials).copy(deviceId = null, deviceSecret = null)
        store.upsert(record.copy(encryptedCredentials = cipher.encode(Gson().toJson(noDevice))))

        val thrown = runCatching { repo.refresh(ACCOUNT_A) }.exceptionOrNull()
        assertEquals(LocalMessages.NO_DEVICE_AUTH, (thrown as EpicException).failure.displayMessage())
        assertEquals(AccountStatus.EXPIRED, store.find(ACCOUNT_A)?.accountStatus)
        assertEquals(0, api.deviceAuthRefreshes)
    }

    @Test
    fun `令牌临期时自动用 device_auth 换新并回写密文`() = runTest {
        repo.bind(listOf(CODE_A))
        val before = store.all().single()
        val stale = repo.decrypt(before.encryptedCredentials).copy(
            accessToken = ACCESS_TOKEN,
            accessTokenExpiresAt = NOW + 60_000L,
        )
        store.upsert(before.copy(encryptedCredentials = cipher.encode(Gson().toJson(stale))))

        val used = repo.validCredentials(ACCOUNT_A)

        assertEquals(1, api.deviceAuthRefreshes)
        assertEquals(REFRESHED_TOKEN, used.accessToken)
        val after = store.all().single()
        assertEquals(NOW + 7200_000L, after.accessTokenExpiresAt)
        assertEquals(REFRESHED_TOKEN, repo.decrypt(after.encryptedCredentials).accessToken)
        assertNull(after.lastError)
        assertFalse(String(after.encryptedCredentials, Charsets.UTF_8).contains(REFRESHED_TOKEN))
    }

    @Test
    fun `有效令牌不会被无谓刷新`() = runTest {
        repo.bind(listOf(CODE_A))
        repo.validCredentials(ACCOUNT_A)
        assertEquals(0, api.deviceAuthRefreshes)
    }
}

/** 只做可逆变换的假加密（真实现是 AndroidKeyStore AES/GCM，在 app 侧） */
private class ReverseCipher : CredentialCipher {
    override fun encrypt(plain: ByteArray): ByteArray = plain.reversedArray()

    override fun decrypt(blob: ByteArray): ByteArray = blob.reversedArray()

    fun encode(text: String): ByteArray = encrypt(text.toByteArray(Charsets.UTF_8))
}

private class InMemoryStore : AccountStore {
    private val rows = LinkedHashMap<String, AccountRecord>()

    override fun observe(): Flow<List<AccountRecord>> = emptyFlow()

    override suspend fun all(): List<AccountRecord> = rows.values.toList()

    override suspend fun find(accountId: String): AccountRecord? = rows[accountId]

    override suspend fun upsert(record: AccountRecord) {
        rows[record.accountId] = record
    }

    override suspend fun delete(accountId: String) {
        rows.remove(accountId)
    }

    override suspend fun updateStatus(
        accountId: String,
        status: String,
        lastError: String?,
        nowMillis: Long,
    ) {
        rows[accountId]?.let {
            rows[accountId] = it.copy(accountStatus = status, lastError = lastError, updatedAt = nowMillis)
        }
    }
}

private class FakeAccountApi : EpicAccountApi {
    var codeExchanges = 0
    var deviceAuthCreates = 0
    var deviceAuthRefreshes = 0

    override suspend fun tokenByAuthorizationCode(
        basicAuth: String,
        grantType: String,
        code: String,
    ): Response<EpicTokenResponse> {
        codeExchanges += 1
        assertEquals("authorization_code", grantType)
        assertTrue("必须带 Basic 客户端凭据", basicAuth.startsWith("Basic "))
        return Response.success(
            EpicTokenResponse(
                accessTokenSnake = ACCESS_TOKEN,
                expiresIn = 7200L,
                accountIdSnake = "ACCOUNT-" + code.first(),
            ),
        )
    }

    override suspend fun tokenByDeviceAuth(
        basicAuth: String,
        grantType: String,
        deviceId: String,
        deviceSecret: String,
    ): Response<EpicTokenResponse> {
        deviceAuthRefreshes += 1
        assertEquals("device_auth", grantType)
        assertEquals("DEV-1", deviceId)
        assertEquals("DEVICE-SECRET-1", deviceSecret)
        return Response.success(
            EpicTokenResponse(
                accessTokenSnake = REFRESHED_TOKEN,
                expiresIn = 7200L,
                accountIdSnake = ACCOUNT_A,
            ),
        )
    }

    override suspend fun account(
        bearerAuth: String,
        accountId: String,
    ): Response<EpicAccountResponse> {
        assertTrue(bearerAuth.startsWith("Bearer "))
        return Response.success(EpicAccountResponse(id = accountId, displayName = "测试昵称"))
    }

    override suspend fun createDeviceAuth(
        bearerAuth: String,
        accountId: String,
        body: Map<String, String>,
    ): Response<EpicDeviceAuthResponse> {
        deviceAuthCreates += 1
        return Response.success(EpicDeviceAuthResponse(deviceId = "DEV-1", secret = "DEVICE-SECRET-1"))
    }

    override suspend fun deleteDeviceAuth(
        bearerAuth: String,
        accountId: String,
        deviceId: String,
    ): Response<Void> = Response.success(null)
}
