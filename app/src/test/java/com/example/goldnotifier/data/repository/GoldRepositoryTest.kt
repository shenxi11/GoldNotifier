package com.example.goldnotifier.data.repository

import com.example.goldnotifier.data.api.GoldApi
import com.example.goldnotifier.data.local.GoldLocalStore
import com.example.goldnotifier.data.model.ApiResponse
import com.example.goldnotifier.data.model.GoldPriceDto
import com.example.goldnotifier.domain.model.GoldPrice
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

    private class FakeGoldApi(
        private val response: ApiResponse<GoldPriceDto>? = null,
        private val error: Throwable? = null,
    ) : GoldApi {
        var callCount = 0
            private set

        override suspend fun getLatestGold(symbol: String): ApiResponse<GoldPriceDto> {
            callCount += 1
            error?.let { throw it }
            return requireNotNull(response) { "response required" }
        }

        override suspend fun getAppConfig() = throw UnsupportedOperationException("Not used")
    }

    private class FakeGoldLocalStore(
        initialPrice: GoldPrice? = null,
    ) : GoldLocalStore {
        private val cachedPrice = MutableStateFlow(initialPrice)
        private val enabled = MutableStateFlow(false)
        private val refreshInterval = MutableStateFlow(3)

        val currentCachedPrice: GoldPrice?
            get() = cachedPrice.value

        override val cachedGoldPrice: Flow<GoldPrice?> = cachedPrice.asStateFlow()
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
    }
}
