package com.fnassistantdog.core.shop

/** 归一化后的商城卡片（渲染单元）。取值链照 Glow ShopManager.ts:131-219。 */
data class ShopCard(
    val offerId: String,
    val name: String,
    val description: String,
    val typeDisplay: String,
    val typeValue: String,
    val rarity: String,
    val rarityLabel: String,
    val imageUrl: String,
    val price: Int,
    val regularPrice: Int,
    val giftable: Boolean,
    val isBundle: Boolean,
    val itemCount: Int,
    /** brItems 的 templateId 列表，Phase 2 的"已拥有"比对与购买明细要用 */
    val itemIds: List<String>,
    val sectionId: String,
    val outDate: String?,
)

enum class ShopSource { ONLINE, CACHE }

data class ShopSnapshot(
    val entries: List<ShopCard>,
    /** 缓存 key 用的 UTC 日（yyyyMMdd）。绝不使用本地日，见 docs/phase-1-设计.md 与 F3 关键坑 */
    val utcDay: String,
    val fetchedAt: Long,
    val source: ShopSource,
    /** 非空表示"在线拉取失败、正在回退展示缓存"，UI 要提示 */
    val degraded: String? = null,
)

/** 商城 JSON 缓存端口（app 侧用 DataStore 实现）。 */
interface ShopCache {
    suspend fun save(utcDay: String, json: String, savedAt: Long)

    suspend fun load(utcDay: String): CachedShop?

    suspend fun newest(): CachedShop?

    suspend fun pruneKeeping(keepDays: List<String>)
}

data class CachedShop(
    val utcDay: String,
    val json: String,
    val savedAt: Long,
)
