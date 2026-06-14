package com.example.goldnotifier.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.goldnotifier.data.local.UserSettingsDataStore
import com.example.goldnotifier.domain.model.GoldPrice
import com.example.goldnotifier.ui.component.GoldPriceCard
import com.example.goldnotifier.ui.theme.GoldNotifierTheme

/*
模块名: HomeScreen
功能概述: 组合首页行情卡片、通知开关、刷新频率和手动刷新入口。
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
    onRefresh: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        HeaderSection()
        GoldPriceCard(price = uiState.price)
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
private fun HeaderSection() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "黄金价格",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "现货黄金 XAU · 元/克",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            !hasNotificationPermission -> "需要通知权限"
                            !hasPanelAccessibilityPermission -> "需要无障碍权限"
                            else -> "下拉通知栏时刷新"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = uiState.notificationEnabled,
                    onCheckedChange = onNotificationEnabledChange,
                )
            }

            HorizontalDivider()

            if (!hasPanelAccessibilityPermission) {
                Button(
                    onClick = onOpenAccessibilitySettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "开启无障碍权限")
                }
            }

            Text(
                text = "刷新频率",
                style = MaterialTheme.typography.titleSmall,
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
                    )
                }
            }

            Button(
                onClick = onRefresh,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth(),
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        uiState.errorMessage?.let { message ->
            AssistChip(
                onClick = {},
                label = { Text(text = message) },
            )
        }
        if (!hasNotificationPermission) {
            Text(
                text = "未授权通知权限时，状态栏显示无法开启。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!hasPanelAccessibilityPermission) {
            Text(
                text = "未开启无障碍权限时，通知栏不会在后台自动请求行情。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = "行情数据仅供参考，不构成投资建议。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun Int.toDisplayLabel(): String = when (this) {
    3 -> "3 秒"
    else -> "${this} 秒"
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
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
            ),
            hasNotificationPermission = true,
            hasPanelAccessibilityPermission = true,
            onNotificationEnabledChange = {},
            onRefreshIntervalChange = {},
            onRefresh = {},
            onOpenAccessibilitySettings = {},
        )
    }
}
