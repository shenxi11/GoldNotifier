package com.example.goldnotifier.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.goldnotifier.data.local.UserSettingsDataStore
import com.example.goldnotifier.domain.model.GoldPrice
import com.example.goldnotifier.domain.model.GoldTrendPoint
import com.example.goldnotifier.domain.trend.TrendChartMode
import com.example.goldnotifier.domain.trend.TrendTimeRange
import com.example.goldnotifier.ui.component.GoldPriceCard
import com.example.goldnotifier.ui.component.GoldRealtimeTrendCard
import com.example.goldnotifier.ui.theme.GoldNotifierTheme

/*
模块名: HomeScreen
功能概述: 组合深色行情首页、趋势图区、通知开关、刷新频率和手动刷新入口。
对外接口: HomeScreen
依赖关系: Compose Material3、HomeUiState、GoldPriceCard
输入输出: 输入 HomeUiState 与用户回调，输出首页 MVP UI。
异常与错误: errorMessage 以状态文本展示，不阻断缓存行情显示。
维护说明: 屏幕保持无导航依赖，便于后续加入 SettingsScreen。
*/
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    hasNotificationPermission: Boolean,
    hasPanelAccessibilityPermission: Boolean,
    onNotificationEnabledChange: (Boolean) -> Unit,
    onRefreshIntervalChange: (Int) -> Unit,
    onTrendTimeRangeChange: (TrendTimeRange) -> Unit,
    onTrendChartModeChange: (TrendChartMode) -> Unit,
    onRefresh: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MarketBlack)
            .verticalScroll(scrollState)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        HeaderSection(price = uiState.price)
        GoldPriceCard(
            price = uiState.price,
            modifier = Modifier.fillMaxWidth(),
        )
        GoldRealtimeTrendCard(
            points = uiState.trendPoints,
            pointCount = uiState.trendPointCount,
            candles = uiState.trendCandles,
            selectedRange = uiState.selectedTrendRange,
            chartMode = uiState.chartMode,
            unit = uiState.price?.unit ?: "元/克",
            message = if (uiState.chartMode == TrendChartMode.Candle) {
                uiState.candleMessage
            } else {
                uiState.trendMessage
            },
            isLoading = if (uiState.chartMode == TrendChartMode.Candle) {
                uiState.isCandleLoading
            } else {
                uiState.isTrendLoading
            },
            onRangeSelected = onTrendTimeRangeChange,
            onChartModeSelected = onTrendChartModeChange,
            modifier = Modifier.fillMaxWidth(),
        )
        ControlPanel(
            uiState = uiState,
            hasNotificationPermission = hasNotificationPermission,
            hasPanelAccessibilityPermission = hasPanelAccessibilityPermission,
            onNotificationEnabledChange = onNotificationEnabledChange,
            onRefreshIntervalChange = onRefreshIntervalChange,
            onRefresh = onRefresh,
            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
        )
        StatusSection(
            uiState = uiState,
            hasNotificationPermission = hasNotificationPermission,
            hasPanelAccessibilityPermission = hasPanelAccessibilityPermission,
        )
    }
}

@Composable
private fun HeaderSection(price: GoldPrice?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MarketPanel)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "现货黄金 ${price?.symbol ?: "XAU"}",
                style = MaterialTheme.typography.titleLarge,
                color = MarketTextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${price?.source?.ifBlank { "server" } ?: "server"} · ${price?.unit ?: "元/克"}",
                style = MaterialTheme.typography.labelMedium,
                color = MarketTextSecondary,
            )
        }
        Text(
            text = price?.marketStatus?.ifBlank { "trading" } ?: "等待行情",
            style = MaterialTheme.typography.labelMedium,
            color = MarketTextMuted,
        )
    }
}

@Composable
private fun ControlPanel(
    uiState: HomeUiState,
    hasNotificationPermission: Boolean,
    hasPanelAccessibilityPermission: Boolean,
    onNotificationEnabledChange: (Boolean) -> Unit,
    onRefreshIntervalChange: (Int) -> Unit,
    onRefresh: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MarketPanel,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "状态栏显示",
                        style = MaterialTheme.typography.titleMedium,
                        color = MarketTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            !hasNotificationPermission -> "需要通知权限"
                            !hasPanelAccessibilityPermission -> "需要无障碍权限"
                            else -> "下拉通知栏时刷新"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MarketTextSecondary,
                    )
                }
                Switch(
                    checked = uiState.notificationEnabled,
                    onCheckedChange = onNotificationEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MarketTextPrimary,
                        checkedTrackColor = MarketAmber,
                        uncheckedThumbColor = MarketTextMuted,
                        uncheckedTrackColor = MarketControl,
                    ),
                )
            }

            HorizontalDivider(color = MarketDivider)

            if (!hasPanelAccessibilityPermission) {
                Button(
                    onClick = onOpenAccessibilitySettings,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MarketControl,
                        contentColor = MarketAmber,
                    ),
                ) {
                    Text(text = "开启无障碍权限")
                }
            }

            Text(
                text = "刷新频率",
                style = MaterialTheme.typography.titleSmall,
                color = MarketTextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UserSettingsDataStore.SUPPORTED_REFRESH_INTERVALS.forEach { seconds ->
                    FilterChip(
                        selected = uiState.refreshIntervalSeconds == seconds,
                        onClick = { onRefreshIntervalChange(seconds) },
                        label = { Text(text = seconds.toDisplayLabel()) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MarketControl,
                            labelColor = MarketTextSecondary,
                            selectedContainerColor = MarketIndigo,
                            selectedLabelColor = MarketTextPrimary,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = uiState.refreshIntervalSeconds == seconds,
                            borderColor = MarketDivider,
                            selectedBorderColor = MarketIndigoBorder,
                        ),
                    )
                }
            }

            Button(
                onClick = onRefresh,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MarketAmber,
                    contentColor = Color.White,
                    disabledContainerColor = MarketAmber.copy(alpha = 0.55f),
                    disabledContentColor = Color.White.copy(alpha = 0.8f),
                ),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                }
                Text(text = if (uiState.isLoading) "正在刷新" else "立即刷新")
            }
        }
    }
}

@Composable
private fun StatusSection(
    uiState: HomeUiState,
    hasNotificationPermission: Boolean,
    hasPanelAccessibilityPermission: Boolean,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        uiState.errorMessage?.let { message ->
            Text(
                text = message,
                color = MarketAmber,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!hasNotificationPermission) {
            Text(
                text = "未授权通知权限时，状态栏显示无法开启。",
                color = MarketTextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!hasPanelAccessibilityPermission) {
            Text(
                text = "未开启无障碍权限时，通知栏不会在后台自动请求行情。",
                color = MarketTextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = "行情数据仅供参考，不构成投资建议。",
            color = MarketTextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun Int.toDisplayLabel(): String = when (this) {
    3 -> "3 秒"
    else -> "${this} 秒"
}

private val MarketBlack = Color(0xFF050505)
private val MarketPanel = Color(0xFF111827)
private val MarketControl = Color(0xFF1F2937)
private val MarketDivider = Color(0xFF374151)
private val MarketAmber = Color(0xFFD97706)
private val MarketIndigo = Color(0x332D5BFF)
private val MarketIndigoBorder = Color(0x664F7DFF)
private val MarketTextPrimary = Color(0xFFF9FAFB)
private val MarketTextSecondary = Color(0xFF9CA3AF)
private val MarketTextMuted = Color(0xFF6B7280)

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    val baseTime = 1_797_000_000_000L
    GoldNotifierTheme(dynamicColor = false) {
        HomeScreen(
            uiState = HomeUiState(
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
                trendPoints = listOf(
                    GoldTrendPoint(baseTime, 885.40, "2026-06-15 17:00:00", "finnhub"),
                    GoldTrendPoint(baseTime + 3_000L, 885.72, "2026-06-15 17:00:03", "finnhub"),
                    GoldTrendPoint(baseTime + 6_000L, 885.58, "2026-06-15 17:00:06", "finnhub"),
                    GoldTrendPoint(baseTime + 9_000L, 886.06, "2026-06-15 17:00:09", "finnhub"),
                ),
                trendPointCount = 4,
            ),
            hasNotificationPermission = true,
            hasPanelAccessibilityPermission = true,
            onNotificationEnabledChange = {},
            onRefreshIntervalChange = {},
            onTrendTimeRangeChange = {},
            onTrendChartModeChange = {},
            onRefresh = {},
            onOpenAccessibilitySettings = {},
        )
    }
}
