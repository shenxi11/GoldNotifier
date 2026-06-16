package com.example.goldnotifier.data.repository

import com.example.goldnotifier.data.api.GoldApi
import com.example.goldnotifier.data.local.GoldLocalStore
import com.example.goldnotifier.data.model.ApiResponse
import com.example.goldnotifier.data.model.GoldCandleBarDto
import com.example.goldnotifier.data.model.GoldCandlesResponseDto
import com.example.goldnotifier.data.model.GoldHistoryPointDto
import com.example.goldnotifier.data.model.GoldHistoryResponseDto
import com.example.goldnotifier.data.model.GoldPriceDto
import com.example.goldnotifier.domain.model.GoldPrice
import com.example.goldnotifier.domain.model.GoldTrendSnapshot
import com.example.goldnotifier.domain.trend.TrendTimeRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId

/*
模块名: GoldRepositoryTest
功能概述: 验证最新金价刷新、缓存回退和 stale 回写策略。
对外接口: JUnit 测试用例
依赖关系: GoldRepository、GoldApi、GoldLocalStore
输入输出: 输入模拟 API 与本地缓存，输出仓库刷新结果断言。
异常与错误: 网络失败或业务错误码必须回退缓存，不能让上层崩溃。
维护说明: 新增缓存策略时同步补充这里的测试。
*/
class GoldRepositoryTest {
    @Test
    fun refreshGoldPriceStoresFreshFinnhubResponse() = runBlocking {
        val freshDto = GoldPriceDto(
            name = "现货黄金",
            symbol = "XAU",
            price = 891.13,
            change = 0.0,
            changePercent = 0.0,
            unit = "元/克",
            openPrice = 890.77,
            previousClose = 890.77,
            high = 891.20,
            low = 890.70,
            updateTime = "2026-06-11 17:16:11",
            serverTime = "2026-06-11 17:16:11",
            source = "finnhub",
            marketStatus = "trading",
            isStale = false,
        )
        val localStore = FakeGoldLocalStore()
        val repository = GoldRepository(
            api = FakeGoldApi(
                response = ApiResponse(
                    code = 0,
                    message = "success",
                    data = freshDto,
                ),
            ),
            localStore = localStore,
        )

        val result = repository.refreshGoldPrice()

        assertFalse(result.fromCache)
        assertEquals(null, result.message)
        assertNotNull(result.price)
        assertEquals("finnhub", result.price?.source)
        assertFalse(result.price?.isStale ?: true)
        assertEquals("finnhub", localStore.currentCachedPrice?.source)
        assertFalse(localStore.currentCachedPrice?.isStale ?: true)
    }

    @Test
    fun refreshGoldPriceDeduplicatesCallsInsideRefreshInterval() = runBlocking {
        val freshDto = GoldPriceDto(
            name = "现货黄金",
            symbol = "XAU",
            price = 891.13,
            change = 0.0,
            changePercent = 0.0,
            unit = "元/克",
            openPrice = 890.77,
            previousClose = 890.77,
            high = 891.20,
            low = 890.70,
            updateTime = "2026-06-11 17:16:11",
            serverTime = "2026-06-11 17:16:11",
            source = "finnhub",
            marketStatus = "trading",
            isStale = false,
        )
        val api = FakeGoldApi(
            response = ApiResponse(
                code = 0,
                message = "success",
                data = freshDto,
            ),
        )
        val repository = GoldRepository(
            api = api,
            localStore = FakeGoldLocalStore(),
        )

        val firstResult = repository.refreshGoldPrice()
        val secondResult = repository.refreshGoldPrice()

        assertEquals(1, api.callCount)
        assertEquals(firstResult.price, secondResult.price)
    }

    @Test
    fun refreshGoldPriceForceBypassesDuplicateThrottle() = runBlocking {
        val freshDto = GoldPriceDto(
            name = "现货黄金",
            symbol = "XAU",
            price = 891.13,
            change = 0.0,
            changePercent = 0.0,
            unit = "元/克",
            openPrice = 890.77,
            previousClose = 890.77,
            high = 891.20,
            low = 890.70,
            updateTime = "2026-06-11 17:16:11",
            serverTime = "2026-06-11 17:16:11",
            source = "finnhub",
            marketStatus = "trading",
            isStale = false,
        )
        val api = FakeGoldApi(
            response = ApiResponse(
                code = 0,
                message = "success",
                data = freshDto,
            ),
        )
        val repository = GoldRepository(
            api = api,
            localStore = FakeGoldLocalStore(),
        )

        repository.refreshGoldPrice()
        repository.refreshGoldPrice(force = true)

        assertEquals(2, api.callCount)
    }

    @Test
    fun refreshGoldPriceFallsBackToCachedQuoteAndMarksItStale() = runBlocking {
        val cachedQuote = GoldPrice(
            name = "现货黄金",
            symbol = "XAU",
            price = 890.00,
            change = -1.13,
            changePercent = -0.13,
            unit = "元/克",
            openPrice = 890.77,
            previousClose = 890.77,
            high = 891.20,
            low = 889.60,
            updateTime = "2026-06-11 17:16:11",
            serverTime = "2026-06-11 17:16:11",
            source = "finnhub",
            marketStatus = "trading",
            isStale = false,
        )
        val localStore = FakeGoldLocalStore(initialPrice = cachedQuote)
        val repository = GoldRepository(
            api = FakeGoldApi(
                response = ApiResponse(
                    code = 503,
                    message = "upstream failed",
                    data = null,
                ),
            ),
            localStore = localStore,
        )

        val result = repository.refreshGoldPrice()

        assertTrue(result.fromCache)
        assertEquals("行情数据可能延迟", result.message)
        assertNotNull(result.price)
        assertEquals("cache", result.price?.source)
        assertTrue(result.price?.isStale ?: false)
        assertEquals("cache", localStore.currentCachedPrice?.source)
        assertTrue(localStore.currentCachedPrice?.isStale ?: false)
    }

    @Test
    fun refreshGoldPriceFallsBackToCachedQuoteWhenApiThrows() = runBlocking {
        val cachedQuote = GoldPrice(
            name = "现货黄金",
            symbol = "XAU",
            price = 890.00,
            change = -1.13,
            changePercent = -0.13,
            unit = "元/克",
            openPrice = 890.77,
            previousClose = 890.77,
            high = 891.20,
            low = 889.60,
            updateTime = "2026-06-11 17:16:11",
            serverTime = "2026-06-11 17:16:11",
            source = "finnhub",
            marketStatus = "trading",
            isStale = false,
        )
        val localStore = FakeGoldLocalStore(initialPrice = cachedQuote)
        val repository = GoldRepository(
            api = FakeGoldApi(error = IOException("network down")),
            localStore = localStore,
        )

        val result = repository.refreshGoldPrice()

        assertTrue(result.fromCache)
        assertEquals("行情数据可能延迟", result.message)
        assertNotNull(result.price)
        assertEquals("cache", result.price?.source)
        assertTrue(result.price?.isStale ?: false)
        assertEquals("cache", localStore.currentCachedPrice?.source)
        assertTrue(localStore.currentCachedPrice?.isStale ?: false)
    }

    @Test
    fun fetchTrendHistoryRequestsDatesAndReturnsSortedValidPoints() = runBlocking {
        val nowMillis = timestampMillis("2026-06-16T00:05:00")
        val api = FakeGoldApi(
            historyResponses = ArrayDeque(
                listOf(
                    ApiResponse(
                        code = 0,
                        message = "success",
                        data = historyResponse(
                            date = "2026-06-15",
                            points = listOf(
                                historyPoint(timestampMillis("2026-06-14T23:59:59"), 880.0),
                                historyPoint(timestampMillis("2026-06-15T23:59:57"), 891.2),
                            ),
                        ),
                    ),
                    ApiResponse(
                        code = 0,
                        message = "success",
                        data = historyResponse(
                            date = "2026-06-16",
                            points = listOf(
                                historyPoint(timestampMillis("2026-06-16T00:00:03"), 892.1),
                                historyPoint(timestampMillis("2026-06-16T00:03:03"), null),
                                historyPoint(timestampMillis("2026-06-16T00:04:03"), 892.6),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val repository = GoldRepository(
            api = api,
            localStore = FakeGoldLocalStore(),
            wallClockMillis = { nowMillis },
        )

        val result = repository.fetchTrendHistory(range = TrendTimeRange.OneDay)

        assertEquals(null, result.message)
        assertEquals(listOf(891.2, 892.1, 892.6), result.points.map { it.price })
        assertEquals(listOf("2026-06-15", "2026-06-16"), api.historyCalls.map { it.date })
        assertTrue(api.historyCalls.all { it.limit == 10_000 })
    }

    @Test
    fun fetchTrendCandlesRequestsServerRangeAndReturnsSortedValidBars() = runBlocking {
        val api = FakeGoldApi(
            candleResponses = ArrayDeque(
                listOf(
                    ApiResponse(
                        code = 0,
                        message = "success",
                        data = candlesResponse(
                            range = "1h",
                            bars = listOf(
                                candleBar(timestampMillis("2026-06-16T00:02:00"), 892.1, 893.0, 891.8, 892.6),
                                candleBar(timestampMillis("2026-06-16T00:01:00"), 891.2, 892.2, 890.9, 892.1),
                                candleBar(timestampMillis("2026-06-16T00:03:00"), null, 893.0, 891.8, 892.6),
                            ),
                        ),
                    )
                ),
            ),
        )
        val repository = GoldRepository(
            api = api,
            localStore = FakeGoldLocalStore(),
        )

        val result = repository.fetchTrendCandles(range = TrendTimeRange.OneHour)

        assertEquals(null, result.message)
        assertEquals("1h", api.candleCalls.single().range)
        assertEquals(
            listOf(
                timestampMillis("2026-06-16T00:01:00"),
                timestampMillis("2026-06-16T00:02:00"),
            ),
            result.candles.map { it.timestampMillis },
        )
    }

    private class FakeGoldApi(
        private val response: ApiResponse<GoldPriceDto>? = null,
        private val error: Throwable? = null,
        private val historyResponses: ArrayDeque<ApiResponse<GoldHistoryResponseDto>> = ArrayDeque(),
        private val candleResponses: ArrayDeque<ApiResponse<GoldCandlesResponseDto>> = ArrayDeque(),
    ) : GoldApi {
        var callCount = 0
            private set
        val historyCalls = mutableListOf<HistoryCall>()
        val candleCalls = mutableListOf<CandleCall>()

        override suspend fun getLatestGold(symbol: String): ApiResponse<GoldPriceDto> {
            callCount += 1
            error?.let { throw it }
            return requireNotNull(response) { "response required" }
        }

        override suspend fun getGoldHistory(
            symbol: String,
            date: String,
            startMillis: Long,
            endMillis: Long,
            limit: Int,
        ): ApiResponse<GoldHistoryResponseDto> {
            historyCalls += HistoryCall(
                symbol = symbol,
                date = date,
                startMillis = startMillis,
                endMillis = endMillis,
                limit = limit,
            )
            return historyResponses.removeFirst()
        }

        override suspend fun getGoldCandles(
            symbol: String,
            range: String,
        ): ApiResponse<GoldCandlesResponseDto> {
            candleCalls += CandleCall(
                symbol = symbol,
                range = range,
            )
            return candleResponses.removeFirst()
        }

        override suspend fun getAppConfig() = throw UnsupportedOperationException("Not used")
    }

    private class FakeGoldLocalStore(
        initialPrice: GoldPrice? = null,
    ) : GoldLocalStore {
        private val cachedPrice = MutableStateFlow(initialPrice)
        private val trendSnapshot = MutableStateFlow<GoldTrendSnapshot?>(null)
        private val enabled = MutableStateFlow(false)
        private val refreshInterval = MutableStateFlow(3)

        val currentCachedPrice: GoldPrice?
            get() = cachedPrice.value

        override val cachedGoldPrice: Flow<GoldPrice?> = cachedPrice.asStateFlow()
        override val cachedTrendSnapshot: Flow<GoldTrendSnapshot?> = trendSnapshot.asStateFlow()
        override val notificationEnabled: Flow<Boolean> = enabled.asStateFlow()
        override val refreshIntervalSeconds: Flow<Int> = refreshInterval.asStateFlow()

        override suspend fun setNotificationEnabled(enabled: Boolean) {
            this.enabled.value = enabled
        }

        override suspend fun setRefreshIntervalSeconds(seconds: Int) {
            refreshInterval.value = seconds
        }

        override suspend fun cacheGoldPrice(price: GoldPrice) {
            cachedPrice.value = price
        }

        override suspend fun cacheTrendSnapshot(snapshot: GoldTrendSnapshot) {
            trendSnapshot.value = snapshot
        }
    }
}

private data class HistoryCall(
    val symbol: String,
    val date: String,
    val startMillis: Long,
    val endMillis: Long,
    val limit: Int,
)

private data class CandleCall(
    val symbol: String,
    val range: String,
)

private fun historyResponse(
    date: String,
    points: List<GoldHistoryPointDto>,
) = GoldHistoryResponseDto(
    symbol = "XAU",
    date = date,
    timezone = "Asia/Shanghai",
    count = points.size,
    points = points,
)

private fun historyPoint(
    timestampMillis: Long,
    price: Double?,
) = GoldHistoryPointDto(
    timestampMillis = timestampMillis,
    price = price,
    updateTime = "2026-06-16 00:00:00",
    serverTime = "2026-06-16 00:00:00",
    source = "finnhub",
)

private fun candlesResponse(
    range: String,
    bars: List<GoldCandleBarDto>,
) = GoldCandlesResponseDto(
    symbol = "XAU",
    range = range,
    resolution = "1m",
    timezone = "Asia/Shanghai",
    count = bars.size,
    bars = bars,
)

private fun candleBar(
    timestampMillis: Long,
    open: Double?,
    high: Double?,
    low: Double?,
    close: Double?,
) = GoldCandleBarDto(
    timestampMillis = timestampMillis,
    open = open,
    high = high,
    low = low,
    close = close,
)

private fun timestampMillis(value: String): Long =
    LocalDateTime.parse(value)
        .atZone(ZoneId.of("Asia/Shanghai"))
        .toInstant()
        .toEpochMilli()
