package com.example.fn_app.copy

/**
 * 硬编码中文文案集中处（原 App 无 i18n 框架，重建沿用；取证措辞尽量逐字复刻）。
 */
object Copy {
    // 底部导航
    const val tabTodayShop = "今日商城"
    const val tabFriends = "好友"
    const val tabAccounts = "账号"
    const val tabAlerts = "警报"

    // 通用
    const val retry = "重试"
    const val refresh = "刷新"
    const val cancel = "取消"
    const val confirm = "确认"
    const val close = "关闭"
    const val delete = "删除"
    const val loading = "加载中"
    const val notVerified = "未验证"

    // 账号 / 认证
    const val addAccountsTitle = "添加账号"
    const val getAuthCode = "获取授权码"
    const val authCodeHint = "通过 Epic 官方页面取得授权码后添加"
    const val authCodeGuide = "先登录 Epic，再在同一浏览器会话中获取授权码。"
    const val authCodesLabel = "授权码（每行一个）"
    const val maxAccountsOnce = "一次最多添加 20 个账号"
    const val atLeastOneCode = "请至少输入一个授权码"
    const val codeNoSpace = "授权码不能包含空格"
    const val codeLengthInvalid = "授权码长度无效"
    const val duplicateCodes = "授权码中存在重复项"
    const val boundAccountsSuffix = "个账号已加密保存"
    const val credentialExpired = "授权码或账号凭据已失效"
    const val needsNewCode = "须获取新的 Epic 授权码"
    const val deleteAccountConfirm = "将删除此设备上保存的账号登录信息。以后重新添加该账号时，需要再次前往 Epic 获取授权码。"
    const val savedAccountsTitle = "已保存账号"
    const val accountLimitHint = "上限 20 个账号"
    const val bindAccounts = "绑定账号"
    const val refreshing = "处理中"
    const val tokenRemainingPrefix = "令牌剩"
    const val tokenExpired = "令牌已过期"
    const val statusActive = "凭据正常"
    const val statusPending = "处理中"
    const val statusNeedsReauth = "需重新授权"
    const val statusError = "上次操作失败"
    const val statusUnknown = "状态未知"
    const val tokenRefreshed = "已刷新令牌"
    const val openBrowserFailed = "无法打开浏览器，请手动访问 Epic 官方页面获取授权码"
    const val deleteAccount = "删除账号"

    // 商城
    const val shopEmpty = "今日商城没有可展示的商品"
    const val shopNotLoaded = "尚未取得商城数据"
    const val vbucksUnit = "V-Bucks"
    const val bundleBadge = "套装"
    const val shopUpdatedFrom = "更新自"
    const val shopFromCache = "缓存"
    const val shopOnline = "在线"
    const val shopWifiOnly = "仅 Wi-Fi 下载"
    const val shopWifiOnlyWaiting = "仅 Wi-Fi 下载已开启，连接 Wi-Fi 后将更新商城"

    // 好友
    const val friendsEmpty = "还没有好友"
    const val favoriteMark = "收藏"
    const val friendsUnavailable = "好友服务暂时不可用"
    const val friendsNoData = "好友服务未返回数据"
    const val friendRequests = "待处理请求"
    const val acceptAll = "一键接受"
    const val cannotFriendSelf = "不能添加当前账号自己"
    const val addFriend = "添加好友"
    const val accept = "接受"
    const val reject = "拒绝"
    const val removeFriend = "移除"
    const val sectionFriends = "好友"
    const val sectionOutgoing = "已发出请求"
    const val sectionBlocked = "已屏蔽"
    const val searchHint = "输入昵称搜索 Epic 用户"
    const val search = "搜索"
    const val noSearchResults = "没有匹配的用户"
    const val sendRequest = "发送请求"
    const val noAccountsYet = "还没有账号，先在下方粘贴 Epic 授权码添加"
    const val requestSent = "已发送好友请求"
    const val requestAccepted = "已接受好友请求"
    const val friendRemoved = "已移除好友"

    // 错误与网络
    const val networkUnreachable = "网络不可达，请检查代理或网络后重试"
    const val timeout = "请求超时（20 秒），fortnite-api 响应可能较慢，请重试"
    const val serviceUnavailable = "服务暂时不可用"
    const val noData = "服务未返回数据"
    const val invalidResponse = "返回了不完整的登录信息"

    // 关于 / 免责（复刻原 App 精神，必留）
    const val aboutTitle = "关于 FN助手狗"
    const val aboutUnofficial =
        "FN助手狗是非官方 Android 客户端，与 Epic Games 没有隶属、授权或合作关系。" +
            "Fortnite、Epic Games 及相关名称、素材和服务归其各自权利人所有。"
    const val aboutCredential =
        "访问 Token 和刷新 Token 会使用 Android Keystore 派生的密钥加密后保存在应用私有数据库中。" +
            "授权码仅用于当次绑定，不作为普通文本持久保存。应用不提供账号中转服务器，也不应接收他人凭据。"
    const val aboutRisk =
        "请妥善保管 Epic 账号、授权码、Device Auth 和设备登录信息，不要将凭据提供给他人。" +
            "Epic 可能根据其服务条款限制非官方客户端的访问。"
}
