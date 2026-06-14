package com.example.goldnotifier.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.goldnotifier.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
模块名: NotificationPanelAccessibilityService
功能概述: 通过无障碍事件估算系统通知面板是否展开，并发布给前台服务控制刷新。
对外接口: NotificationPanelAccessibilityService、NotificationPanelState
依赖关系: Android AccessibilityService、SystemUI、StateFlow
输入输出: 输入 SystemUI 窗口变化事件，输出通知面板展开状态。
异常与错误: 无法稳定识别时保持未展开状态，避免后台误请求。
维护说明: 不读取用户通知内容，只依据窗口包名、类名和尺寸判断面板状态。
*/
class NotificationPanelAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        NotificationPanelState.updateAccessibilityConnected(true)
        NotificationPanelState.updateExpanded(false)
        logDebug("connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !event.isSystemUiEvent()) {
            return
        }
        val expanded = isNotificationPanelExpanded(event)
        NotificationPanelState.updateExpanded(expanded)
        logDebug(
            "expanded=$expanded type=${event.eventType} class=${event.className} " +
                "root=${rootInActiveWindow?.className} windows=${windows.size}",
        )
    }

    override fun onInterrupt() {
        NotificationPanelState.updateExpanded(false)
        logDebug("interrupted")
    }

    override fun onDestroy() {
        NotificationPanelState.updateExpanded(false)
        NotificationPanelState.updateAccessibilityConnected(false)
        logDebug("destroyed")
        super.onDestroy()
    }

    private fun AccessibilityEvent.isSystemUiEvent(): Boolean {
        if (packageName?.toString() == SYSTEM_UI_PACKAGE) {
            return true
        }
        if (rootInActiveWindow?.packageName?.toString() == SYSTEM_UI_PACKAGE) {
            return true
        }
        return windows.any { window ->
            window.root?.packageName?.toString() == SYSTEM_UI_PACKAGE
        }
    }

    private fun isNotificationPanelExpanded(event: AccessibilityEvent): Boolean {
        val eventClassName = event.className?.toString().orEmpty()
        val root = rootInActiveWindow
        val rootClassName = root?.className?.toString().orEmpty()
        val screenHeight = resources.displayMetrics.heightPixels
        val screenWidth = resources.displayMetrics.widthPixels

        if (isCollapsedSystemSurface(eventClassName) && !hasLargeSystemUiWindow(screenWidth, screenHeight)) {
            return false
        }

        val classSignal = PANEL_CLASS_SIGNALS.any { signal ->
            eventClassName.contains(signal, ignoreCase = true) ||
                rootClassName.contains(signal, ignoreCase = true)
        }
        val rootSizeSignal = root?.hasLargeSystemUiBounds(screenWidth, screenHeight) == true
        return classSignal || rootSizeSignal || hasLargeSystemUiWindow(screenWidth, screenHeight)
    }

    private fun hasLargeSystemUiWindow(screenWidth: Int, screenHeight: Int): Boolean {
        return windows.any { window ->
            val root = window.root ?: return@any false
            root.packageName?.toString() == SYSTEM_UI_PACKAGE &&
                !isCollapsedSystemSurface(root.className?.toString().orEmpty()) &&
                root.hasLargeSystemUiBounds(screenWidth, screenHeight)
        }
    }

    private fun AccessibilityNodeInfo.hasLargeSystemUiBounds(
        screenWidth: Int,
        screenHeight: Int,
    ): Boolean {
        val bounds = screenBoundsOrNull() ?: return false
        return bounds.width() >= (screenWidth * MIN_PANEL_WIDTH_RATIO).toInt() &&
            bounds.height() >= (screenHeight * MIN_PANEL_HEIGHT_RATIO).toInt()
    }

    private fun AccessibilityNodeInfo.screenBoundsOrNull(): Rect? {
        val rect = Rect()
        getBoundsInScreen(rect)
        return rect.takeIf { !it.isEmpty }
    }

    private fun isCollapsedSystemSurface(className: String): Boolean {
        return COLLAPSED_CLASS_SIGNALS.any { signal ->
            className.contains(signal, ignoreCase = true)
        }
    }

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }

    companion object {
        private const val TAG = "GoldPanelAccess"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val MIN_PANEL_WIDTH_RATIO = 0.8f
        private const val MIN_PANEL_HEIGHT_RATIO = 0.35f

        private val PANEL_CLASS_SIGNALS = listOf(
            "NotificationShade",
            "NotificationPanel",
            "NotificationStack",
            "QSPanel",
            "QuickSettings",
        )

        private val COLLAPSED_CLASS_SIGNALS = listOf(
            "NavigationBar",
            "VolumeDialog",
            "Toast",
            "KeyboardShortcuts",
        )
    }
}

object NotificationPanelState {
    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded.asStateFlow()

    private val _isAccessibilityConnected = MutableStateFlow(false)
    val isAccessibilityConnected: StateFlow<Boolean> = _isAccessibilityConnected.asStateFlow()

    fun updateExpanded(expanded: Boolean) {
        _isExpanded.value = expanded
    }

    fun updateAccessibilityConnected(connected: Boolean) {
        _isAccessibilityConnected.value = connected
        if (!connected) {
            _isExpanded.value = false
        }
    }
}
