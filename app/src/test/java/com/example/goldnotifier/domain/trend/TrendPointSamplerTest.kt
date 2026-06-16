package com.example.goldnotifier.domain.trend

import com.example.goldnotifier.domain.model.GoldTrendPoint
import org.junit.Assert.assertEquals
import org.junit.Test

/*
模块名: TrendPointSamplerTest
功能概述: 验证长窗口趋势点抽样策略。
对外接口: JUnit 测试用例
依赖关系: TrendPointSampler、GoldTrendPoint、JUnit
输入输出: 输入模拟趋势点，输出抽样数量和首尾点断言。
异常与错误: maxPoints 非法时应返回空列表，避免绘图层处理异常状态。
维护说明: 调整抽样上限或算法时同步更新这些边界断言。
*/
class TrendPointSamplerTest {
    @Test
    fun sampleKeepsSmallInputUnchanged() {
        val points = listOf(point(2, 892.0), point(1, 891.0))

        val sampled = TrendPointSampler.sample(points, maxPoints = 5)

        assertEquals(listOf(1L, 2L), sampled.map { it.timestampMillis })
    }

    @Test
    fun sampleKeepsFirstAndLastWithinLimit() {
        val points = (0L until 100L).map { index -> point(index, 890.0 + index) }

        val sampled = TrendPointSampler.sample(points, maxPoints = 10)

        assertEquals(10, sampled.size)
        assertEquals(0L, sampled.first().timestampMillis)
        assertEquals(99L, sampled.last().timestampMillis)
    }

    @Test
    fun sampleReturnsEmptyWhenMaxPointsInvalid() {
        val sampled = TrendPointSampler.sample(listOf(point(1, 891.0)), maxPoints = 1)

        assertEquals(emptyList<GoldTrendPoint>(), sampled)
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
