package com.example.goldnotifier.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.goldnotifier.domain.model.GoldTrendPoint
import com.example.goldnotifier.domain.trend.TrendTimeRange
import com.example.goldnotifier.ui.theme.GoldNotifierTheme
import java.util.Locale
import kotlin.math.abs

/*
模块名: GoldRealtimeTrendCard
功能概述: 展示客户端金价趋势摘要、时间尺度选择和 Canvas 折线图。
对外接口: GoldRealtimeTrendCard
依赖关系: Compose Material3、GoldRealtimeTrendChart、GoldTrendPoint、TrendTimeRange
输入输出: 输入 ViewModel 维护的趋势点和尺度状态，输出首页趋势卡片。
异常与错误: 点数不足时展示采集中状态，错误提示由 ViewModel 传入。
维护说明: 卡片只负责展示和尺度选择，历史请求由 ViewModel/Repository 处理。
*/
@Composable
fun GoldRealtimeTrendCard(
    points: List<GoldTrendPoint>,
    pointCount: Int,
    selectedRange: TrendTimeRange,
    unit: String,
    message: String?,
    isLoading: Boolean,
    onRangeSelected: (TrendTimeRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = remember(points) { points.toTrendSummary() }
    val trendColor = summary?.delta.trendColor()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MarketPanel,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrendRangeSelector(
                    selectedRange = selectedRange,
                    onRangeSelected = onRangeSelected,
                    modifier = Modifier.weight(1f),
                )
                TrendHeader(
                    summary = summary,
                    trendColor = trendColor,
                    unit = unit,
                    pointCount = pointCount,
                    selectedRange = selectedRange,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .widthIn(min = 96.dp, max = 132.dp),
                )
            }

            if (points.size < MIN_CHART_POINTS) {
                EmptyTrendContent(isLoading = isLoading)
            } else {
                GoldRealtimeTrendChart(
                    points = points,
                    lineColor = trendColor,
                )
                TrendFooter(
                    pointCount = pointCount,
                    selectedRange = selectedRange,
                )
            }

            message?.takeIf { it.isNotBlank() }?.let { text ->
                Text(
                    text = text,
                    color = MarketTextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun TrendHeader(
    summary: TrendSummary?,
    trendColor: Color,
    unit: String,
    pointCount: Int,
    selectedRange: TrendTimeRange,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "实时趋势",
            style = MaterialTheme.typography.labelSmall,
            color = MarketTextSecondary,
        )
        Text(
            text = summary?.deltaText() ?: "--",
            style = MaterialTheme.typography.labelLarge,
            color = trendColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "近 ${selectedRange.label} · $pointCount 点 · $unit",
            style = MaterialTheme.typography.labelSmall,
            color = MarketTextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TrendRangeSelector(
    selectedRange: TrendTimeRange,
    onRangeSelected: (TrendTimeRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MarketControl)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TrendTimeRange.supported.forEach { range ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selectedRange == range) MarketSelected else Color.Transparent)
                    .clickable { onRangeSelected(range) }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = range.compactLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selectedRange == range) MarketTextPrimary else MarketTextSecondary,
                    fontWeight = if (selectedRange == range) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun TrendTimeRange.compactLabel(): String = when (this) {
    TrendTimeRange.FiveMinutes -> "5分钟"
    TrendTimeRange.OneHour -> "1小时"
    TrendTimeRange.SixHours -> "6小时"
    TrendTimeRange.OneDay -> "1天"
}

@Composable
private fun EmptyTrendContent(isLoading: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (isLoading) "趋势加载中" else "趋势点采集中",
                style = MaterialTheme.typography.titleSmall,
                color = MarketTextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "至少 2 个有效刷新点后显示折线",
                style = MaterialTheme.typography.bodySmall,
                color = MarketTextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun TrendFooter(
    pointCount: Int,
    selectedRange: TrendTimeRange,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = selectedRange.startLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MarketTextMuted,
        )
        Text(
            text = "$pointCount 点 · 现在",
            style = MaterialTheme.typography.labelSmall,
            color = MarketTextMuted,
        )
    }
}

@Composable
private fun Double?.trendColor(): Color {
    val delta = this ?: 0.0
    return when {
        delta > 0.0 -> MarketRed
        delta < 0.0 -> MarketGreen
        else -> MarketTextSecondary
    }
}

private fun List<GoldTrendPoint>.toTrendSummary(): TrendSummary? {
    val validPoints = filter { it.price > 0.0 }.sortedBy { it.timestampMillis }
    if (validPoints.size < MIN_CHART_POINTS) return null
    val first = validPoints.first()
    val last = validPoints.last()
    val delta = last.price - first.price
    val deltaPercent = if (first.price == 0.0) 0.0 else delta / first.price * 100.0
    return TrendSummary(
        delta = delta,
        deltaPercent = deltaPercent,
    )
}

private fun TrendSummary.deltaText(): String {
    val sign = when {
        delta > 0.0 -> "+"
        delta < 0.0 -> ""
        else -> ""
    }
    return if (abs(deltaPercent) >= 0.01) {
        String.format(Locale.CHINA, "%s%.2f (%s%.2f%%)", sign, delta, sign, deltaPercent)
    } else {
        String.format(Locale.CHINA, "%s%.2f", sign, delta)
    }
}

private data class TrendSummary(
    val delta: Double,
    val deltaPercent: Double,
)

private const val MIN_CHART_POINTS = 2

private val MarketPanel = Color(0xFF111827)
private val MarketControl = Color(0xFF1F2937)
private val MarketSelected = Color(0xFF374151)
private val MarketTextPrimary = Color(0xFFF9FAFB)
private val MarketTextSecondary = Color(0xFF9CA3AF)
private val MarketTextMuted = Color(0xFF6B7280)
private val MarketGreen = Color(0xFF10B981)
private val MarketRed = Color(0xFFEF4444)

@Preview(showBackground = true)
@Composable
private fun GoldRealtimeTrendCardPreview() {
    val baseTime = 1_797_000_000_000L
    GoldNotifierTheme(dynamicColor = false) {
        GoldRealtimeTrendCard(
            points = listOf(
                GoldTrendPoint(baseTime, 883.20, "2026-06-15 17:00:00", "finnhub"),
                GoldTrendPoint(baseTime + 3_000L, 883.64, "2026-06-15 17:00:03", "finnhub"),
                GoldTrendPoint(baseTime + 6_000L, 883.38, "2026-06-15 17:00:06", "finnhub"),
                GoldTrendPoint(baseTime + 9_000L, 884.02, "2026-06-15 17:00:09", "finnhub"),
                GoldTrendPoint(baseTime + 12_000L, 884.18, "2026-06-15 17:00:12", "finnhub"),
            ),
            pointCount = 5,
            selectedRange = TrendTimeRange.FiveMinutes,
            unit = "元/克",
            message = null,
            isLoading = false,
            onRangeSelected = {},
        )
    }
}
