package com.example.goldnotifier.domain.model

import java.util.Locale

/*
模块名: GoldPriceFormatter
功能概述: 提供金价、涨跌幅、时间和通知文本的统一格式化规则。
对外接口: STALE_QUOTE_MESSAGE、isQuoteStale、displayPrice、displayChange、shortUpdateTime、fullQuoteText
依赖关系: GoldPrice
输入输出: 输入 GoldPrice，输出 UI 和通知可直接展示的字符串。
异常与错误: 空时间或异常时间格式回退为原始值，避免展示链路崩溃。
维护说明: 价格统一保留两位小数，涨跌幅显示为百分数数值。
*/
const val STALE_QUOTE_MESSAGE = "行情数据可能延迟"
private const val QUOTE_LOADING_MESSAGE = "正在获取最新行情"

fun GoldPrice.isQuoteStale(): Boolean =
    isStale || source.equals("cache", ignoreCase = true)

fun GoldPrice?.notificationStatusText(message: String? = null): String {
    if (this == null) {
        return message?.takeIf { it.isNotBlank() } ?: QUOTE_LOADING_MESSAGE
    }
    if (isQuoteStale()) {
        return STALE_QUOTE_MESSAGE
    }
    return ""
}

fun GoldPrice.displayPrice(): String = String.format(Locale.CHINA, "%.2f", price)

fun GoldPrice.displayChange(): String {
    val sign = if (change > 0.0) "+" else ""
    return String.format(Locale.CHINA, "%s%.2f (%s%.2f%%)", sign, change, sign, changePercent)
}

fun GoldPrice.shortUpdateTime(): String {
    if (updateTime.length >= 19) {
        return updateTime.substring(11, 19)
    }
    return updateTime
}

fun GoldPrice.fullQuoteText(): String = buildString {
    append(name).append(' ').append(symbol).append("  ")
    append(displayPrice()).append(' ').append(unit).append('\n')
    append(displayChange()).append("  ").append(updateTime).append('\n')
    append("今开 ").append(openPrice.displayNumber()).append("  ")
    append("昨收 ").append(previousClose.displayNumber()).append('\n')
    append("最高 ").append(high.displayNumber()).append("  ")
    append("最低 ").append(low.displayNumber())
    if (isQuoteStale()) {
        append('\n').append(STALE_QUOTE_MESSAGE)
    }
}

fun Double.displayNumber(): String = String.format(Locale.CHINA, "%.2f", this)
