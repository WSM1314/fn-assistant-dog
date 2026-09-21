package com.fnassistantdog.core.model

import com.google.gson.annotations.SerializedName

/**
 * 今日商城 DTO。字段形状来自本机抓包的真实响应
 * （fortnite-api.com/v2/shop?language=zh-Hans，2026-09-20，346 条 entry），
 * 取值优先级照 Glow `ShopManager.downloadShop()`（src/main/managers/shop/ShopManager.ts:119-219）平移。
 */
data class ShopResponse(
    @SerializedName("status") val status: Int? = null,
    @SerializedName("data") val data: ShopData? = null,
)

data class ShopData(
    @SerializedName("hash") val hash: String? = null,
    /** 商城所属 UTC 日，例 2026-09-20T00:00:00Z —— 缓存 key 用它，不用本地日 */
    @SerializedName("date") val date: String? = null,
    @SerializedName("vbuckIcon") val vbuckIcon: String? = null,
    @SerializedName("entries") val entries: List<ShopEntryDto>? = null,
)

data class ShopEntryDto(
    @SerializedName("offerId") val offerId: String? = null,
    @SerializedName("devName") val devName: String? = null,
    @SerializedName("regularPrice") val regularPrice: Int? = null,
    @SerializedName("finalPrice") val finalPrice: Int? = null,
    @SerializedName("giftable") val giftable: Boolean = false,
    @SerializedName("refundable") val refundable: Boolean = false,
    @SerializedName("sortPriority") val sortPriority: Int? = null,
    @SerializedName("layoutId") val layoutId: String? = null,
    @SerializedName("tileSize") val tileSize: String? = null,
    @SerializedName("inDate") val inDate: String? = null,
    @SerializedName("outDate") val outDate: String? = null,
    @SerializedName("brItems") val brItems: List<ShopItemDto>? = null,
    @SerializedName("bundle") val bundle: ShopBundleDto? = null,
    @SerializedName("newDisplayAsset") val newDisplayAsset: ShopDisplayAssetDto? = null,
    @SerializedName("offerTag") val offerTag: String? = null,
)

data class ShopItemDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("type") val type: ShopNamedValue? = null,
    @SerializedName("rarity") val rarity: ShopNamedValue? = null,
    @SerializedName("series") val series: ShopSeriesDto? = null,
    @SerializedName("set") val set: ShopNamedValue? = null,
    @SerializedName("images") val images: ShopImagesDto? = null,
)

data class ShopBundleDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("type") val type: ShopNamedValue? = null,
    @SerializedName("rarity") val rarity: ShopNamedValue? = null,
    @SerializedName("series") val series: ShopSeriesDto? = null,
    @SerializedName("images") val images: ShopImagesDto? = null,
)

/** rarity / type / set 共用形状：{value, displayValue, backendValue} */
data class ShopNamedValue(
    @SerializedName("value") val value: String? = null,
    @SerializedName("displayValue") val displayValue: String? = null,
    @SerializedName("backendValue") val backendValue: String? = null,
)

data class ShopSeriesDto(
    @SerializedName("value") val value: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("colors") val colors: List<String>? = null,
)

/** 抓包所见 key：smallIcon / icon / featured / otherPlaceholder / lego{small,large} */
data class ShopImagesDto(
    @SerializedName("smallIcon") val smallIcon: String? = null,
    @SerializedName("icon") val icon: String? = null,
    @SerializedName("featured") val featured: String? = null,
    @SerializedName("otherPlaceholder") val otherPlaceholder: String? = null,
)

data class ShopDisplayAssetDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("renderImages") val renderImages: List<ShopRenderImageDto>? = null,
)

data class ShopRenderImageDto(
    @SerializedName("productTag") val productTag: String? = null,
    @SerializedName("image") val image: String? = null,
)
