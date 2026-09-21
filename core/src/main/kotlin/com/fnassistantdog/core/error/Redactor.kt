package com.fnassistantdog.core.error

/**
 * 日志/错误回显前的脱敏。正则主体逐字取自原版 APK dex 常量池（line 12740），
 * 保证与原 App 的"不记录授权码、Token、Secret、设备 ID"承诺一致。
 * 相对原字面量只多做一件事：把裸 `code=` 与 `deviceSecret=` 也纳入打码——这两个是本工程最敏感的输入，
 * 而原式的 `auth(?:orization)?_?code` 要求带 auth 前缀（匹配不到 query 里的 `code=XXXX`），
 * `secret` 又因为 `deviceSecret` 内部没有词边界而匹配不到驼峰写法。
 */
object Redactor {
    private val SECRET_PATTERN = Regex(
        """(?i)\b(auth(?:orization)?_?code|code|access_?token|refresh_?token|device_?id|device_?secret|secret)\b["']?\s*[:=]\s*["']?[^\s,;"']+""",
    )

    fun redact(text: String): String = SECRET_PATTERN.replace(text) { match ->
        val matched = match.value
        val separator = matched.indexOfFirst { it == ':' || it == '=' }
        if (separator < 0) matched else {
            val keyPart = matched.substring(0, separator + 1)
            val valuePart = matched.substring(separator + 1)
            val quote = valuePart.trimStart().firstOrNull()
                .takeIf { it == '"' || it == '\'' }
            if (quote == null) "$keyPart[REDACTED]" else "$keyPart$quote[REDACTED]$quote"
        }
    }

    /** Bearer/Basic 头整体打码，用于 HTTP 日志 */
    fun redactHeaderValue(value: String): String =
        when {
            value.startsWith("Bearer", ignoreCase = true) -> "Bearer [REDACTED]"
            value.startsWith("Basic", ignoreCase = true) -> "Basic [REDACTED]"
            else -> value
        }
}
