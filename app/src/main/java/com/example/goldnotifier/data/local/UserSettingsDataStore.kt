package com.example.goldnotifier.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.goldnotifier.domain.model.GoldPrice
import com.example.goldnotifier.domain.model.GoldTrendSnapshot
import com.example.goldnotifier.domain.model.isQuoteStale
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.goldNotifierDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "gold_notifier_preferences",
)

interface GoldLocalStore {
    val cachedGoldPrice: Flow<GoldPrice?>
    val cachedTrendSnapshot: Flow<GoldTrendSnapshot?>
    val notificationEnabled: Flow<Boolean>
    val refreshIntervalSeconds: Flow<Int>

    suspend fun setNotificationEnabled(enabled: Boolean)

    suspend fun setRefreshIntervalSeconds(seconds: Int)

    suspend fun cacheGoldPrice(price: GoldPrice)

    suspend fun cacheTrendSnapshot(snapshot: GoldTrendSnapshot)
}

/*
模块名: UserSettingsDataStore
功能概述: 保存刷新频率、通知开关和最近一次成功金价缓存。
对外接口: GoldLocalStore、observeNotificationEnabled、observeRefreshIntervalSeconds、observeCachedGoldPrice 等。
依赖关系: Android DataStore Preferences、GoldPrice
输入输出: 输入用户设置或接口成功行情，输出 Flow 形式的本地状态。
异常与错误: DataStore IO 异常时回退为空偏好，避免启动链路崩溃。
维护说明: MVP 使用 Preferences DataStore；后续历史行情可迁移到 Room。
*/
class UserSettingsDataStore(context: Context) : GoldLocalStore {
    private val dataStore = context.goldNotifierDataStore
    private val gson = Gson()

    override val notificationEnabled: Flow<Boolean> = dataStore.safeData()
        .map { preferences -> preferences[Keys.NotificationEnabled] ?: false }

    override val refreshIntervalSeconds: Flow<Int> = dataStore.safeData()
        .map { preferences ->
            sanitizeRefreshIntervalSeconds(
                preferences[Keys.RefreshIntervalSeconds] ?: DEFAULT_REFRESH_INTERVAL_SECONDS,
            )
        }

    override val cachedGoldPrice: Flow<GoldPrice?> = dataStore.safeData()
        .map { preferences -> preferences.toGoldPriceOrNull() }

    override val cachedTrendSnapshot: Flow<GoldTrendSnapshot?> = dataStore.safeData()
        .map { preferences -> preferences.toTrendSnapshotOrNull() }

    override suspend fun setNotificationEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.NotificationEnabled] = enabled
        }
    }

    override suspend fun setRefreshIntervalSeconds(seconds: Int) {
        val sanitized = sanitizeRefreshIntervalSeconds(seconds)
        dataStore.edit { preferences ->
            preferences[Keys.RefreshIntervalSeconds] = sanitized
        }
    }

    override suspend fun cacheGoldPrice(price: GoldPrice) {
        dataStore.edit { preferences ->
            preferences[Keys.Name] = price.name
            preferences[Keys.Symbol] = price.symbol
            preferences[Keys.Price] = price.price
            preferences[Keys.Change] = price.change
            preferences[Keys.ChangePercent] = price.changePercent
            preferences[Keys.Unit] = price.unit
            preferences[Keys.OpenPrice] = price.openPrice
            preferences[Keys.PreviousClose] = price.previousClose
            preferences[Keys.High] = price.high
            preferences[Keys.Low] = price.low
            preferences[Keys.UpdateTime] = price.updateTime
            preferences[Keys.ServerTime] = price.serverTime
            preferences[Keys.Source] = price.source
            preferences[Keys.MarketStatus] = price.marketStatus
            preferences[Keys.IsStale] = price.isQuoteStale()
        }
    }

    override suspend fun cacheTrendSnapshot(snapshot: GoldTrendSnapshot) {
        dataStore.edit { preferences ->
            preferences[Keys.TrendSnapshotJson] = gson.toJson(snapshot)
            preferences[Keys.TrendSnapshotSavedAtMillis] = snapshot.savedAtMillis
        }
    }

    private fun DataStore<Preferences>.safeData(): Flow<Preferences> = data.catch { error ->
        if (error is IOException) {
            emit(androidx.datastore.preferences.core.emptyPreferences())
        } else {
            throw error
        }
    }

    private fun Preferences.toGoldPriceOrNull(): GoldPrice? {
        val price = this[Keys.Price] ?: return null
        val source = this[Keys.Source] ?: "cache"
        return GoldPrice(
            name = this[Keys.Name] ?: "现货黄金",
            symbol = this[Keys.Symbol] ?: "XAU",
            price = price,
            change = this[Keys.Change] ?: 0.0,
            changePercent = this[Keys.ChangePercent] ?: 0.0,
            unit = this[Keys.Unit] ?: "元/克",
            openPrice = this[Keys.OpenPrice] ?: 0.0,
            previousClose = this[Keys.PreviousClose] ?: 0.0,
            high = this[Keys.High] ?: 0.0,
            low = this[Keys.Low] ?: 0.0,
            updateTime = this[Keys.UpdateTime] ?: "",
            serverTime = this[Keys.ServerTime] ?: "",
            source = source,
            marketStatus = this[Keys.MarketStatus] ?: "unknown",
            isStale = (this[Keys.IsStale] ?: false) || source.equals("cache", ignoreCase = true),
        )
    }

    private fun Preferences.toTrendSnapshotOrNull(): GoldTrendSnapshot? {
        val json = this[Keys.TrendSnapshotJson] ?: return null
        return try {
            gson.fromJson(json, GoldTrendSnapshot::class.java)
        } catch (_: JsonSyntaxException) {
            null
        }
    }

    private object Keys {
        val NotificationEnabled = booleanPreferencesKey("notification_enabled")
        val RefreshIntervalSeconds = intPreferencesKey("refresh_interval_seconds")
        val Name = stringPreferencesKey("gold_name")
        val Symbol = stringPreferencesKey("gold_symbol")
        val Price = doublePreferencesKey("gold_price")
        val Change = doublePreferencesKey("gold_change")
        val ChangePercent = doublePreferencesKey("gold_change_percent")
        val Unit = stringPreferencesKey("gold_unit")
        val OpenPrice = doublePreferencesKey("gold_open_price")
        val PreviousClose = doublePreferencesKey("gold_previous_close")
        val High = doublePreferencesKey("gold_high")
        val Low = doublePreferencesKey("gold_low")
        val UpdateTime = stringPreferencesKey("gold_update_time")
        val ServerTime = stringPreferencesKey("gold_server_time")
        val Source = stringPreferencesKey("gold_source")
        val MarketStatus = stringPreferencesKey("gold_market_status")
        val IsStale = booleanPreferencesKey("gold_is_stale")
        val TrendSnapshotJson = stringPreferencesKey("trend_snapshot_json")
        val TrendSnapshotSavedAtMillis = longPreferencesKey("trend_snapshot_saved_at_millis")
    }

    companion object {
        const val MIN_REFRESH_INTERVAL_SECONDS = 3
        const val DEFAULT_REFRESH_INTERVAL_SECONDS = 3
        val SUPPORTED_REFRESH_INTERVALS = listOf(MIN_REFRESH_INTERVAL_SECONDS)

        fun sanitizeRefreshIntervalSeconds(seconds: Int): Int =
            seconds.takeIf { it in SUPPORTED_REFRESH_INTERVALS }
                ?: DEFAULT_REFRESH_INTERVAL_SECONDS
    }
}
