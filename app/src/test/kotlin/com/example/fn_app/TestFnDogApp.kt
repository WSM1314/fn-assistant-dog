package com.example.fn_app

import android.content.Context
import com.example.fn_app.di.AppContainer
import com.fnassistantdog.core.auth.CredentialCipher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/**
 * 本地（JVM）跑真 App：Robolectric 启动真实 MainActivity 与 Compose 树，
 * 只有两处替换 —— 四个 API 基址指向本机 MockWebServer，加密换成可逆实现
 * （AndroidKeyStore 在 JVM 沙箱里不存在）。Room / DataStore / OkHttp / Retrofit / WorkManager 全走真实实现。
 */
object FakeEpic {
    // 按路径应答，避免"先入队还是先发请求"的竞态；每个测试可临时改这些字段。
    @Volatile var shopBody: String = Fixtures.shopJson

    @Volatile var shopStatus: Int = 200
    @Volatile var tokenBody: String = Fixtures.tokenJson
    @Volatile var deviceAuthBody: String = Fixtures.deviceAuthJson
    @Volatile var accountBody: String = Fixtures.accountJson
    @Volatile var friendsBody: String = Fixtures.friendsJson

    val requests = java.util.concurrent.CopyOnWriteArrayList<okhttp3.mockwebserver.RecordedRequest>()

    val server: MockWebServer = MockWebServer().apply {
        dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                requests += request
                val path = request.path.orEmpty().substringBefore('?')
                val (status, body) = when {
                    path.endsWith("/v2/shop") -> shopStatus to shopBody
                    path.endsWith("/oauth/token") -> 200 to tokenBody
                    path.contains("/deviceAuth") -> 200 to deviceAuthBody
                    path.endsWith("/summary") -> 200 to friendsBody
                    path.contains("/api/v1/search/") -> 200 to "{\"entities\":[]}"
                    path.contains("/public/account/") -> 200 to accountBody
                    else -> 404 to Fixtures.errorJson("not_routed", "未预设的路径 $path")
                }
                return MockResponse()
                    .setResponseCode(status)
                    .setHeader("Content-Type", "application/json")
                    .setBody(body)
            }
        }
        start()
    }

    fun bases(): AppContainer.Bases = AppContainer.Bases(
        account = server.url("/account/api/").toString(),
        friends = server.url("/friends/api/v1/").toString(),
        userSearch = server.url("/api/v1/search/").toString(),
        fortnite = server.url("/").toString(),
    )

    fun reset() {
        requests.clear()
        shopBody = Fixtures.shopJson
        shopStatus = 200
        tokenBody = Fixtures.tokenJson
        deviceAuthBody = Fixtures.deviceAuthJson
        accountBody = Fixtures.accountJson
        friendsBody = Fixtures.friendsJson
    }

    fun firstMatching(predicate: (okhttp3.mockwebserver.RecordedRequest) -> Boolean): okhttp3.mockwebserver.RecordedRequest? =
        requests.firstOrNull(predicate)

    /** 超时诊断用：Mock 服务器看到过什么 */
    fun describe(): String =
        if (requests.isEmpty()) "MockWebServer(端口 ${server.port}) 一个请求都没收到 —— 请求没发出或被代理劫走"
        else "已收到 ${requests.size} 个请求：" + requests.joinToString(", ") { "${it.method} ${it.path}" }
}

/** 可逆假加密：加 "ENC:" 前缀，便于断言库里存的确实不是明文 */
class ReversibleCipher : CredentialCipher {
    override fun encrypt(plain: ByteArray): ByteArray = (MARKER + plain.reversedArray())

    override fun decrypt(blob: ByteArray): ByteArray {
        check(blob.size > MARKER.size && blob.copyOfRange(0, MARKER.size).contentEquals(MARKER)) { "不是本假加密产出的密文" }
        return blob.copyOfRange(MARKER.size, blob.size).reversedArray()
    }

    companion object {
        val MARKER = "ENC:".toByteArray(Charsets.UTF_8)
    }
}

class TestFnDogApp : FnDogApp() {
    override fun buildContainer(context: Context): AppContainer = AppContainer(
        context,
        bases = FakeEpic.bases(),
        cipherFactory = { ReversibleCipher() },
    )
}

/** 响应样本的字段形状与抓包所得一致（brItems/finalPrice/giftable；Epic 的 account_id/access_token 等）。 */
object Fixtures {
    const val ACCOUNT_ID = "acc-fixture-0001"
    const val ACCESS_TOKEN = "tok-fixture-abcdef"
    const val DEVICE_ID = "dev-fixture-1"
    const val DEVICE_SECRET = "sec-fixture-1"
    const val DISPLAY_NAME = "测试昵称甲"
    const val FRIEND_NAME = "好友甲"
    const val ITEM_NAME = "杰利"
    const val AUTH_CODE = "fixtureauthorizationcode1"

    val shopJson = """
        {"status":200,"data":{"hash":"h1","date":"2026-09-20T00:00:00Z","vbuckIcon":"https://x/vbuck.png","entries":[
        {"offerId":"v2:/offer-a","devName":"[VIRTUAL]1 x A","regularPrice":1500,"finalPrice":1000,"giftable":true,
         "refundable":false,"sortPriority":1,"layoutId":"BR092026.95","tileSize":"Size_1_x_1",
         "outDate":"2026-09-21T23:59:59.999Z",
         "brItems":[{"id":"CID_A","name":"$ITEM_NAME","description":"你不想与之为敌的海葵。",
           "type":{"value":"outfit","displayValue":"皮肤","backendValue":"AthenaCharacter"},
           "rarity":{"value":"rare","displayValue":"稀有","backendValue":"EFortRarity::Rare"},
           "images":{"smallIcon":"https://x/a_small.png","icon":"https://x/a_icon.png","featured":"https://x/a_featured.png"}}]},
        {"offerId":"v2:/offer-b","regularPrice":500,"finalPrice":500,"giftable":false,"layoutId":"Daily",
         "brItems":[]}
        ]}}
    """.trimIndent()

    val tokenJson = """
        {"access_token":"$ACCESS_TOKEN","token_type":"bearer","expires_in":7200,
         "account_id":"$ACCOUNT_ID","display_name":"$DISPLAY_NAME","client_id":"c"}
    """.trimIndent()

    val deviceAuthJson = """{"deviceId":"$DEVICE_ID","secret":"$DEVICE_SECRET","note":"fn"}"""

    val accountJson = """{"id":"$ACCOUNT_ID","displayName":"$DISPLAY_NAME"}"""

    val friendsJson = """
        {"friends":[{"accountId":"friend-1","displayName":"$FRIEND_NAME","favorite":false,"created":"2026-01-01T00:00:00.000Z"}],
         "incoming":[{"accountId":"stranger-1","displayName":"陌生人甲"}],
         "outgoing":[],"blocked":[]}
    """.trimIndent()

    fun errorJson(code: String, message: String) =
        """{"errorCode":"$code","errorMessage":"$message","messageVars":[],"numericErrorCode":1}"""
}
