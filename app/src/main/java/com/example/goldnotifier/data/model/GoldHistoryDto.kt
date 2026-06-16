package com.example.goldnotifier.data.model

import com.example.goldnotifier.domain.model.GoldTrendPoint
import com.example.goldnotifier.domain.model.GoldCandle

/*
模块名: GoldHistoryDto
功能概述: 定义服务端历史行情与 K 线接口 DTO，并映射为趋势图领域模型。
对外接口: GoldHistoryResponseDto、GoldHistoryPointDto、GoldCandlesResponseDto、GoldCandleBarDto
依赖关系: GoldTrendPoint
输入输出: 输入 /api/v1/gold/history 和 /api/v1/gold/candles data 节点，输出可绘制的趋势点或 K 线。
异常与错误: 缺少时间戳或非法价格时返回 null，由 Repository 过滤。
维护说明: 历史点只用于趋势图，不承载最新行情卡片的 open/high/low 字段。
*/
data class GoldHistoryResponseDto(
    val symbol: String?,
    val date: String?,
    val timezone: String?,
    val count: Int?,
    val points: List<GoldHistoryPointDto>?,
)

data class GoldHistoryPointDto(
    val timestampMillis: Long?,
    val price: Double?,
    val updateTime: String?,
    val serverTime: String?,
    val source: String?,
)

data class GoldCandlesResponseDto(
    val symbol: String?,
    val range: String?,
    val resolution: String?,
    val timezone: String?,
    val count: Int?,
    val bars: List<GoldCandleBarDto>?,
)

data class GoldCandleBarDto(
    val timestampMillis: Long?,
    val open: Double?,
    val high: Double?,
    val low: Double?,
    val close: Double?,
)

fun GoldHistoryPointDto.toDomainOrNull(): GoldTrendPoint? {
    val timestamp = timestampMillis ?: return null
    val validPrice = price?.takeIf { it > 0.0 } ?: return null
    return GoldTrendPoint(
        timestampMillis = timestamp,
        price = validPrice,
        updateTime = updateTime.orEmpty(),
        source = source.orEmpty().ifBlank { "server" },
        isStale = false,
    )
}

fun GoldCandleBarDto.toDomainOrNull(): GoldCandle? {
    val timestamp = timestampMillis ?: return null
    val openPrice = open?.takeIf { it > 0.0 } ?: return null
    val highPrice = high?.takeIf { it > 0.0 } ?: return null
    val lowPrice = low?.takeIf { it > 0.0 } ?: return null
    val closePrice = close?.takeIf { it > 0.0 } ?: return null
    if (highPrice < lowPrice) return null
    return GoldCandle(
        timestampMillis = timestamp,
        open = openPrice,
        high = highPrice,
        low = lowPrice,
        close = closePrice,
    )
}
