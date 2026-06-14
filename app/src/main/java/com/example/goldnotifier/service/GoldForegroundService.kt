package com.example.goldnotifier.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.goldnotifier.BuildConfig
import com.example.goldnotifier.GoldNotifierApplication
import com.example.goldnotifier.data.local.UserSettingsDataStore
import com.example.goldnotifier.data.repository.GoldRepository
import com.example.goldnotifier.domain.model.GoldPrice
import com.example.goldnotifier.notification.GoldNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/*
模块名: GoldForegroundService
功能概述: 运行金价前台服务，根据应用前台状态和通知栏展开状态更新常驻通知。
对外接口: createStartIntent、createStopIntent
依赖关系: GoldRepository、GoldNotificationManager、NotificationPanelState、AppVisibilityState
输入输出: 输入本地缓存、远端行情和系统状态，输出持续显示的金价通知。
异常与错误: 网络失败时保留缓存通知并显示延迟提示。
维护说明: 后台不做 3 秒轮询；只有通知栏展开时才按 3 秒主动刷新。
*/
class GoldForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: GoldRepository
    private lateinit var notificationManager: GoldNotificationManager
    private var refreshJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        repository = (application as GoldNotifierApplication).appContainer.goldRepository
        notificationManager = GoldNotificationManager(this).also { it.createChannel() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            serviceScope.launch {
                repository.setNotificationEnabled(false)
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(
            GoldNotificationManager.GOLD_NOTIFICATION_ID,
            notificationManager.buildGoldNotification(price = null),
        )
        refreshJob?.cancel()
        refreshJob = serviceScope.launch {
            repository.setNotificationEnabled(true)
            showLatestCachedNotification()
            runAdaptiveRefreshLoop()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        refreshJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun runAdaptiveRefreshLoop() {
        combine(
            AppVisibilityState.isForeground,
            NotificationPanelState.isExpanded,
        ) { appForeground, panelExpanded ->
            when {
                panelExpanded -> RefreshMode.PanelExpanded
                appForeground -> RefreshMode.AppForeground
                else -> RefreshMode.Background
            }
        }.collectLatest { mode ->
            logDebug("refreshMode=$mode")
            when (mode) {
                RefreshMode.PanelExpanded -> runRefreshLoop(
                    intervalSeconds = UserSettingsDataStore.MIN_REFRESH_INTERVAL_SECONDS,
                )
                RefreshMode.AppForeground -> runCachedNotificationLoop()
                RefreshMode.Background -> runRefreshLoop(
                    intervalSeconds = BACKGROUND_REFRESH_INTERVAL_SECONDS,
                )
            }
        }
    }

    private suspend fun runCachedNotificationLoop() {
        repository.cachedGoldPrice.collectLatest { price ->
            if (price != null) {
                showNotification(price = price, message = null)
            }
        }
    }

    private suspend fun runRefreshLoop(intervalSeconds: Int) {
        val sanitizedIntervalSeconds = intervalSeconds
            .coerceAtLeast(UserSettingsDataStore.MIN_REFRESH_INTERVAL_SECONDS)
        while (currentCoroutineContext().isActive) {
            val startedAtNanos = System.nanoTime()
            logDebug("refreshTick intervalSeconds=$sanitizedIntervalSeconds")
            val result = repository.refreshGoldPrice()
            val price = result.price ?: repository.latestCachedGoldPrice()
            showNotification(price = price, message = result.message)
            delay(nextRefreshDelayMillis(startedAtNanos, sanitizedIntervalSeconds))
        }
    }

    private suspend fun nextRefreshDelayMillis(
        startedAtNanos: Long,
        intervalSeconds: Int,
    ): Long {
        val intervalMillis = intervalSeconds
            .coerceAtLeast(UserSettingsDataStore.MIN_REFRESH_INTERVAL_SECONDS) * 1000L
        val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)
        return (intervalMillis - elapsedMillis).coerceAtLeast(0L)
    }

    private suspend fun showLatestCachedNotification() {
        repository.latestCachedGoldPrice()?.let { cached ->
            showNotification(price = cached, message = null)
        }
    }

    private fun showNotification(
        price: GoldPrice?,
        message: String?,
    ) {
        notificationManagerCompat().notify(
            GoldNotificationManager.GOLD_NOTIFICATION_ID,
            notificationManager.buildGoldNotification(
                price = price,
                message = message,
            ),
        )
    }

    private fun notificationManagerCompat() =
        getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }

    private enum class RefreshMode {
        PanelExpanded,
        AppForeground,
        Background,
    }

    companion object {
        private const val ACTION_START = "com.example.goldnotifier.action.START_GOLD_SERVICE"
        private const val ACTION_STOP = "com.example.goldnotifier.action.STOP_GOLD_SERVICE"
        private const val BACKGROUND_REFRESH_INTERVAL_SECONDS = 300
        private const val TAG = "GoldForegroundService"

        fun createStartIntent(context: Context): Intent =
            Intent(context, GoldForegroundService::class.java).setAction(ACTION_START)

        fun createStopIntent(context: Context): Intent =
            Intent(context, GoldForegroundService::class.java).setAction(ACTION_STOP)
    }
}

/*
模块名: AppVisibilityState
功能概述: 保存应用前后台状态，供前台服务选择通知刷新频率。
对外接口: isForeground、updateForeground
依赖关系: Kotlin Coroutines StateFlow
输入输出: 输入 Activity 生命周期事件，输出应用是否处于前台。
异常与错误: 状态默认为后台，避免无 Activity 时误判为前台。
维护说明: 只保存进程内轻量状态，不持有 Context 或 Activity 引用。
*/
object AppVisibilityState {
    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    fun updateForeground(isForeground: Boolean) {
        _isForeground.value = isForeground
    }
}
