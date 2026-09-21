package com.example.fn_app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fnassistantdog.core.shop.CachedShop
import com.fnassistantdog.core.shop.ShopCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 商城 JSON 缓存（按 UTC 日存，保留最近 3 天）与"当前选中账号"设置。 */
private val Context.shopPrefs: DataStore<Preferences> by preferencesDataStore(name = "shop_cache")
private val Context.appPrefs: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

private const val JSON_PREFIX = "shop_json_"
private const val SAVED_PREFIX = "shop_saved_at_"

private fun jsonKey(day: String) = stringPreferencesKey("$JSON_PREFIX$day")

private fun savedAtKey(day: String) = longPreferencesKey("$SAVED_PREFIX$day")

private fun dayOfShopKey(name: String): String? = when {
    name.startsWith(JSON_PREFIX) -> name.removePrefix(JSON_PREFIX)
    name.startsWith(SAVED_PREFIX) -> name.removePrefix(SAVED_PREFIX)
    else -> null
}

class DataStoreShopCache(context: Context) : ShopCache {
    private val store = context.shopPrefs

    override suspend fun save(utcDay: String, json: String, savedAt: Long) {
        store.edit { prefs ->
            prefs[jsonKey(utcDay)] = json
            prefs[savedAtKey(utcDay)] = savedAt
        }
    }

    override suspend fun load(utcDay: String): CachedShop? {
        val prefs = store.data.first()
        val json = prefs[jsonKey(utcDay)] ?: return null
        return CachedShop(utcDay = utcDay, json = json, savedAt = prefs[savedAtKey(utcDay)] ?: 0L)
    }

    override suspend fun newest(): CachedShop? {
        val day = store.data.first().asMap().keys
            .map { it.name }
            .mapNotNull { dayOfShopKey(it) }
            .maxOrNull() ?: return null
        return load(day)
    }

    override suspend fun pruneKeeping(keepDays: List<String>) {
        val keep = keepDays.toSet()
        store.edit { prefs ->
            val removable = prefs.asMap().keys.filter { key ->
                val day = dayOfShopKey(key.name)
                day != null && day !in keep
            }
            removable.forEach { prefs.remove(it) }
        }
    }
}

class AppSettings(context: Context) {
    private val store = context.appPrefs
    private val keySelected = stringPreferencesKey("selected_account_id")

    val selectedAccountId: Flow<String?> = store.data.map { it[keySelected] }

    suspend fun select(accountId: String?) {
        store.edit { prefs ->
            if (accountId.isNullOrBlank()) prefs.remove(keySelected) else prefs[keySelected] = accountId
        }
    }
}
