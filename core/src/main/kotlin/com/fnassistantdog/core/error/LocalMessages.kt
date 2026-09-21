package com.fnassistantdog.core.error

/**
 * 本地校验/状态文案（措辞逐字取自原 App dex 中文字符串取证）。
 * 放在 :core 是因为抛错的主体是 :core 的业务校验，UI 直接显示 displayMessage()。
 */
object LocalMessages {
    const val AT_LEAST_ONE_CODE = "请至少输入一个授权码"
    const val CODE_HAS_SPACE = "授权码不能包含空格"
    const val CODE_LENGTH_INVALID = "授权码长度无效"
    const val DUPLICATE_CODE = "授权码中存在重复项"
    const val TOO_MANY_CODES = "一次最多添加 20 个账号"
    const val ACCOUNT_LIMIT = "账号数量已达上限，最多 20 个账号"

    const val NO_LOCAL_CREDENTIALS = "未找到此账号的本地凭据"
    const val NO_DEVICE_AUTH = "该账号没有可用的设备凭据，须获取新的 Epic 授权码"
    const val CORRUPTED_CREDENTIALS = "本地凭据无法解密，须获取新的 Epic 授权码"
    const val MISSING_ACCOUNT_ID = "Epic 未返回账号 ID"
    const val MISSING_ACCESS_TOKEN = "未返回客户端访问令牌"
    const val INCOMPLETE_DEVICE_AUTH = "返回了不完整的设备凭据"

    const val SHOP_NO_ENTRIES = "商城未返回商品条目"
    const val FRIENDS_NO_DATA = "好友服务未返回数据"
    const val EMPTY_FRIENDS = "empty_friends_response"
}
