package com.example.goldnotifier.domain.model

/*
模块名: GoldPrice
功能概述: 定义客户端内部使用的金价领域模型，隔离服务端 DTO 字段命名。
对外接口: GoldPrice、MarketTrend
依赖关系: 无
输入输出: 输入接口或缓存映射数据，输出 UI、通知和缓存共享的稳定模型。
异常与错误: 不在模型层执行校验，异常字段由 Repository 映射时处理。
维护说明: Kotlin 关键字 open 不直接作为属性名，统一使用 openPrice。
*/
data class GoldPrice(
    val name: String,
    val symbol: String,
    val price: Double,
    val change: Double,
    val changePercent: Double,
    val unit: String,
    val openPrice: Double,
    val previousClose: Double,
    val high: Double,
    val low: Double,
    val updateTime: String,
    val serverTime: String,
    val source: String,
    val marketStatus: String,
    val isStale: Boolean,
)

enum class MarketTrend {
    Up,
    Down,
    Flat,
}

val GoldPrice.trend: MarketTrend
    get() = when {
        change > 0.0 -> MarketTrend.Up
        change < 0.0 -> MarketTrend.Down
        else -> MarketTrend.Flat
    }
