package com.example.goldnotifier.data.model

import com.example.goldnotifier.domain.model.GoldTrendPoint

/*
模块名: GoldHistoryDto
功能概述: 定义服务端历史行情接口 DTO，并映射为趋势图领域点。
对外接口: GoldHistoryResponseDto、GoldHistoryPointDto、GoldHistoryPointDto.toDomainOrNull
依赖关系: GoldTrendPoint
输入输出: 输入 /api/v1/gold/history data 节点，输出可绘制的 GoldTrendPoint。
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
