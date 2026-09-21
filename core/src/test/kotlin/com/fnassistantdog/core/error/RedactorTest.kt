package com.fnassistantdog.core.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactorTest {
    @Test
    fun `打码授权码 access_token 与设备凭据`() {
        val line = "POST oauth/token grant_type=authorization_code&code=ABCDEF123456&access_token=tok-live " +
            "device_id=dev-999 deviceSecret=secret-999 note=hi"
        val out = Redactor.redact(line)
        assertFalse(out.contains("ABCDEF123456"))
        assertFalse(out.contains("tok-live"))
        assertFalse(out.contains("dev-999"))
        assertFalse(out.contains("secret-999"))
        assertTrue(out.contains("[REDACTED]"))
        assertTrue(out.contains("grant_type=authorization_code"))
    }

    @Test
    fun `带引号的 json 字段也打码`() {
        val json = """{"accessToken":"tok-1","refresh_token": "tok-2"}"""
        val out = Redactor.redact(json)
        assertFalse(out.contains("tok-1"))
        assertFalse(out.contains("tok-2"))
    }

    @Test
    fun `普通文案不被改动`() {
        val text = "今日商城没有可展示的商品"
        assertEquals(text, Redactor.redact(text))
    }

    @Test
    fun `Authorization 头整体打码`() {
        assertTrue(Redactor.redactHeaderValue("Bearer abc.def").contains("[REDACTED]"))
        assertTrue(Redactor.redactHeaderValue("Basic M2Y2OWU1NmM=").contains("[REDACTED]"))
        assertEquals("application/json", Redactor.redactHeaderValue("application/json"))
    }
}
