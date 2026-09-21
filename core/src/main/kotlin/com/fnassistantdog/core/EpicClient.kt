package com.fnassistantdog.core

/**
 * Epic OAuth 客户端凭据。
 *
 * 这是 Epic 公开的"浏览器"客户端对（原 APK dex 常量池 line 13890 就是这条 base64 字面量本身，
 * 各家开源 Fortnite 工具内置的同一条），不是用户凭据；用户侧 access_token / deviceSecret
 * 一律经 AndroidKeyStore 加密后才落库（app 侧 AndroidKeystoreCipher，约束 5）。
 *
 * 这里直接存 Basic 头的成品字符串：minSdk 24 上 `java.util.Base64` 不可用（API 26+），
 * 自己实现一个编码器为一个常量而不值当，且原 App 同样是存字面量。
 *
 * 实测（无用户凭据的匿名探测，docs/phase-1-设计.md R1）：
 *   不带 Authorization      -> errors.com.epicgames.common.oauth.invalid_client
 *   带本常量 + 假授权码    -> errors.com.epicgames.account.oauth.authorization_code_not_found
 *   即客户端被服务端接受，请求为 POST form-urlencoded。
 */
object EpicClient {
    const val CLIENT_ID = "3f69e56c7649492c8cc29f1af08a8a12"

    private const val BASIC_AUTH_HEADER =
        "Basic M2Y2OWU1NmM3NjQ5NDkyYzhjYzI5ZjFhZjA4YThhMTI6YjUxZWU5Y2IxMjIzNGY1MGE2OWVmYTY3ZWY1MzgxMmU="

    fun basicAuthHeader(): String = BASIC_AUTH_HEADER
}
