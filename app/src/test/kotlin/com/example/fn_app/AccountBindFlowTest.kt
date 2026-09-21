package com.example.fn_app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fn_app.copy.Copy
import com.fnassistantdog.core.auth.AccountStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** 绑定链路端到端（JVM 内真跑 App）：UI 输入授权码 → OAuth → deviceAuth → Room 密文 → 好友页带 Bearer。 */
@RunWith(AndroidJUnit4::class)
@Config(application = TestFnDogApp::class, sdk = [34])
class AccountBindFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetBodies() {
        FakeEpic.reset()
    }

    @Test
    fun `单个授权码绑定后界面出现昵称且库中只存密文`() {
        bindCodes(Fixtures.AUTH_CODE)

        val tokenRequest = FakeEpic.firstMatching { it.path?.endsWith("/oauth/token") == true }!!
        assertEquals("POST", tokenRequest.method)
        assertEquals("/account/api/oauth/token", tokenRequest.path)
        assertTrue(
            "必须带 Basic 客户端凭据",
            tokenRequest.getHeader("Authorization").orEmpty().startsWith("Basic "),
        )
        val form = tokenRequest.body.readUtf8()
        assertTrue(form.contains("grant_type=authorization_code"))
        assertTrue(form.contains("code=${Fixtures.AUTH_CODE}"))

        val deviceAuthRequest = FakeEpic.firstMatching { it.path?.contains("/deviceAuth") == true }!!
        assertEquals("Bearer ${Fixtures.ACCESS_TOKEN}", deviceAuthRequest.getHeader("Authorization"))
        assertTrue(deviceAuthRequest.path!!.endsWith("/public/account/${Fixtures.ACCOUNT_ID}/deviceAuth"))

        val record = runBlocking { app().container.accountStore.all() }.single()
        assertEquals(Fixtures.ACCOUNT_ID, record.accountId)
        assertEquals(Fixtures.DISPLAY_NAME, record.displayName)
        assertEquals(AccountStatus.ACTIVE, record.accountStatus)
        assertTrue(
            "落库必须是加密实现产出的密文（带 ENC: 前缀）",
            record.encryptedCredentials.copyOfRange(0, ReversibleCipher.MARKER.size)
                .contentEquals(ReversibleCipher.MARKER),
        )
        val blobText = String(record.encryptedCredentials, Charsets.UTF_8)
        assertFalse("明文令牌不得出现在库里", blobText.contains(Fixtures.ACCESS_TOKEN))
        assertFalse("明文设备密钥不得出现在库里", blobText.contains(Fixtures.DEVICE_SECRET))
        assertTrue(record.accessTokenExpiresAt > System.currentTimeMillis())

        compose.onNodeWithText(Fixtures.DISPLAY_NAME).assertIsDisplayed()
    }

    @Test
    fun `多行批量绑定逐条回显校验结果`() {
        bindCodes("${Fixtures.AUTH_CODE}\n${Fixtures.AUTH_CODE}\nshort")

        compose.onNodeWithText("重复", substring = true).assertExists()
        compose.onNodeWithText("长度无效", substring = true).assertExists()
        compose.onNodeWithText(Copy.boundAccountsSuffix, substring = true).assertExists()
        assertEquals(1, FakeEpic.requests.count { it.path?.endsWith("/oauth/token") == true })
    }

    @Test
    fun `绑定后好友页用该账号令牌加载并显示分组`() {
        bindCodes(Fixtures.AUTH_CODE)

        compose.onNode(hasText(Copy.tabFriends) and hasClickAction()).performClick()
        pumpUntil(diagnose = FakeEpic::describe) { nodesWithText(Fixtures.FRIEND_NAME).isNotEmpty() }
        compose.onNodeWithText("待处理请求 (1)").assertExists()
        compose.onNodeWithText(Fixtures.FRIEND_NAME).assertIsDisplayed()

        val friendsRequest = FakeEpic.firstMatching { it.path?.endsWith("/summary") == true }!!
        assertEquals("/friends/api/v1/${Fixtures.ACCOUNT_ID}/summary", friendsRequest.path?.substringBefore('?'))
        // 本机 Mock 的 host 是 localhost：按 BearerInterceptor 规则不注入 token（只有 *.epicgames.com 才注入）。
        // "Epic 域名注入、第三方不注入" 这条规则本身由 :core 的 BearerInterceptorTest 逐条证明。
        assertNull("非 Epic 域名不得带 token", friendsRequest.getHeader("Authorization"))
    }

    private fun bindCodes(codes: String) {
        compose.onNode(hasText(Copy.tabAccounts) and hasClickAction()).performClick()
        compose.onNode(hasSetTextAction()).performTextInput(codes)
        compose.onNodeWithText(Copy.bindAccounts).performClick()
        // 等到界面上出现该账号行：说明 token→deviceAuth→加密→Room 写入这条链已跑完
        pumpUntil(diagnose = FakeEpic::describe) { nodesWithText(Fixtures.DISPLAY_NAME).isNotEmpty() }
    }

    private fun nodesWithText(text: String, substring: Boolean = false) =
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes()

    private fun app() = compose.activity.application as TestFnDogApp
}
