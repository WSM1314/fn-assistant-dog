package com.example.fn_app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fn_app.copy.Copy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 真启动 App 的冒烟测试（Robolectric）：MainActivity 生命周期 + Compose 渲染 + 真实 OkHttp/Retrofit 请求。
 */
@RunWith(AndroidJUnit4::class)
@Config(application = TestFnDogApp::class, sdk = [34])
class AppSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetBodies() {
        FakeEpic.reset()
    }

    @Test
    fun `启动后三个 tab 可见且今日商城渲染出条目`() {
        clickTab(Copy.tabFriends)
        clickTab(Copy.tabAccounts)
        clickTab(Copy.tabTodayShop)

        pumpUntil(diagnose = FakeEpic::describe) {
            nodesWithText(Fixtures.ITEM_NAME).isNotEmpty()
        }
        // 网格内的卡片文本可能在折叠区，用 assertExists（存在即已渲染）
        compose.onNodeWithText("1000 ${Copy.vbucksUnit}").assertExists()
        compose.onNodeWithText("1500").assertExists()
        compose.onNodeWithText("更新自 2026-09-20 (UTC) · ${Copy.shopOnline}").assertExists()
        // 只有 1 张卡片：无 brItems 的第二条被跳过
        assertEquals("空 brItems 的条目不应渲染成卡片", 1, nodesWithText(Copy.vbucksUnit, substring = true).size)
    }

    @Test
    fun `商城请求不带用户 Authorization 且查询参数正确`() {
        // @Before 清空了录制列表，这里点刷新主动产生一次可观测的请求
        compose.onNodeWithContentDescription(Copy.refresh).performClick()
        pumpUntil(diagnose = FakeEpic::describe) { shopRequests() > 0 }
        val request = FakeEpic.requests.first { it.path?.substringBefore('?') == "/v2/shop" }
        assertEquals("GET", request.method)
        assertEquals("/v2/shop", request.path?.substringBefore('?'))
        assertEquals("zh-Hans", request.requestUrl?.queryParameter("language"))
        assertNull("免认证第三方域名不得带上用户 token", request.getHeader("Authorization"))
    }

    @Test
    fun `服务端报错原文透传到横幅且不崩溃`() {
        FakeEpic.shopStatus = 503
        FakeEpic.shopBody = Fixtures.errorJson("errors.com.epicgames.maintenance", "商城服务暂时维护中")
        compose.onNodeWithContentDescription(Copy.refresh).performClick()

        pumpUntil(diagnose = FakeEpic::describe) {
            nodesWithText("商城服务暂时维护中", substring = true).isNotEmpty()
        }
        val shown = nodesWithText("HTTP 503", substring = true).isNotEmpty() ||
            nodesWithText(Copy.shopFromCache, substring = true).isNotEmpty()
        assertTrue("要么直显 HTTP 503，要么明示回退到缓存", shown)
    }

    @Test
    fun `关于对话框呈现免责声明与加密说明`() {
        clickTab(Copy.tabAccounts)
        compose.onNodeWithContentDescription(Copy.aboutTitle).performClick()

        pumpUntil(diagnose = FakeEpic::describe) {
            nodesWithText(Copy.aboutUnofficial, substring = true).isNotEmpty()
        }
        compose.onNodeWithText(Copy.aboutCredential, substring = true).assertExists()
        compose.onNodeWithText(Copy.aboutRisk, substring = true).assertExists()
        compose.onNodeWithText(Copy.close).performClick()
    }

    @Test
    fun `打印首屏语义树留作文本证据`() {
        pumpUntil(diagnose = FakeEpic::describe) { nodesWithText(Fixtures.ITEM_NAME).isNotEmpty() }
        val tree = compose.onRoot().printToString()
        println("=== 今日商城首屏语义树 ===\n$tree")
        assertTrue(tree.contains(Copy.tabTodayShop))
        assertTrue(tree.contains(Fixtures.ITEM_NAME))
    }

    private fun clickTab(label: String) {
        compose.onNode(hasText(label) and hasClickAction()).performClick()
    }

    private fun nodesWithText(text: String, substring: Boolean = false) =
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes()

    private fun shopRequests() = FakeEpic.requests.count { it.path?.substringBefore('?') == "/v2/shop" }
}
