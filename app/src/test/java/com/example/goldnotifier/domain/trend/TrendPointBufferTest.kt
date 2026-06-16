package com.example.goldnotifier.domain.trend

import com.example.goldnotifier.domain.model.GoldPrice
import com.example.goldnotifier.domain.model.GoldTrendPoint
import org.junit.Assert.assertEquals
import org.junit.Test

/*
模块名: TrendPointBufferTest
功能概述: 验证实时趋势点缓冲区的追加、过滤和窗口裁剪策略。
对外接口: JUnit 测试用例
依赖关系: TrendPointBuffer、GoldPrice、JUnit
输入输出: 输入模拟行情与时间推进，输出缓冲区快照断言。
异常与错误: 无效行情必须返回跳过原因，避免 UI 追加缓存或延迟价格。
维护说明: 调整趋势窗口或追加间隔时同步更新这些边界断言。
*/
class TrendPointBufferTest {
    private var nowMillis = 10_000L

    @Test
    fun appendIfValidStoresFreshPoint() {
        val buffer = newBuffer()

        val result = buffer.appendIfValid(price = price(891.13), fromCache = false)

        assertEquals(AppendResult.Appended, result)
        assertEquals(listOf(891.13), buffer.snapshot().map { it.price })
    }

    @Test
    fun appendIfValidSkipsCacheAndStalePoints() {
        val buffer = newBuffer()

        assertEquals(AppendResult.SkippedStaleOrCache, buffer.appendIfValid(price(891.13), fromCache = true))
        assertEquals(AppendResult.SkippedStaleOrCache, buffer.appendIfValid(price(891.13, isStale = true), fromCache = false))
        assertEquals(AppendResult.SkippedStaleOrCache, buffer.appendIfValid(price(891.13, source = "cache"), fromCache = false))

        assertEquals(0, buffer.snapshot().size)
    }

    @Test
    fun appendIfValidSkipsInvalidAndTooSoonPoints() {
        val buffer = newBuffer()

        assertEquals(AppendResult.SkippedInvalidPrice, buffer.appendIfValid(price(0.0), fromCache = false))
        assertEquals(AppendResult.Appended, buffer.appendIfValid(price(891.13), fromCache = false))
        nowMillis += 1_000L
        assertEquals(AppendResult.SkippedTooSoon, buffer.appendIfValid(price(891.20), fromCache = false))

        assertEquals(listOf(891.13), buffer.snapshot().map { it.price })
    }

    @Test
    fun appendIfValidTrimsOutsideWindow() {
        val buffer = newBuffer(windowMillis = 5_000L)

        assertEquals(AppendResult.Appended, buffer.appendIfValid(price(891.13), fromCache = false))
        nowMillis += 3_000L
        assertEquals(AppendResult.Appended, buffer.appendIfValid(price(891.20), fromCache = false))
        nowMillis += 3_000L
        assertEquals(AppendResult.Appended, buffer.appendIfValid(price(891.30), fromCache = false))

        assertEquals(listOf(891.20, 891.30), buffer.snapshot().map { it.price })
    }

    @Test
    fun snapshotCanUseShorterWindow() {
        val buffer = newBuffer(windowMillis = 10_000L)

        assertEquals(AppendResult.Appended, buffer.appendIfValid(price(891.10), fromCache = false))
        nowMillis += 3_000L
        assertEquals(AppendResult.Appended, buffer.appendIfValid(price(891.20), fromCache = false))
        nowMillis += 3_000L
        assertEquals(AppendResult.Appended, buffer.appendIfValid(price(891.30), fromCache = false))

        assertEquals(listOf(891.20, 891.30), buffer.snapshot(windowMillis = 3_000L).map { it.price })
        assertEquals(listOf(891.10, 891.20, 891.30), buffer.snapshot().map { it.price })
    }

    @Test
    fun restoreFiltersInvalidPointsAndSortsByTime() {
        val buffer = newBuffer()

        buffer.restore(
            listOf(
                point(timestampMillis = 13_000L, price = 891.30),
                point(timestampMillis = 11_000L, price = 891.10),
                point(timestampMillis = 12_000L, price = -1.0),
                point(timestampMillis = 14_000L, price = 891.40, source = "cache"),
                point(timestampMillis = 15_000L, price = 891.50, isStale = true),
            ),
        )

        assertEquals(listOf(891.10, 891.30), buffer.snapshot().map { it.price })
    }

    private fun newBuffer(windowMillis: Long = 300_000L) = TrendPointBuffer(
        windowMillis = windowMillis,
        minAppendIntervalMillis = 2_500L,
        nowMillis = { nowMillis },
    )

    private fun price(
        price: Double,
        source: String = "finnhub",
        isStale: Boolean = false,
    ) = GoldPrice(
        name = "现货黄金",
        symbol = "XAU",
        price = price,
        change = 0.0,
        changePercent = 0.0,
        unit = "元/克",
        openPrice = price,
        previousClose = price,
        high = price,
        low = price,
        updateTime = "2026-06-15 17:16:11",
        serverTime = "2026-06-15 17:16:11",
        source = source,
        marketStatus = "trading",
        isStale = isStale,
    )

    private fun point(
        timestampMillis: Long,
        price: Double,
        source: String = "finnhub",
        isStale: Boolean = false,
    ) = GoldTrendPoint(
        timestampMillis = timestampMillis,
        price = price,
        updateTime = "2026-06-15 17:16:11",
        source = source,
        isStale = isStale,
    )
}
