package com.fnassistantdog.core.shop

import com.fnassistantdog.core.api.FortniteApi
import com.fnassistantdog.core.auth.Clock
import com.fnassistantdog.core.error.EpicException
import com.fnassistantdog.core.error.EpicFailure
import com.fnassistantdog.core.error.LocalMessages
import com.fnassistantdog.core.http.apiCall
import com.fnassistantdog.core.http.sharedGson
import com.fnassistantdog.core.model.ShopBundleDto
import com.fnassistantdog.core.model.ShopEntryDto
import com.fnassistantdog.core.model.ShopImagesDto
import com.fnassistantdog.core.model.ShopItemDto
import com.fnassistantdog.core.model.ShopResponse
import com.fnassistantdog.core.util.UtcDay
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.SimpleTimeZone
import java.util.TimeZone

/**
 * 今日商城数据源（F1：GET https://fortnite-api.com/v2/shop，免认证）。
 *
 * 渲染单元归一化的取值链逐条平移 Glow `ShopManager.downloadShop()`
 * （Glow-Launcher-2.4.1/src/main/managers/shop/ShopManager.ts:131-219）。
 * 缓存 key 用 UTC 日，拉取失败时回退缓存并带上去失败原因（不静默、不崩溃）。
 */
class ShopRepository(
    private val api: FortniteApi,
    private val cache: ShopCache,
    private val clock: Clock,
    private val gson: Gson = sharedGson,
) {
    /** 缓存优先；force=true 先打网络。网络失败时回退缓存并把失败原因放进 snapshot.degraded。 */
    suspend fun load(force: Boolean = false): ShopSnapshot {
        val today = UtcDay.of(clock.nowMillis())
        if (!force) {
            readCache(today)?.let { return it }
        }
        val outcome = runCatching { fetch(today) }
        val failure = outcome.exceptionOrNull()
        if (failure == null) return outcome.getOrThrow()
        val fallback = readCache(today) ?: newestCache()
        if (fallback != null) return fallback.copy(degraded = failure.message)
        throw failure
    }

    suspend fun refresh(): ShopSnapshot = load(force = true)

    /** 后台 Worker 用：只关心成功或抛出，不做回退（回退由 UI 侧 load 负责）。 */
    suspend fun download(): ShopSnapshot = fetch(UtcDay.of(clock.nowMillis()))

    private suspend fun fetch(today: String): ShopSnapshot {
        val response = apiCall("今日商城") { api.shop(LANGUAGE) }
        val data = response.data ?: throw EpicException(EpicFailure.BadPayload("data 为空"))
        val rawEntries = data.entries ?: emptyList()
        if (rawEntries.isEmpty()) throw EpicException(EpicFailure.BadPayload(LocalMessages.SHOP_NO_ENTRIES))
        val cards = rawEntries.mapNotNull { toCard(it) }
        val day = UtcDay.fromIso(data.date) ?: today
        val now = clock.nowMillis()
        cache.save(day, gson.toJson(response), now)
        cache.pruneKeeping(recentUtcDays(day, RETAINED_DAYS))
        return ShopSnapshot(entries = cards, utcDay = day, fetchedAt = now, source = ShopSource.ONLINE)
    }

    private suspend fun readCache(day: String): ShopSnapshot? =
        cache.load(day)?.let { parseCached(it) }

    private suspend fun newestCache(): ShopSnapshot? =
        cache.newest()?.let { parseCached(it) }

    private fun parseCached(cached: CachedShop): ShopSnapshot? {
        val response = runCatching { gson.fromJson(cached.json, ShopResponse::class.java) }
            .getOrNull() ?: return null
        val cards = response?.data?.entries.orEmpty().mapNotNull { toCard(it) }
        return ShopSnapshot(
            entries = cards,
            utcDay = cached.utcDay,
            fetchedAt = cached.savedAt,
            source = ShopSource.CACHE,
        )
    }

    companion object {
        const val LANGUAGE = "zh-Hans"
        const val RETAINED_DAYS = 3

        /** 从给定 UTC 日往回数 n 天，用于缓存裁剪 */
        fun recentUtcDays(fromDay: String, count: Int): List<String> {
            val format = SimpleDateFormat("yyyyMMdd", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            val start = runCatching { format.parse(fromDay) }.getOrNull() ?: return listOf(fromDay)
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            calendar.time = start
            val days = ArrayList<String>(count)
            repeat(count) {
                days += format.format(calendar.time)
                calendar.add(Calendar.DAY_OF_MONTH, -1)
            }
            return days
        }
    }
}

/** 单个 entry → 卡片；无 brItems 或名字为 TBD/unknown 的条目按 Glow 逻辑跳过。 */
internal fun toCard(entry: ShopEntryDto): ShopCard? {
    val items: List<ShopItemDto> = entry.brItems.orEmpty()
    if (items.isEmpty()) return null
    val bundle: ShopBundleDto? = entry.bundle
    val isBundle = items.size > 1 && bundle != null
    val first = items.first()

    // Glow: mainItem = (brItems.size>1 && bundle) ? bundle : brItems[0]；name 再回落到 brItems[0].name
    val name = ((if (isBundle) bundle?.name else first.name)?.takeIf { it.isNotBlank() } ?: first.name).orEmpty()
    if (name.isBlank() || name == "TBD" || name.equals("unknown", ignoreCase = true)) return null

    val mainRarity = if (isBundle) bundle?.rarity else first.rarity
    val rarity = (mainRarity?.value ?: first.rarity?.value)?.lowercase() ?: "common"
    val rarityLabel = (mainRarity?.displayValue ?: first.rarity?.displayValue).orEmpty()
    val description = ((if (isBundle) bundle?.description else first.description) ?: first.description).orEmpty()
    val typeValue = first.type?.value.orEmpty()
    val typeDisplay = if (isBundle) "Bundle" else first.type?.displayValue.orEmpty()
    val image = pickImage(if (isBundle) bundle?.images else first.images) ?: pickImage(first.images)
        ?: entryRenderImage(entry) ?: ""

    return ShopCard(
        offerId = entry.offerId.orEmpty(),
        name = name,
        description = description,
        typeDisplay = typeDisplay,
        typeValue = typeValue,
        rarity = rarity,
        rarityLabel = rarityLabel,
        imageUrl = image,
        price = entry.finalPrice ?: entry.regularPrice ?: 0,
        regularPrice = entry.regularPrice ?: 0,
        giftable = entry.giftable,
        isBundle = isBundle,
        itemCount = items.size,
        itemIds = items.mapNotNull { it.id },
        sectionId = entry.layoutId ?: "other",
        outDate = entry.outDate,
    )
}

private fun pickImage(images: ShopImagesDto?): String? =
    images?.featured ?: images?.icon ?: images?.smallIcon

private fun entryRenderImage(entry: ShopEntryDto): String? =
    entry.newDisplayAsset?.renderImages?.firstOrNull { !it.image.isNullOrBlank() }?.image
