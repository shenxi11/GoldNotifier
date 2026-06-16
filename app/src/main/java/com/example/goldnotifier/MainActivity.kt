package com.example.goldnotifier

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.goldnotifier.service.AppVisibilityState
import com.example.goldnotifier.service.GoldForegroundService
import com.example.goldnotifier.service.NotificationPanelAccessibilityService
import com.example.goldnotifier.ui.screen.HomeScreen
import com.example.goldnotifier.ui.screen.HomeViewModel
import com.example.goldnotifier.ui.theme.GoldNotifierTheme

/*
模块名: MainActivity
功能概述: 作为客户端入口，装配 Compose 首页、通知权限和前台服务控制。
对外接口: Android launcher Activity
依赖关系: GoldNotifierApplication、HomeViewModel、GoldForegroundService
输入输出: 输入用户首页操作，输出 UI 状态变化和前台服务启动/停止。
异常与错误: 通知权限未授权时不启动前台服务，避免状态栏显示失败。
维护说明: Activity 只处理 Android 平台能力，业务状态交给 ViewModel。
*/
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as GoldNotifierApplication).appContainer
        setContent {
            GoldNotifierTheme(dynamicColor = false) {
                GoldNotifierApp(
                    repository = container.goldRepository,
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        AppVisibilityState.updateForeground(false)
    }
}

@Composable
private fun GoldNotifierApp(
    repository: com.example.goldnotifier.data.repository.GoldRepository,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(repository),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var hasNotificationPermission by remember {
        mutableStateOf(context.hasNotificationPermission())
    }
    var hasPanelAccessibilityPermission by remember {
        mutableStateOf(context.hasNotificationPanelAccessibilityPermission())
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasNotificationPermission = granted
        if (granted) {
            viewModel.setNotificationEnabled(true)
            context.startGoldForegroundService()
        } else {
            viewModel.setNotificationEnabled(false)
        }
    }
    val accessibilitySettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        val accessibilityGranted = context.hasNotificationPanelAccessibilityPermission()
        hasPanelAccessibilityPermission = accessibilityGranted
        if (accessibilityGranted && uiState.notificationEnabled && context.hasNotificationPermission()) {
            hasNotificationPermission = true
            context.startGoldForegroundService()
        }
    }

    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> AppVisibilityState.updateForeground(true)
                Lifecycle.Event.ON_RESUME -> {
                    hasNotificationPermission = context.hasNotificationPermission()
                    hasPanelAccessibilityPermission = context.hasNotificationPanelAccessibilityPermission()
                }
                Lifecycle.Event.ON_STOP -> {
                    viewModel.saveTrendSnapshot(force = true)
                    AppVisibilityState.updateForeground(false)
                }
                else -> Unit
            }
        }
        AppVisibilityState.updateForeground(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
        )
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.runForegroundAutoRefreshLoop()
        }
    }

    LaunchedEffect(uiState.notificationEnabled, hasNotificationPermission) {
        when {
            uiState.notificationEnabled && hasNotificationPermission -> {
                context.startGoldForegroundService()
            }

            uiState.notificationEnabled && !hasNotificationPermission -> {
                viewModel.setNotificationEnabled(false)
            }
        }
    }

    HomeScreen(
        uiState = uiState,
        hasNotificationPermission = hasNotificationPermission,
        hasPanelAccessibilityPermission = hasPanelAccessibilityPermission,
        onNotificationEnabledChange = { enabled ->
            if (enabled) {
                if (context.hasNotificationPermission()) {
                    hasNotificationPermission = true
                    viewModel.setNotificationEnabled(true)
                    context.startGoldForegroundService()
                } else {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                viewModel.setNotificationEnabled(false)
                context.stopGoldForegroundService()
            }
        },
        onRefreshIntervalChange = viewModel::setRefreshIntervalSeconds,
        onTrendTimeRangeChange = viewModel::selectTrendTimeRange,
        onRefresh = viewModel::refresh,
        onOpenAccessibilitySettings = {
            accessibilitySettingsLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        },
    )
}

private fun Context.hasNotificationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}

private fun Context.hasNotificationPanelAccessibilityPermission(): Boolean {
    val enabledServices = Settings.Secure.getString(
        contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    return enabledServices
        .split(':')
        .mapNotNull(ComponentName::unflattenFromString)
        .any { component ->
            component.packageName == packageName &&
                component.className == NotificationPanelAccessibilityService::class.java.name
        }
}

private fun Context.startGoldForegroundService() {
    ContextCompat.startForegroundService(
        this,
        GoldForegroundService.createStartIntent(this),
    )
}

private fun Context.stopGoldForegroundService() {
    startService(GoldForegroundService.createStopIntent(this))
}
