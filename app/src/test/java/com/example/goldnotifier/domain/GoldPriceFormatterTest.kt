package com.example.goldnotifier.domain

import com.example.goldnotifier.data.model.GoldPriceDto
import com.example.goldnotifier.data.model.toDomain
import com.example.goldnotifier.domain.model.MarketTrend
import com.example.goldnotifier.domain.model.STALE_QUOTE_MESSAGE
import com.example.goldnotifier.domain.model.displayChange
import com.example.goldnotifier.domain.model.displayPrice
import com.example.goldnotifier.domain.model.fullQuoteText
import com.example.goldnotifier.domain.model.isQuoteStale
import com.example.goldnotifier.domain.model.notificationStatusText
import com.example.goldnotifier.domain.model.trend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
模块名: GoldPriceFormatterTest
功能概述: 验证金价 DTO 映射与展示格式，防止接口字段变更影响 UI。
对外接口: JUnit 测试用例
依赖关系: GoldPriceDto、GoldPriceFormatter、JUnit
输入输出: 输入样例 DTO，输出断言结果。
异常与错误: 映射失败或格式不一致时测试失败。
维护说明: 新增行情字段时同步扩展此测试，保持客户端契约可验证。
*/
class GoldPriceFormatterTest {
    @Test
    fun dtoMapsFreshFinnhubQuoteToDomain() {
        val price = GoldPriceDto(
            name = "现货黄金",
            symbol = "XAU",
            price = 891.13,
            change = 0.0,
            changePercent = 0.0,
            unit = "元/克",
            openPrice = 890.77,
            previousClose = 890.77,
            high = 891.20,
            low = 890.70,
            updateTime = "2026-06-11 17:16:11",
            serverTime = "2026-06-11 17:16:11",
            source = "finnhub",
            marketStatus = "trading",
            isStale = false,
        ).toDomain()

        assertEquals("891.13", price.displayPrice())
        assertEquals("0.00 (0.00%)", price.displayChange())
        assertEquals(MarketTrend.Flat, price.trend)
        assertTrue(!price.isQuoteStale())
        assertEquals("", price.notificationStatusText())
    }

    @Test
    fun dtoWithCacheSourceIsTreatedAsStale() {
        val price = GoldPriceDto(
            name = "黄金9999",
            symbol = "XAU",
            price = 896.5,
            change = -19.13,
            changePercent = -2.09,
            unit = "元/克",
            openPrice = 915.0,
            previousClose = 915.63,
            high = 915.0,
            low = 883.81,
            updateTime = "2026-06-11 15:27:32",
            serverTime = "2026-06-11 16:06:22",
            source = "cache",
            marketStatus = "trading",
            isStale = false,
        ).toDomain()

        assertEquals("黄金9999", price.name)
        assertEquals("896.50", price.displayPrice())
        assertEquals("-19.13 (-2.09%)", price.displayChange())
        assertEquals(915.0, price.openPrice, 0.0)
        assertEquals(915.63, price.previousClose, 0.0)
        assertEquals("cache", price.source)
        assertTrue(price.isQuoteStale())
        assertTrue(price.isStale)
        assertTrue(price.fullQuoteText().contains(STALE_QUOTE_MESSAGE))
        assertEquals(STALE_QUOTE_MESSAGE, price.notificationStatusText())
    }
}
