package com.example.goldnotifier.domain.model

/*
模块名: GoldCandle
功能概述: 定义 TradingView K 线渲染使用的 OHLC 领域模型。
对外接口: GoldCandle
依赖关系: 无
输入输出: 输入服务端 candles 接口数据，输出 UI 图表可消费的时间有序 K 线。
异常与错误: 模型层不执行校验，非法价格由 DTO 映射和 Repository 过滤。
维护说明: timestampMillis 表示 K 线周期起点，需与服务端聚合口径一致。
*/
data class GoldCandle(
    val timestampMillis: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
)
