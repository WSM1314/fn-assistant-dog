package com.fnassistantdog.core.http

import com.fnassistantdog.core.error.EpicException
import com.fnassistantdog.core.error.EpicFailure
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

val sharedGson: Gson = Gson()

fun newRetrofit(
    baseUrl: String,
    client: OkHttpClient,
    gson: Gson = sharedGson,
): Retrofit = Retrofit.Builder()
    .baseUrl(baseUrl)
    .client(client)
    .addConverterFactory(GsonConverterFactory.create(gson))
    .build()

/**
 * 统一调用包装（照 F2 的错误处理模式：无客户端预检，失败透传服务端 errorMessage）。
 * 4xx/5xx 一律解析 Epic 的 {errorCode, errorMessage} 原文；网络异常单独分类，便于区分环境阻断。
 */
suspend fun <T : Any> apiCall(
    label: String,
    block: suspend () -> Response<T>,
): T {
    val response = execute(label, block)
    return response.body() ?: throw EpicException(
        EpicFailure.BadPayload("$label：响应体为空"),
    )
}

/** 无响应体的写操作（好友 POST/DELETE 等返回空 body 的调用）。 */
suspend fun apiCallEmpty(
    label: String,
    block: suspend () -> Response<Void>,
) {
    execute<Void>(label, block)
}

private suspend fun <T> execute(
    label: String,
    block: suspend () -> Response<T>,
): Response<T> {
    val response = try {
        block()
    } catch (timeout: SocketTimeoutException) {
        throw EpicException(EpicFailure.Timeout(Http.TIMEOUT_SECONDS.toInt()), timeout)
    } catch (unknownHost: UnknownHostException) {
        throw EpicException(EpicFailure.Unreachable("DNS ${unknownHost.message}"), unknownHost)
    } catch (connectionFailure: IOException) {
        val detail = buildString {
            append(connectionFailure.javaClass.simpleName)
            connectionFailure.message?.let { append(": ").append(it) }
        }
        throw EpicException(EpicFailure.Unreachable(detail), connectionFailure)
    }

    if (!response.isSuccessful) {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull()
        throw EpicException(httpFailure(response.code(), raw))
    }
    return response
}

fun httpFailure(
    status: Int,
    rawBody: String?,
): EpicFailure.Http {
    var errorCode: String? = null
    var message: String? = null
    if (!rawBody.isNullOrBlank()) {
        val parsed = runCatching { JsonParser.parseString(rawBody) }.getOrNull()
        if (parsed != null && parsed.isJsonObject) {
            val obj = parsed.asJsonObject
            errorCode = obj.stringOrNull("errorCode") ?: obj.stringOrNull("error")
            message = obj.stringOrNull("errorMessage")
                ?: obj.stringOrNull("error_description")
                ?: obj.stringOrNull("message")
        } else {
            message = rawBody.take(300)
        }
    }
    return EpicFailure.Http(status, errorCode, message)
}

private fun com.google.gson.JsonObject.stringOrNull(key: String): String? =
    runCatching { if (has(key) && !get(key).isJsonNull) get(key).asString else null }.getOrNull()
