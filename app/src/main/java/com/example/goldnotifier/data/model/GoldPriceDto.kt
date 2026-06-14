package com.example.goldnotifier.data.model

import com.example.goldnotifier.domain.model.GoldPrice
import com.google.gson.annotations.SerializedName

/*
模块名: GoldPriceDto
功能概述: 定义服务端金价接口 DTO，并显式映射为客户端领域模型。
对外接口: GoldPriceDto、GoldPriceDto.toDomain
依赖关系: Gson SerializedName、GoldPrice
输入输出: 输入 /api/v1/gold/latest data 节点，输出 GoldPrice。
异常与错误: 必填数值缺失时抛出 IllegalArgumentException，由 Repository 兜底到缓存。
维护说明: JSON 字段 open 和 prevClose 分别映射到 openPrice、previousClose。
*/
data class GoldPriceDto(
    val name: String?,
    val symbol: String?,
    val price: Double?,
    val change: Double?,
    val changePercent: Double?,
    val unit: String?,
    @SerializedName("open")
    val openPrice: Double?,
    @SerializedName("prevClose")
    val previousClose: Double?,
    val high: Double?,
    val low: Double?,
    val updateTime: String?,
    val serverTime: String?,
    val source: String?,
    val marketStatus: String?,
    val isStale: Boolean?,
)

fun GoldPriceDto.toDomain(): GoldPrice = GoldPrice(
    name = name.orEmpty().ifBlank { "现货黄金" },
    symbol = symbol.orEmpty().ifBlank { "XAU" },
    price = requireNotNull(price) { "price is required" },
    change = requireNotNull(change) { "change is required" },
    changePercent = requireNotNull(changePercent) { "changePercent is required" },
    unit = unit.orEmpty().ifBlank { "元/克" },
    openPrice = requireNotNull(openPrice) { "open is required" },
    previousClose = requireNotNull(previousClose) { "prevClose is required" },
    high = requireNotNull(high) { "high is required" },
    low = requireNotNull(low) { "low is required" },
    updateTime = updateTime.orEmpty(),
    serverTime = serverTime.orEmpty(),
    source = source.orEmpty().ifBlank { "server" },
    marketStatus = marketStatus.orEmpty().ifBlank { "unknown" },
    isStale = (isStale ?: false) || source.equals("cache", ignoreCase = true),
)
