package com.fnassistantdog.core.auth

/**
 * 当前会话：把"选中账号 + 有效 token"收在一处。
 *
 * 请求前的 token 新鲜度由 [use] 保证（过期前 5 分钟即刷新），
 * 拦截器只能同步取用，故这里保留最近一次有效 access token 供 [bearerOrNull] 读取。
 */
class EpicSession(private val auth: EpicAuthRepository) {
    @Volatile
    private var accessToken: String? = null

    @Volatile
    var accountId: String? = null
        private set

    suspend fun use(accountId: String): StoredCredentials {
        val credentials = auth.validCredentials(accountId)
        accessToken = credentials.accessToken
        this.accountId = accountId
        return credentials
    }

    fun bearerOrNull(): String? = accessToken

    fun clear() {
        accessToken = null
        accountId = null
    }
}
