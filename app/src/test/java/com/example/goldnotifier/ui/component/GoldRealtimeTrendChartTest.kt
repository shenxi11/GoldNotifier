package com.example.goldnotifier.ui.component

import com.example.goldnotifier.domain.model.GoldTrendPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun maxDrawPointsUseChartWidthBounds() {
        assertEquals(80, calculateTrendCurveMaxDrawPoints(chartWidth = 100f))
        assertEquals(125, calculateTrendCurveMaxDrawPoints(chartWidth = 500f))
        assertEquals(240, calculateTrendCurveMaxDrawPoints(chartWidth = 1200f))
    }

    @Test
    fun interpolationKeepsSmallInputUnchanged() {
        val chartPoints = listOf(
            chartPoint(x = 0f, y = 20f),
            chartPoint(x = 10f, y = 10f),
            chartPoint(x = 20f, y = 16f),
        )

        val interpolated = interpolateTrendChartPoints(chartPoints, maxDrawPoints = 6)

        assertEquals(chartPoints, interpolated)
    }

    @Test
    fun interpolationLimitsDenseInputAndKeepsCriticalPoints() {
        val chartPoints = listOf(
            chartPoint(x = 0f, y = 10f),
            chartPoint(x = 1f, y = 9f),
            chartPoint(x = 2f, y = 8f),
            chartPoint(x = 3f, y = 1f),
            chartPoint(x = 4f, y = 7f),
            chartPoint(x = 5f, y = 6f),
            chartPoint(x = 6f, y = 18f),
            chartPoint(x = 7f, y = 5f),
            chartPoint(x = 8f, y = 4f),
            chartPoint(x = 9f, y = 3f),
        )

        val interpolated = interpolateTrendChartPoints(chartPoints, maxDrawPoints = 6)

        assertTrue(interpolated.size <= 6)
        assertTrue(interpolated.contains(chartPoint(x = 0f, y = 10f)))
        assertTrue(interpolated.contains(chartPoint(x = 9f, y = 3f)))
        assertTrue(interpolated.contains(chartPoint(x = 3f, y = 1f)))
        assertTrue(interpolated.contains(chartPoint(x = 6f, y = 18f)))
    }

    @Test
    fun interpolationUsesLinearYBetweenNeighborPoints() {
        val interpolated = interpolateTrendChartPoints(
            points = listOf(
                chartPoint(x = 0f, y = 0f),
                chartPoint(x = 10f, y = 10f),
                chartPoint(x = 20f, y = 20f),
                chartPoint(x = 30f, y = 30f),
            ),
            maxDrawPoints = 3,
        )

        assertEquals(3, interpolated.size)
        assertEquals(15f, interpolated[1].x, 0.001f)
        assertEquals(15f, interpolated[1].y, 0.001f)
    }

    @Test
    fun interpolatedPointsRemainSortedAndFiniteForCurveSegments() {
        val chartPoints = (0..120).map { index ->
            chartPoint(
                x = index.toFloat(),
                y = if (index % 2 == 0) 12f else 18f,
            )
        }

        val interpolated = interpolateTrendChartPoints(chartPoints, maxDrawPoints = 24)
        val segments = buildTrendCurveSegments(interpolated)

        assertTrue(interpolated.zipWithNext().all { (start, end) -> start.x < end.x })
        assertTrue(interpolated.all { point -> point.x.isFinite() && point.y.isFinite() })
        assertEquals(interpolated.size - 1, segments.size)
    }

    @Test
    fun smoothCurveSegmentsPassThroughRealPoints() {
        val chartPoints = listOf(
            chartPoint(x = 0f, y = 20f),
            chartPoint(x = 10f, y = 4f),
            chartPoint(x = 20f, y = 16f),
            chartPoint(x = 30f, y = 8f),
        )

        val segments = buildTrendCurveSegments(chartPoints)

        assertEquals(chartPoints.size - 1, segments.size)
        segments.forEachIndexed { index, segment ->
            assertEquals(chartPoints[index], segment.start)
            assertEquals(chartPoints[index + 1], segment.end)
            assertTrue(segment.isCurved)
        }
    }

    @Test
    fun smoothCurveControlsStayWithinSegmentBounds() {
        val segments = buildTrendCurveSegments(
            listOf(
                chartPoint(x = 0f, y = 40f),
                chartPoint(x = 8f, y = 10f),
                chartPoint(x = 16f, y = 38f),
                chartPoint(x = 24f, y = 12f),
                chartPoint(x = 32f, y = 20f),
            ),
        )

        segments.forEach { segment ->
            val minY = minOf(segment.start.y, segment.end.y) - 0.001f
            val maxY = maxOf(segment.start.y, segment.end.y) + 0.001f
            assertTrue(segment.firstControl.y in minY..maxY)
            assertTrue(segment.secondControl.y in minY..maxY)
        }
    }

    @Test
    fun smoothCurveUsesLineForTwoPoints() {
        val segments = buildTrendCurveSegments(
            listOf(
                chartPoint(x = 0f, y = 20f),
                chartPoint(x = 10f, y = 10f),
            ),
        )

        assertEquals(1, segments.size)
        assertFalse(segments.single().isCurved)
    }

    @Test
    fun smoothCurveKeepsLastDuplicateXPoint() {
        val segments = buildTrendCurveSegments(
            listOf(
                chartPoint(x = 0f, y = 20f),
                chartPoint(x = 10f, y = 18f),
                chartPoint(x = 10f, y = 12f),
                chartPoint(x = 20f, y = 8f),
            ),
        )

        assertEquals(2, segments.size)
        assertEquals(12f, segments.first().end.y, 0.001f)
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

    private fun chartPoint(
        x: Float,
        y: Float,
    ) = TrendChartPoint(x = x, y = y)
}
