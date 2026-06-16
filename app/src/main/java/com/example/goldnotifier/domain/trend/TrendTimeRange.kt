package com.example.goldnotifier.domain.trend

/*
模块名: TrendTimeRange
功能概述: 定义首页趋势图支持的时间尺度和展示标签。
对外接口: TrendTimeRange
依赖关系: 无
输入输出: 输入用户选择，输出查询窗口和 UI 标签。
异常与错误: 无运行时异常，枚举值由调用方显式选择。
维护说明: windowMillis 与服务端历史查询窗口保持一致，新增尺度时同步补充 UI 和测试。
*/
enum class TrendTimeRange(
    val label: String,
    val windowMillis: Long,
    val startLabel: String,
    val serverRange: String,
    val candleBucketMillis: Long,
) {
    FiveMinutes(
        label = "5 分钟",
        windowMillis = 5 * 60 * 1000L,
        startLabel = "5 分钟前",
        serverRange = "5m",
        candleBucketMillis = 15 * 1000L,
    ),
    OneHour(
        label = "1 小时",
        windowMillis = 60 * 60 * 1000L,
        startLabel = "1 小时前",
        serverRange = "1h",
        candleBucketMillis = 60 * 1000L,
    ),
    SixHours(
        label = "6 小时",
        windowMillis = 6 * 60 * 60 * 1000L,
        startLabel = "6 小时前",
        serverRange = "6h",
        candleBucketMillis = 5 * 60 * 1000L,
    ),
    OneDay(
        label = "1 天",
        windowMillis = 24 * 60 * 60 * 1000L,
        startLabel = "24 小时前",
        serverRange = "1d",
        candleBucketMillis = 15 * 60 * 1000L,
    );

    companion object {
        val supported: List<TrendTimeRange> = entries
    }
}
