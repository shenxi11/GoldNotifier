package com.example.goldnotifier.ui.component

import com.example.goldnotifier.domain.model.GoldTrendPoint
import org.junit.Assert.assertEquals
import org.junit.Test

/*
模块名: GoldRealtimeTrendChartTest
功能概述: 验证趋势图左侧价格刻度的动态计算策略。
对外接口: JUnit 测试用例
依赖关系: GoldRealtimeTrendChart、GoldTrendPoint、JUnit
输入输出: 输入模拟趋势点，输出价格刻度数量、数值和展示文本断言。
异常与错误: 非法点或非法刻度数量应返回空刻度，避免绘图层处理异常状态。
维护说明: 调整横向网格线数量或刻度格式时同步更新这些断言。
*/
class GoldRealtimeTrendChartTest {
    @Test
    fun priceAxisLabelsUseMaxAndMinAsBounds() {
        val labels = calculateTrendPriceAxisLabels(
            points = listOf(
                point(timestampMillis = 1L, price = 941.20),
                point(timestampMillis = 2L, price = 945.80),
                point(timestampMillis = 3L, price = 936.10),
            ),
        )

        assertEquals(4, labels.size)
        assertEquals(945.80, labels.first().price, 0.001)
        assertEquals("945.80", labels.first().text)
        assertEquals(936.10, labels.last().price, 0.001)
        assertEquals("936.10", labels.last().text)
    }

    @Test
    fun priceAxisLabelsUseDynamicEvenStep() {
        val labels = calculateTrendPriceAxisLabels(
            points = listOf(
                point(timestampMillis = 1L, price = 900.0),
                point(timestampMillis = 2L, price = 960.0),
            ),
        )

        assertEquals(listOf("960.00", "940.00", "920.00", "900.00"), labels.map { it.text })
    }

    @Test
    fun priceAxisLabelsStayStableWhenPricesAreFlat() {
        val labels = calculateTrendPriceAxisLabels(
            points = listOf(
                point(timestampMillis = 1L, price = 941.08),
                point(timestampMillis = 2L, price = 941.08),
            ),
        )

        assertEquals(listOf("941.08", "941.08", "941.08", "941.08"), labels.map { it.text })
    }

    @Test
    fun priceAxisLabelsReturnEmptyForInvalidInput() {
        assertEquals(emptyList<TrendPriceAxisLabel>(), calculateTrendPriceAxisLabels(emptyList()))
        assertEquals(
            emptyList<TrendPriceAxisLabel>(),
            calculateTrendPriceAxisLabels(listOf(point(timestampMillis = 1L, price = 0.0))),
        )
        assertEquals(
            emptyList<TrendPriceAxisLabel>(),
            calculateTrendPriceAxisLabels(listOf(point(timestampMillis = 1L, price = 941.0)), labelCount = 1),
        )
    }

    private fun point(
        timestampMillis: Long,
        price: Double,
    ) = GoldTrendPoint(
        timestampMillis = timestampMillis,
        price = price,
        updateTime = "2026-06-16 00:00:00",
        source = "finnhub",
    )
}
