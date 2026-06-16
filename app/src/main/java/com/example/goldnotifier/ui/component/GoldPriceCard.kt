package com.example.goldnotifier.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.goldnotifier.domain.model.GoldPrice
import com.example.goldnotifier.domain.model.MarketTrend
import com.example.goldnotifier.domain.model.displayChange
import com.example.goldnotifier.domain.model.displayNumber
import com.example.goldnotifier.domain.model.displayPrice
import com.example.goldnotifier.domain.model.shortUpdateTime
import com.example.goldnotifier.domain.model.isQuoteStale
import com.example.goldnotifier.domain.model.STALE_QUOTE_MESSAGE
import com.example.goldnotifier.domain.model.trend
import com.example.goldnotifier.ui.theme.GoldNotifierTheme

/*
模块名: GoldPriceCard
功能概述: 展示现货黄金核心行情字段，匹配客户端文档中的首页卡片。
对外接口: GoldPriceCard
依赖关系: Compose Material3、GoldPrice 格式化扩展
输入输出: 输入 GoldPrice 或空状态，输出可复用行情卡片 UI。
异常与错误: price 为空时显示等待行情状态，不让首页出现空白。
维护说明: 国内行情配色采用红涨、绿跌、灰色持平或延迟。
*/
@Composable
fun GoldPriceCard(
    price: GoldPrice?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MarketPanel,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        if (price == null) {
            EmptyQuoteContent()
        } else {
            QuoteContent(price = price)
        }
    }
}

@Composable
private fun EmptyQuoteContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "现货黄金 XAU",
            style = MaterialTheme.typography.titleMedium,
            color = MarketTextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "等待行情数据",
            style = MaterialTheme.typography.displaySmall,
            color = MarketTextPrimary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "请检查网络或服务端地址配置",
            color = MarketTextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun QuoteContent(price: GoldPrice) {
    val trendColor = price.trendColor()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = price.displayPrice(),
                    color = trendColor,
                    fontSize = 42.sp,
                    lineHeight = 46.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = price.unit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MarketTextSecondary,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.widthIn(min = 120.dp),
            ) {
                Text(
                    text = price.displayChange(),
                    color = trendColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(trendColor, CircleShape),
                    )
                    Text(
                        text = price.shortUpdateTime(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MarketTextMuted,
                        maxLines = 1,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            QuoteMetric(label = "今开", value = price.openPrice.displayNumber(), modifier = Modifier.weight(1f))
            QuoteMetric(label = "昨收", value = price.previousClose.displayNumber(), modifier = Modifier.weight(1f))
            QuoteMetric(label = "最高", value = price.high.displayNumber(), modifier = Modifier.weight(1f))
            QuoteMetric(label = "最低", value = price.low.displayNumber(), modifier = Modifier.weight(1f))
        }
        if (price.isQuoteStale()) {
            Text(
                text = STALE_QUOTE_MESSAGE,
                color = MarketTextSecondary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun QuoteMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MarketTextSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MarketTextPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GoldPrice.trendColor(): Color {
    if (isQuoteStale()) return MarketTextSecondary
    return when (trend) {
        MarketTrend.Up -> MarketRed
        MarketTrend.Down -> MarketGreen
        MarketTrend.Flat -> MarketTextSecondary
    }
}

private val MarketPanel = Color(0xFF111827)
private val MarketTextPrimary = Color(0xFFF9FAFB)
private val MarketTextSecondary = Color(0xFF9CA3AF)
private val MarketTextMuted = Color(0xFF6B7280)
private val MarketGreen = Color(0xFF10B981)
private val MarketRed = Color(0xFFEF4444)

@Preview(showBackground = true)
@Composable
private fun GoldPriceCardPreview() {
    GoldNotifierTheme(dynamicColor = false) {
        GoldPriceCard(
            price = GoldPrice(
                name = "现货黄金",
                symbol = "XAU",
                price = 885.72,
                change = -1.01,
                changePercent = -0.11,
                unit = "元/克",
                openPrice = 887.28,
                previousClose = 886.73,
                high = 896.99,
                low = 876.21,
                updateTime = "2026-06-11 11:39:23",
                serverTime = "2026-06-11 11:39:25",
                source = "finnhub",
                marketStatus = "trading",
                isStale = false,
            ),
        )
    }
}
