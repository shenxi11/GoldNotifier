package com.example.goldnotifier.ui.component

import com.example.goldnotifier.domain.model.GoldCandle
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/*
模块名: TradingViewKLineChartTest
功能概述: 验证 TradingView K 线图的增量更新决策，避免拖拽时反复全量刷新。
对外接口: JUnit 测试用例
依赖关系: resolveCandleChartUpdate、GoldCandle、JUnit
输入输出: 输入前后两组 K 线，输出 setData、update 或 no-op 决策断言。
异常与错误: 无运行时异常，非法 K 线过滤由 UI 组件内部执行。
维护说明: 调整 K 线刷新策略时同步更新这些性能回归测试。
*/
class TradingViewKLineChartTest {
    @Test
    fun firstRenderUsesFullSetData() {
        val result = resolveCandleChartUpdate(
            previousRangeKey = null,
            previousCandles = emptyList(),
            nextRangeKey = "5m",
            nextCandles = listOf(candle(0, 940.0)),
        )

        assertEquals(CandleChartUpdate.SetData, result)
    }

    @Test
    fun rangeChangeUsesFullSetData() {
        val previous = listOf(candle(0, 940.0))
        val next = listOf(candle(0, 941.0))

        val result = resolveCandleChartUpdate(
            previousRangeKey = "5m",
            previousCandles = previous,
            nextRangeKey = "1h",
            nextCandles = next,
        )

        assertEquals(CandleChartUpdate.SetData, result)
    }

    @Test
    fun unchangedCandlesDoNothing() {
        val candles = listOf(candle(0, 940.0), candle(60_000, 941.0))

        val result = resolveCandleChartUpdate(
            previousRangeKey = "1h",
            previousCandles = candles,
            nextRangeKey = "1h",
            nextCandles = candles,
        )

        assertEquals(CandleChartUpdate.None, result)
    }

    @Test
    fun lastCandleReplacementUsesIncrementalUpdate() {
        val previous = listOf(candle(0, 940.0), candle(60_000, 941.0))
        val next = listOf(candle(0, 940.0), candle(60_000, 941.4))

        val result = resolveCandleChartUpdate(
            previousRangeKey = "1h",
            previousCandles = previous,
            nextRangeKey = "1h",
            nextCandles = next,
        )

        assertEquals(CandleChartUpdate.UpdateLast, result)
    }

    @Test
    fun appendingNextCandleUsesIncrementalUpdate() {
        val previous = listOf(candle(0, 940.0), candle(60_000, 941.0))
        val next = previous + candle(120_000, 942.0)

        val result = resolveCandleChartUpdate(
            previousRangeKey = "1h",
            previousCandles = previous,
            nextRangeKey = "1h",
            nextCandles = next,
        )

        assertEquals(CandleChartUpdate.UpdateLast, result)
    }

    @Test
    fun historicalCorrectionUsesFullSetData() {
        val previous = listOf(candle(0, 940.0), candle(60_000, 941.0))
        val next = listOf(candle(0, 940.8), candle(60_000, 941.0))

        val result = resolveCandleChartUpdate(
            previousRangeKey = "1h",
            previousCandles = previous,
            nextRangeKey = "1h",
            nextCandles = next,
        )

        assertEquals(CandleChartUpdate.SetData, result)
    }

    @Test
    fun shanghaiTimestampDisplaysAsShanghaiTimeOnTradingViewUtcAxis() {
        val shanghaiMillis = LocalDateTime.of(2026, 6, 16, 16, 0)
            .atZone(ZoneId.of("Asia/Shanghai"))
            .toInstant()
            .toEpochMilli()

        val displayTime = Instant.ofEpochSecond(
            toTradingViewShanghaiTimestampSeconds(shanghaiMillis),
        ).atZone(ZoneOffset.UTC).toLocalTime()

        assertEquals(LocalTime.of(16, 0), displayTime)
    }

    private fun candle(
        timestampMillis: Long,
        close: Double,
    ) = GoldCandle(
        timestampMillis = timestampMillis,
        open = 940.0,
        high = maxOf(940.0, close),
        low = minOf(940.0, close),
        close = close,
    )
}
