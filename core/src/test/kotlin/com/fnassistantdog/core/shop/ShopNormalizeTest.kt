package com.fnassistantdog.core.shop

import com.fnassistantdog.core.api.FortniteApi
import com.fnassistantdog.core.auth.Clock
import com.fnassistantdog.core.error.EpicException
import com.fnassistantdog.core.error.EpicFailure
import com.fnassistantdog.core.model.ShopEntryDto
import com.fnassistantdog.core.model.ShopItemDto
import com.fnassistantdog.core.model.ShopResponse
import com.fnassistantdog.core.util.UtcDay
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/**
 * 归一化测试。fixture 取自本机真实抓包（fortnite-api.com/v2/shop?language=zh-Hans，
 * 2026-09-20，346 条）裁出的样本：捆绑包 / 无 brItems / 普通两条目。
 */
class ShopNormalizeTest {
    private val fixture: ShopResponse = Gson().fromJson(readFixture(), ShopResponse::class.java)

    @Test
    fun `无 brItems 的条目被跳过，其余成为卡片`() {
        val entries = fixture.data!!.entries!!
        val cards = entries.mapNotNull { toCard(it) }
        assertTrue(entries.size > cards.size)
        assertEquals(cards.size, entries.count { !it.brItems.isNullOrEmpty() })
    }

    @Test
    fun `捆绑包卡片带子件数量与折扣价`() {
        val bundle = fixture.data!!.entries!!.mapNotNull { toCard(it) }.first { it.isBundle }
        assertTrue(bundle.itemCount > 1)
        assertTrue(bundle.giftable)
        assertEquals(1000, bundle.price)
        assertEquals(1500, bundle.regularPrice)
        assertEquals("Bundle", bundle.typeDisplay)
        assertEquals("rare", bundle.rarity)
        assertTrue(bundle.imageUrl.isNotBlank())
        assertEquals(bundle.itemCount, bundle.itemIds.size)
        assertTrue(bundle.offerId.startsWith("v2:/"))
        assertEquals("2026-09-21T23:59:59.999Z", bundle.outDate)
    }

    @Test
    fun `单件卡片的类型与中文名来自 brItems`() {
        val plain = fixture.data!!.entries!!.mapNotNull { toCard(it) }.first { !it.isBundle }
        assertFalse(plain.name.isBlank())
        assertEquals(2, plain.itemCount)
        assertEquals("outfit", plain.typeValue)
    }

    @Test
    fun `名字为 TBD 或 unknown 的条目不展示`() {
        val tbd = ShopEntryDto(
            offerId = "v2:/synthetic",
            brItems = listOf(ShopItemDto(id = "CID_TBD", name = "TBD")),
            finalPrice = 0,
            regularPrice = 0,
        )
        val unknown = tbd.copy(offerId = "v2:/synthetic2", brItems = listOf(ShopItemDto(id = "X", name = "unknown")))
        assertNull(toCard(tbd))
        assertNull(toCard(unknown))
    }
}

class UtcDayTest {
    @Test
    fun `ISO 日期取 UTC 日`() {
        assertEquals("20260920", UtcDay.fromIso("2026-09-20T00:00:00Z"))
        assertEquals("20260920", UtcDay.fromIso("2026-09-20T16:04:00.000Z"))
        assertNull(UtcDay.fromIso("not-a-date"))
        assertEquals("2026-09-20", UtcDay.pretty("20260920"))
    }

    @Test
    fun `北京时间 08 点前仍属前一 UTC 日`() {
        // 2026-09-19 23:00 UTC == 2026-09-20 07:00 北京
        assertEquals("20260919", UtcDay.of(utcMillis(2026, 9, 19, 23, 0)))
        assertEquals("20260919", UtcDay.of(utcMillis(2026, 9, 19, 23, 59)))
    }

    @Test
    fun `缓存裁剪用的往前 N 天不跨月出错`() {
        assertEquals(listOf("20260901", "20260831", "20260830"), ShopRepository.recentUtcDays("20260901", 3))
    }

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.time.time
}

class ShopCacheTest {
    @Test
    fun `缓存命中时不打网络`() = runTest {
        val api = FakeFortniteApi(failWith = AssertionError("缓存命中时不应请求网络"))
        val cache = FakeShopCache()
        cache.store(UTC_DAY, readFixture(), NOW - 60_000)
        val repo = ShopRepository(api, cache, FakeClock(NOW))

        val snapshot = repo.load(force = false)

        assertTrue(snapshot.entries.isNotEmpty())
        assertEquals(ShopSource.CACHE, snapshot.source)
        assertEquals(UTC_DAY, snapshot.utcDay)
        assertNull(snapshot.degraded)
        assertEquals(0, api.calls)
    }

    @Test
    fun `网络失败时回退缓存并带出失败原因`() = runTest {
        val api = FakeFortniteApi(failWith = EpicException(EpicFailure.Timeout(20)))
        val cache = FakeShopCache()
        cache.store("20260919", readFixture(), NOW - 86_400_000)
        val repo = ShopRepository(api, cache, FakeClock(NOW))

        val snapshot = repo.load(force = true)

        assertEquals(ShopSource.CACHE, snapshot.source)
        assertEquals("20260919", snapshot.utcDay)
        assertTrue(snapshot.degraded.orEmpty().contains("超时"))
    }

    @Test
    fun `无缓存且网络失败时抛出而不是静默`() = runTest {
        val api = FakeFortniteApi(failWith = EpicException(EpicFailure.Unreachable("SSL handshake")))
        val repo = ShopRepository(api, FakeShopCache(), FakeClock(NOW))
        var thrown: Throwable? = null
        try {
            repo.load(force = false)
        } catch (failure: Throwable) {
            thrown = failure
        }
        assertTrue(thrown is EpicException)
        assertEquals(1, api.calls)
    }

    @Test
    fun `成功拉取后按 UTC 日写缓存并裁剪旧日`() = runTest {
        val api = FakeFortniteApi(body = readFixture())
        val cache = FakeShopCache()
        cache.store("20260917", readFixture(), 1L)
        val repo = ShopRepository(api, cache, FakeClock(NOW))

        val snapshot = repo.refresh()

        assertTrue(snapshot.entries.isNotEmpty())
        assertEquals(ShopSource.ONLINE, snapshot.source)
        assertEquals(UTC_DAY, snapshot.utcDay)
        assertTrue(cache.keysSaved.contains(UTC_DAY))
        assertTrue(cache.deletedDays.contains("20260917"))
    }

    private companion object {
        // 2026-09-20T00:30Z（北京时间 08:30，即 UTC 日刚切换之后）
        const val NOW = 1_789_864_200_000L
        const val UTC_DAY = "20260920"
    }
}

internal fun readFixture(): String =
    checkNotNull(ShopCacheTest::class.java.getResourceAsStream("/shop-fixture.json"))
        .reader(Charsets.UTF_8).readText()

private class FakeClock(private val now: Long) : Clock {
    override fun nowMillis(): Long = now
}

private class FakeFortniteApi(
    private val body: String? = null,
    private val failWith: Throwable? = null,
) : FortniteApi {
    var calls = 0
        private set

    override suspend fun shop(language: String): Response<ShopResponse> {
        calls += 1
        failWith?.let { throw it }
        return Response.success(Gson().fromJson(body, ShopResponse::class.java))
    }
}

private class FakeShopCache : ShopCache {
    private val docs = LinkedHashMap<String, CachedShop>()
    val keysSaved = ArrayList<String>()
    val deletedDays = ArrayList<String>()

    fun store(day: String, json: String, savedAt: Long) {
        docs[day] = CachedShop(day, json, savedAt)
    }

    override suspend fun save(utcDay: String, json: String, savedAt: Long) {
        docs[utcDay] = CachedShop(utcDay, json, savedAt)
        keysSaved += utcDay
    }

    override suspend fun load(utcDay: String): CachedShop? = docs[utcDay]

    override suspend fun newest(): CachedShop? = docs.keys.maxOrNull()?.let { docs[it] }

    override suspend fun pruneKeeping(keepDays: List<String>) {
        val doomed = docs.keys.filter { it !in keepDays }
        doomed.forEach { deletedDays += it }
        doomed.forEach { docs.remove(it) }
    }
}
