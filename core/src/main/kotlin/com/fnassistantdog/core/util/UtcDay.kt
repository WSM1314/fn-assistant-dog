package com.fnassistantdog.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * UTC 日工具。
 *
 * 刻意只用 java.text/java.util（minSdk 24 上 java.time 要 API 26，会运行期崩）。
 * 缓存 key 一律用 UTC 日：用本地日会在北京时区 08:00 前取到昨天的数据（F3 关键坑，商城同理）。
 */
object UtcDay {
    private const val DAY_PATTERN = "yyyyMMdd"

    /** 只看前 10 个字符（Regex.matches 是全串语义，必须截断后再比） */
    private val DATE_PREFIX = Regex("""\d{4}-\d{2}-\d{2}""")

    fun of(millis: Long): String {
        val format = SimpleDateFormat(DAY_PATTERN, Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(millis))
    }

    /** 从 ISO-8601（2026-09-20T00:00:00Z 等）取 UTC 日 → 20260920；不匹配返回 null */
    fun fromIso(iso: String?): String? {
        if (iso == null || iso.length < 10) return null
        val prefix = iso.substring(0, 10)
        if (!DATE_PREFIX.matches(prefix)) return null
        return prefix.replace("-", "")
    }

    /** 20260920 → 2026-09-20，用于界面显示 */
    fun pretty(day: String): String =
        if (day.length == 8) "${day.take(4)}-${day.substring(4, 6)}-${day.substring(6, 8)}" else day
}
