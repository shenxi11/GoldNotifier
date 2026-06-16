package com.example.goldnotifier.domain.trend

/*
模块名: TrendChartMode
功能概述: 定义首页行情图表在折线与 K 线之间的展示模式。
对外接口: TrendChartMode
依赖关系: 无
输入输出: 输入用户切换意图，输出趋势卡片选择对应图表组件。
异常与错误: 无运行时异常，模式集合固定。
维护说明: 折线为默认模式，K 线依赖服务端 candles 接口和 WebView 渲染能力。
*/
enum class TrendChartMode(
    val label: String,
) {
    Line(label = "折线"),
    Candle(label = "K线"),
}
