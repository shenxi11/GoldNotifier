package com.example.goldnotifier.data.repository

import com.example.goldnotifier.data.api.GoldApi
import com.example.goldnotifier.data.local.GoldLocalStore
import com.example.goldnotifier.data.local.UserSettingsDataStore
import com.example.goldnotifier.data.model.toDomain
import com.example.goldnotifier.domain.model.GoldPrice
import com.example.goldnotifier.domain.model.STALE_QUOTE_MESSAGE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/*
模块名: GoldRepository
功能概述: 协调服务端接口、本地缓存和用户设置，提供客户端单一数据入口。
对外接口: cachedGoldPrice、refreshGoldPrice、setNotificationEnabled、setRefreshIntervalSeconds
依赖关系: GoldApi、GoldLocalStore
输入输出: 输入网络响应和用户设置，输出 UI/通知可消费的 GoldRefreshResult。
异常与错误: 网络或解析失败时优先返回本地缓存，并附带延迟提示。
维护说明: 所有缓存策略集中在 Repository，避免分散到 ViewModel 或 Service。
*/
class GoldRepository(
    private val api: GoldApi,
    private val localStore: GoldLocalStore,
) {
    val cachedGoldPrice: Flow<GoldPrice?> = localStore.cachedGoldPrice
    val notificationEnabled: Flow<Boolean> = localStore.notificationEnabled
    val refreshIntervalSeconds: Flow<Int> = localStore.refreshIntervalSeconds

    private val refreshMutex = Mutex()

    @Volatile
    private var lastRefreshStartedAtMillis: Long = 0L
    private var lastRefreshResult: GoldRefreshResult? = null

    suspend fun refreshGoldPrice(
        symbol: String = "XAU",
        force: Boolean = false,
    ): GoldRefreshResult {
        val observedRefreshStartedAt = lastRefreshStartedAtMillis
        return refreshMutex.withLock {
            val nowMillis = monotonicMillis()
            val refreshIntervalMillis = refreshIntervalSeconds.first()
                .coerceAtLeast(UserSettingsDataStore.MIN_REFRESH_INTERVAL_SECONDS) * 1000L
            if (!force) {
                lastRefreshResult?.let { result ->
                    val anotherRefreshCompleted = observedRefreshStartedAt != lastRefreshStartedAtMillis
                    val insideRefreshWindow = lastRefreshStartedAtMillis > 0L &&
                        nowMillis - lastRefreshStartedAtMillis < refreshIntervalMillis
                    if (anotherRefreshCompleted || insideRefreshWindow) {
                        return@withLock result
                    }
                }
            }

            lastRefreshStartedAtMillis = nowMillis
            fetchLatestGoldPrice(symbol).also { result ->
                lastRefreshResult = result
            }
        }
    }

    private suspend fun fetchLatestGoldPrice(symbol: String): GoldRefreshResult {
        return runCatching {
            val response = api.getLatestGold(symbol)
            if (response.code != 0) {
                error(response.message.ifBlank { "服务端返回错误码 ${response.code}" })
            }
            val price = requireNotNull(response.data) { "服务端未返回行情数据" }.toDomain()
            localStore.cacheGoldPrice(price)
            GoldRefreshResult(price = price, fromCache = false, message = null)
        }.getOrElse { error ->
            val cached = cachedGoldPrice.first()?.copy(isStale = true, source = "cache")
            if (cached != null) {
                localStore.cacheGoldPrice(cached)
                GoldRefreshResult(
                    price = cached,
                    fromCache = true,
                    message = STALE_QUOTE_MESSAGE,
                )
            } else {
                GoldRefreshResult(
                    price = null,
                    fromCache = false,
                    message = error.message ?: "行情获取失败",
                )
            }
        }
    }

    private fun monotonicMillis(): Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime())

    suspend fun latestCachedGoldPrice(): GoldPrice? = cachedGoldPrice.first()

    suspend fun setNotificationEnabled(enabled: Boolean) {
        localStore.setNotificationEnabled(enabled)
    }

    suspend fun setRefreshIntervalSeconds(seconds: Int) {
        localStore.setRefreshIntervalSeconds(seconds)
    }
}

data class GoldRefreshResult(
    val price: GoldPrice?,
    val fromCache: Boolean,
    val message: String?,
)
