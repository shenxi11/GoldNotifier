package com.example.goldnotifier.data.repository

import com.example.goldnotifier.data.api.GoldApi
import com.example.goldnotifier.data.local.GoldLocalStore
import com.example.goldnotifier.data.local.UserSettingsDataStore
import com.example.goldnotifier.data.model.GoldCandleBarDto
import com.example.goldnotifier.data.model.GoldHistoryPointDto
import com.example.goldnotifier.data.model.toDomain
import com.example.goldnotifier.data.model.toDomainOrNull
import com.example.goldnotifier.domain.model.GoldCandle
import com.example.goldnotifier.domain.model.GoldPrice
import com.example.goldnotifier.domain.model.GoldTrendPoint
import com.example.goldnotifier.domain.model.GoldTrendSnapshot
import com.example.goldnotifier.domain.model.STALE_QUOTE_MESSAGE
import com.example.goldnotifier.domain.trend.TrendTimeRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/*
模块名: GoldRepository
功能概述: 协调服务端接口、本地缓存和用户设置，提供客户端单一数据入口。
对外接口: cachedGoldPrice、refreshGoldPrice、fetchTrendHistory、fetchTrendCandles、setNotificationEnabled、setRefreshIntervalSeconds
依赖关系: GoldApi、GoldLocalStore
输入输出: 输入网络响应和用户设置，输出 UI/通知可消费的 GoldRefreshResult。
异常与错误: 网络或解析失败时优先返回本地缓存，并附带延迟提示。
维护说明: 所有缓存策略集中在 Repository，避免分散到 ViewModel 或 Service。
*/
class GoldRepository(
    private val api: GoldApi,
    private val localStore: GoldLocalStore,
    private val wallClockMillis: () -> Long = { System.currentTimeMillis() },
    private val monotonicMillis: () -> Long = {
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
    },
) {
    val cachedGoldPrice: Flow<GoldPrice?> = localStore.cachedGoldPrice
    val cachedTrendSnapshot: Flow<GoldTrendSnapshot?> = localStore.cachedTrendSnapshot
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

    suspend fun fetchTrendHistory(
        symbol: String = "XAU",
        range: TrendTimeRange,
    ): GoldTrendHistoryResult {
        val endMillis = wallClockMillis()
        val startMillis = endMillis - range.windowMillis
        return runCatching {
            val points = historyDatesBetween(startMillis, endMillis)
                .flatMap { date ->
                    fetchTrendHistoryForDate(
                        symbol = symbol,
                        date = date,
                        startMillis = startMillis,
                        endMillis = endMillis,
                    )
                }
                .filter { point -> point.timestampMillis in startMillis..endMillis }
                .distinctBy { point -> point.timestampMillis }
                .sortedBy { point -> point.timestampMillis }
            GoldTrendHistoryResult(points = points, message = null)
        }.getOrElse { error ->
            GoldTrendHistoryResult(
                points = emptyList(),
                message = error.message?.takeIf { it.isNotBlank() } ?: "历史趋势获取失败",
            )
        }
    }

    suspend fun fetchTrendCandles(
        symbol: String = "XAU",
        range: TrendTimeRange,
    ): GoldCandlesResult {
        return runCatching {
            val response = api.getGoldCandles(
                symbol = symbol,
                range = range.serverRange,
            )
            if (response.code != 0) {
                error(response.message.ifBlank { "服务端返回错误码 ${response.code}" })
            }
            val candles = response.data
                ?.bars
                .orEmpty()
                .mapNotNull(GoldCandleBarDto::toDomainOrNull)
                .distinctBy { candle -> candle.timestampMillis }
                .sortedBy { candle -> candle.timestampMillis }
            GoldCandlesResult(candles = candles, message = null)
        }.getOrElse { error ->
            GoldCandlesResult(
                candles = emptyList(),
                message = error.message?.takeIf { it.isNotBlank() } ?: "K线数据获取失败",
            )
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

    private suspend fun fetchTrendHistoryForDate(
        symbol: String,
        date: String,
        startMillis: Long,
        endMillis: Long,
    ): List<GoldTrendPoint> {
        val points = mutableListOf<GoldTrendPoint>()
        var pageEndMillis = endMillis
        while (pageEndMillis >= startMillis) {
            val page = fetchTrendHistoryPage(
                symbol = symbol,
                date = date,
                startMillis = startMillis,
                endMillis = pageEndMillis,
            )
            if (page.isEmpty()) break
            points += page
            val earliest = page.minOf { point -> point.timestampMillis }
            if (page.size < HISTORY_PAGE_LIMIT || earliest <= startMillis) break
            pageEndMillis = earliest - 1
        }
        return points
    }

    private suspend fun fetchTrendHistoryPage(
        symbol: String,
        date: String,
        startMillis: Long,
        endMillis: Long,
    ): List<GoldTrendPoint> {
        val response = api.getGoldHistory(
            symbol = symbol,
            date = date,
            startMillis = startMillis,
            endMillis = endMillis,
            limit = HISTORY_PAGE_LIMIT,
        )
        if (response.code != 0) {
            error(response.message.ifBlank { "服务端返回错误码 ${response.code}" })
        }
        return response.data
            ?.points
            .orEmpty()
            .mapNotNull(GoldHistoryPointDto::toDomainOrNull)
    }

    private fun historyDatesBetween(startMillis: Long, endMillis: Long): List<String> {
        val dates = mutableListOf<String>()
        var date = localDateOf(startMillis)
        val endDate = localDateOf(endMillis)
        while (!date.isAfter(endDate)) {
            dates += date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            date = date.plusDays(1)
        }
        return dates
    }

    private fun localDateOf(timestampMillis: Long): LocalDate =
        Instant.ofEpochMilli(timestampMillis).atZone(HISTORY_ZONE).toLocalDate()

    suspend fun latestCachedGoldPrice(): GoldPrice? = cachedGoldPrice.first()

    suspend fun latestTrendSnapshot(): GoldTrendSnapshot? = cachedTrendSnapshot.first()

    suspend fun cacheTrendSnapshot(snapshot: GoldTrendSnapshot) {
        localStore.cacheTrendSnapshot(snapshot)
    }

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

data class GoldTrendHistoryResult(
    val points: List<GoldTrendPoint>,
    val message: String?,
)

data class GoldCandlesResult(
    val candles: List<GoldCandle>,
    val message: String?,
)

private const val HISTORY_PAGE_LIMIT = 10_000
private val HISTORY_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
