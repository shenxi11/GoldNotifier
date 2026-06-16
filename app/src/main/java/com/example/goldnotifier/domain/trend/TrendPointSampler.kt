package com.example.goldnotifier.domain.trend

import com.example.goldnotifier.domain.model.GoldTrendPoint
import kotlin.math.roundToInt

/*
模块名: TrendPointSampler
功能概述: 对长时间窗口趋势点做等距抽样，控制 Compose Canvas 绘制点数。
对外接口: TrendPointSampler
依赖关系: GoldTrendPoint
输入输出: 输入按时间采集的趋势点，输出保留首尾点的抽样点列。
异常与错误: 非法价格点会被过滤；maxPoints 小于 2 时返回空列表。
维护说明: 抽样只影响绘图密度，不改变 Repository 保存或服务端返回的原始历史数据。
*/
object TrendPointSampler {
    const val DEFAULT_MAX_POINTS = 720

    fun sample(
        points: List<GoldTrendPoint>,
        maxPoints: Int = DEFAULT_MAX_POINTS,
    ): List<GoldTrendPoint> {
        if (maxPoints < 2) return emptyList()
        val validPoints = points
            .filter { it.price > 0.0 }
            .sortedBy { it.timestampMillis }
        if (validPoints.size <= maxPoints) return validPoints

        val lastIndex = validPoints.lastIndex
        val sampled = ArrayList<GoldTrendPoint>(maxPoints)
        var lastSampledIndex = -1
        repeat(maxPoints) { targetIndex ->
            val sourceIndex = (targetIndex * lastIndex.toDouble() / (maxPoints - 1))
                .roundToInt()
                .coerceIn(0, lastIndex)
            if (sourceIndex != lastSampledIndex) {
                sampled.add(validPoints[sourceIndex])
                lastSampledIndex = sourceIndex
            }
        }
        return sampled
    }
}
