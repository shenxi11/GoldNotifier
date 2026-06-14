package com.example.goldnotifier.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.example.goldnotifier.R
import com.example.goldnotifier.domain.model.GoldPrice
import com.example.goldnotifier.domain.model.MarketTrend
import com.example.goldnotifier.domain.model.displayChange
import com.example.goldnotifier.domain.model.displayPrice
import com.example.goldnotifier.domain.model.notificationStatusText
import com.example.goldnotifier.domain.model.trend

/*
模块名: GoldNotificationManager
功能概述: 创建通知渠道并构建常驻金价通知内容。
对外接口: createChannel、buildGoldNotification
依赖关系: Android Notification、GoldPrice 格式化扩展
输入输出: 输入最新或缓存金价，输出前台服务可使用的 Notification。
异常与错误: price 为空时展示同步中状态，避免前台服务启动超时。
维护说明: 通知 onlyAlertOnce，避免每次刷新都震动或发声。
*/
class GoldNotificationManager(
    private val context: Context,
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createChannel() {
        val channel = NotificationChannel(
            GOLD_CHANNEL_ID,
            "黄金价格",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "持续显示现货黄金 XAU 行情"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun buildGoldNotification(
        price: GoldPrice?,
        message: String? = null,
    ): Notification {
        val expandedView = buildRemoteViews(price = price, message = message, expanded = true)
        val title = price?.let { "${it.name} (${it.symbol})" } ?: "黄金价格同步中"
        val content = price?.let { "${it.displayPrice()} ${it.unit} · ${it.displayChange()}" }
            ?: message?.takeIf { it.isNotBlank() } ?: "正在获取最新行情"

        return NotificationCompat.Builder(context, GOLD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_gold)
            .setContentTitle(title)
            .setContentText(content)
            .setCustomContentView(expandedView)
            .setCustomBigContentView(expandedView)
            .setCustomHeadsUpContentView(expandedView)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setColor(0xFFFFFFFF.toInt())
            .setColorized(true)
            .build()
    }

    companion object {
        const val GOLD_CHANNEL_ID = "gold_price_status"
        const val GOLD_NOTIFICATION_ID = 1001

        private val DARK_TEXT_COLOR: Int = 0xFF111827.toInt()
        private val SUB_TEXT_COLOR: Int = 0xFF6B7280.toInt()
        private val TREND_UP_COLOR: Int = 0xFFC62828.toInt()
        private val TREND_DOWN_COLOR: Int = 0xFF0D9B4B.toInt()
    }

    private fun buildRemoteViews(
        price: GoldPrice?,
        message: String?,
        expanded: Boolean,
    ): RemoteViews {
        val layoutId = if (expanded) {
            R.layout.notification_gold_price_expanded
        } else {
            R.layout.notification_gold_price_compact
        }
        val views = RemoteViews(context.packageName, layoutId)
        val title = price?.let { "${it.name} (${it.symbol})" } ?: "黄金价格同步中"
        val priceText = price?.displayPrice() ?: "--"
        val unitText = price?.unit.orEmpty()
        val changeText = buildChangeChipText(price)
        val updateTimeText = price?.updateTime?.takeIf { it.isNotBlank() } ?: "--"
        val statusText = price.notificationStatusText(message)
        val chipStyle = resolveChipStyle(price)

        views.setTextViewText(R.id.notification_title, title)
        views.setTextViewText(R.id.notification_price_value, priceText)
        views.setTextViewText(R.id.notification_unit_value, unitText)
        views.setTextViewText(R.id.notification_change_chip, changeText)
        views.setTextViewText(R.id.notification_update_time, updateTimeText)
        views.setTextViewText(R.id.notification_status_text, statusText)
        views.setTextColor(R.id.notification_price_value, DARK_TEXT_COLOR)
        views.setTextColor(R.id.notification_unit_value, SUB_TEXT_COLOR)
        views.setTextColor(R.id.notification_update_time, SUB_TEXT_COLOR)
        views.setTextColor(R.id.notification_status_text, SUB_TEXT_COLOR)
        views.setTextColor(R.id.notification_title, DARK_TEXT_COLOR)
        views.setTextColor(R.id.notification_change_chip, chipStyle.textColor)
        views.setInt(R.id.notification_change_chip, "setBackgroundResource", chipStyle.backgroundRes)

        return views
    }

    private fun buildChangeChipText(price: GoldPrice?): String {
        if (price == null) {
            return "正在获取最新行情"
        }
        val baseText = price.displayChange()
        return when (price.trend) {
            MarketTrend.Up -> "↗ $baseText"
            MarketTrend.Down -> "↘ $baseText"
            MarketTrend.Flat -> "→ $baseText"
        }
    }

    private fun resolveChipStyle(price: GoldPrice?): ChipStyle {
        if (price == null) {
            return ChipStyle(
                backgroundRes = R.drawable.notification_change_flat_background,
                textColor = SUB_TEXT_COLOR,
            )
        }
        return when (price.trend) {
            MarketTrend.Up -> ChipStyle(
                backgroundRes = R.drawable.notification_change_up_background,
                textColor = TREND_UP_COLOR,
            )
            MarketTrend.Down -> ChipStyle(
                backgroundRes = R.drawable.notification_change_down_background,
                textColor = TREND_DOWN_COLOR,
            )
            MarketTrend.Flat -> ChipStyle(
                backgroundRes = R.drawable.notification_change_flat_background,
                textColor = SUB_TEXT_COLOR,
            )
        }
    }

    private data class ChipStyle(
        val backgroundRes: Int,
        val textColor: Int,
    )
}
