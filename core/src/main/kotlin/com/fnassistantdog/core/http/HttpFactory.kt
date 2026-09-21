package com.fnassistantdog.core.http

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

/** 一行式接口：app 侧把它接到 logcat，用于验收时留接口响应证据。 */
fun interface HttpEvidence {
    fun log(line: String)
}

/**
 * 网络层。超时按 network_facts 要求取 20 秒（fortnite-api.com 慢、Epic 域名间歇阻断）。
 * 禁为网络问题改这里的链路逻辑，只允许改错误回显文案。
 */
object Http {
    const val TIMEOUT_SECONDS = 20L

    fun client(
        bearer: (() -> String?)? = null,
        evidence: HttpEvidence? = null,
        extraInterceptors: List<Interceptor> = emptyList(),
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_SECONDS * 2, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
        if (bearer != null) builder.addInterceptor(BearerInterceptor(bearer))
        extraInterceptors.forEach { builder.addInterceptor(it) }
        if (evidence != null) builder.addInterceptor(EvidenceInterceptor(evidence))
        return builder.build()
    }
}

/**
 * 只在 Epic 自有域名上注入 Bearer，避免把用户 token 发给第三方 fortnite-api.com。
 * 调用点已显式带 Authorization（如 oauth/token 的 Basic）时不覆盖。
 */
class BearerInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenProvider()
        val host = request.url.host
        val isEpicHost = host.endsWith("epicgames.com")
        val finalRequest =
            if (!token.isNullOrBlank() && isEpicHost && request.header("Authorization") == null) {
                request.newBuilder().header("Authorization", "Bearer $token").build()
            } else {
                request
            }
        return chain.proceed(finalRequest)
    }
}

/** 证据拦截器：记录方法/URL/状态/响应前若干字节（敏感头由上层 Redactor 处理，这里根本不打印头）。 */
class EvidenceInterceptor(private val evidence: HttpEvidence) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val snippet = runCatching {
            String(response.peekBody(MAX_SNIPPET_BYTES).byteStream().readBytes())
        }.getOrDefault("")
        evidence.log(
            "<- ${response.code} ${request.method} ${request.url}" +
                if (snippet.isEmpty()) "" else "\n   ${snippet.take(MAX_SNIPPET_CHARS)}",
        )
        return response
    }

    private companion object {
        const val MAX_SNIPPET_BYTES = 2_048L
        const val MAX_SNIPPET_CHARS = 1_200
    }
}
