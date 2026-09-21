package com.fnassistantdog.core.error

/**
 * 统一错误模型。约束：失败必须把服务端 errorMessage 原文透传到 UI，不吞、不改写。
 * 网络类错误（不可达/超时/握手）与代码缺陷严格区分文案，见 network_facts。
 */
sealed class EpicFailure {
    /** HTTP 4xx/5xx：带状态码与服务端 errorCode/errorMessage 原文 */
    data class Http(
        val status: Int,
        val errorCode: String?,
        val serverMessage: String?,
    ) : EpicFailure()

    /** 连不上/DNS/TLS 握手被掐（本机 Epic 域名间歇阻断时走这里） */
    data class Unreachable(val causeMessage: String) : EpicFailure()

    /** 读超时（fortnite-api.com 响应慢） */
    data class Timeout(val seconds: Int) : EpicFailure()

    /** 2xx 但 body 为空或字段缺失 */
    data class BadPayload(val detail: String) : EpicFailure()

    /** 本地校验（授权码格式、账号数上限等），不发请求 */
    data class Local(val message: String) : EpicFailure()

    /** 面向用户的中文回显，服务端原文始终附在后面 */
    fun displayMessage(): String = when (this) {
        is Http -> buildString {
            append("请求失败（HTTP ").append(status).append("）")
            errorCode?.let { append(" · ").append(it) }
            serverMessage?.let { append("：").append(it) }
        }

        is Unreachable -> "网络不可达，请检查代理或网络后重试（$causeMessage）"
        is Timeout -> "请求超时（${seconds} 秒），接口响应可能较慢，请重试"
        is BadPayload -> "服务返回数据不完整：$detail"
        is Local -> message
    }

    val isUnauthorized: Boolean get() = this is Http && status == 401
}

class EpicException(
    val failure: EpicFailure,
    cause: Throwable? = null,
) : Exception(failure.displayMessage(), cause)
