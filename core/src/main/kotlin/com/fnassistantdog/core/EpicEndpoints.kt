package com.fnassistantdog.core

/**
 * 端点基址：逐条取自原版 APK 的 dex 常量池（只读取证），不做臆造。
 * 全 HTTPS，与原 App 的 usesCleartextTraffic=false 一致。
 * 具体路径模板写在各 Retrofit 接口的注解上（那里有 @Path/@Query 约束）。
 */
object EpicEndpoints {
    const val ACCOUNT_BASE = "https://account-public-service-prod.ol.epicgames.com/account/api/"
    const val FRIENDS_BASE = "https://friends-public-service-prod.ol.epicgames.com/friends/api/v1/"
    const val USER_SEARCH_BASE = "https://user-search-service-prod.ol.epicgames.com/api/v1/search/"

    /** 免认证第三方数据源（今日商城）。注意：不得把用户 Bearer 发给这个域名。 */
    const val FORTNITE_API_BASE = "https://fortnite-api.com/"

    /** MCP（Phase 2 的购买/赠送/余额与 Phase 3 的官方回退会用到） */
    const val MCP_BASE = "https://fngw-mcp-gc-livefn.ol.epicgames.com/fortnite/api/"

    /** 用户在浏览器里打开它拿授权码；clientId 与原 App 相同 */
    const val CLIENT_ID = "3f69e56c7649492c8cc29f1af08a8a12"

    const val AUTH_CODE_PAGE =
        "https://www.epicgames.com/id/api/redirect?clientId=$CLIENT_ID&responseType=code"
}
