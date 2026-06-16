package com.example.goldnotifier.domain.model

/*
模块名: GoldTrendPoint
功能概述: 定义客户端实时趋势图使用的轻量趋势点模型。
对外接口: GoldTrendPoint、GoldTrendSnapshot
依赖关系: 无
输入输出: 输入最新行情刷新结果，输出 UI 绘图和本地快照可复用的趋势点。
异常与错误: 模型层不做校验，过滤逻辑由 TrendPointBuffer 统一处理。
维护说明: timestampMillis 使用客户端接收行情时间，避免解析服务端时间字符串失败。
*/
data class GoldTrendPoint(
    val timestampMillis: Long,
    val price: Double,
    val updateTime: String,
    val source: String,
    val isStale: Boolean = false,
)

data class GoldTrendSnapshot(
    val savedAtMillis: Long,
    val windowMillis: Long,
    val points: List<GoldTrendPoint>,
)
