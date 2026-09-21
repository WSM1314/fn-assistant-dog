package com.fnassistantdog.core.http

import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Bearer 注入规则：只有 Epic 自有域名带 token，且不得覆盖调用点显式设置的 Authorization。 */
class BearerInterceptorTest {
    @Test
    fun `Epic 域名注入 Bearer`() {
        val chain = run(
            "https://friends-public-service-prod.ol.epicgames.com/friends/api/v1/a/summary",
            token = "T-1",
        )
        assertEquals("Bearer T-1", chain.passed!!.header("Authorization"))
    }

    @Test
    fun `第三方 fortnite-api 域名不注入`() {
        val chain = run("https://fortnite-api.com/v2/shop?language=zh-Hans", token = "T-1")
        assertNull(chain.passed!!.header("Authorization"))
    }

    @Test
    fun `调用点已显式带 Basic 时不被覆盖`() {
        val chain = run(
            url = "https://account-public-service-prod.ol.epicgames.com/account/api/oauth/token",
            token = "T-1",
            presetAuthorization = "Basic Zm9v",
        )
        assertEquals("Basic Zm9v", chain.passed!!.header("Authorization"))
    }

    @Test
    fun `无 token 时不写头`() {
        val chain = run("https://www.epicgames.com/id/api/redirect", token = null)
        assertNull(chain.passed!!.header("Authorization"))
    }

    private fun run(
        url: String,
        token: String?,
        presetAuthorization: String? = null,
    ): RecordingChain {
        val builder = Request.Builder().url(url)
        presetAuthorization?.let { builder.header("Authorization", it) }
        val chain = RecordingChain(builder.build())
        BearerInterceptor { token }.intercept(chain)
        return chain
    }
}

private class RecordingChain(private val req: Request) : Interceptor.Chain {
    var passed: Request? = null

    override fun request(): Request = req

    override fun proceed(request: Request): Response {
        passed = request
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("{}".toResponseBody(null))
            .build()
    }

    override fun connection(): Connection? = null

    override fun call(): Call = throw UnsupportedOperationException("测试不需要真实 Call")

    override fun connectTimeoutMillis(): Int = 0

    override fun readTimeoutMillis(): Int = 0

    override fun writeTimeoutMillis(): Int = 0

    override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

    override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

    override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
}
