package com.example.goldnotifier.domain.trend

import com.example.goldnotifier.domain.model.GoldPrice
import com.example.goldnotifier.domain.model.GoldTrendPoint

/*
模块名: TrendPointBuffer
功能概述: 维护首页实时趋势图的内存点列，过滤缓存行情并裁剪时间窗口。
对外接口: TrendPointBuffer、AppendResult
依赖关系: GoldPrice、GoldTrendPoint
输入输出: 输入最新行情刷新结果，输出最近窗口内的趋势点快照。
异常与错误: 对无价格、缓存、延迟和非法价格返回跳过原因，不向调用方抛异常。
维护说明: 默认保留最近 5 分钟；最小追加间隔略低于 3 秒以容忍协程调度抖动。
*/
class TrendPointBuffer(
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    private val minAppendIntervalMillis: Long = MIN_APPEND_INTERVAL_MILLIS,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val points = ArrayDeque<GoldTrendPoint>()

    fun appendIfValid(
        price: GoldPrice?,
        fromCache: Boolean,
    ): AppendResult {
        if (price == null) return AppendResult.SkippedNoPrice
        if (fromCache || price.isStale || price.source.equals("cache", ignoreCase = true)) {
            return AppendResult.SkippedStaleOrCache
        }
        if (price.price <= 0.0) return AppendResult.SkippedInvalidPrice

        val now = nowMillis()
        val last = points.lastOrNull()
        if (last != null && now - last.timestampMillis < minAppendIntervalMillis) {
            trim(now)
            return AppendResult.SkippedTooSoon
        }

        points.addLast(
            GoldTrendPoint(
                timestampMillis = now,
                price = price.price,
                updateTime = price.updateTime,
                source = price.source,
                isStale = price.isStale,
            ),
        )
        trim(now)
        return AppendResult.Appended
    }

    fun restore(restoredPoints: List<GoldTrendPoint>) {
        points.clear()
        restoredPoints
            .filter { point ->
                point.price > 0.0 &&
                    !point.isStale &&
                    !point.source.equals("cache", ignoreCase = true)
            }
            .sortedBy { it.timestampMillis }
            .forEach(points::addLast)
        trim()
    }

    fun snapshot(windowMillis: Long = this.windowMillis): List<GoldTrendPoint> {
        trim()
        val now = nowMillis()
        val start = now - windowMillis
        return points.filter { point -> point.timestampMillis >= start }
    }

    fun clear() {
        points.clear()
    }

    private fun trim(now: Long = nowMillis()) {
        val start = now - windowMillis
        while (points.isNotEmpty() && points.first().timestampMillis < start) {
            points.removeFirst()
        }
    }

    companion object {
        const val DEFAULT_WINDOW_MILLIS = 5 * 60 * 1000L
        const val MIN_APPEND_INTERVAL_MILLIS = 2_500L
    }
}

enum class AppendResult {
    Appended,
    SkippedNoPrice,
    SkippedStaleOrCache,
    SkippedInvalidPrice,
    SkippedTooSoon,
}
