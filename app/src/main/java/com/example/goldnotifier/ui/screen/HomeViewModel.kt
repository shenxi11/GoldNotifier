package com.example.goldnotifier.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.goldnotifier.data.local.UserSettingsDataStore
import com.example.goldnotifier.data.repository.GoldRepository
import com.example.goldnotifier.domain.model.GoldPrice
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/*
模块名: HomeViewModel
功能概述: 管理首页金价、刷新状态、通知开关和刷新频率。
对外接口: uiState、refresh、setNotificationEnabled、setRefreshIntervalSeconds
依赖关系: GoldRepository、ViewModelScope
输入输出: 输入用户意图和 Repository 数据流，输出 HomeUiState。
异常与错误: 刷新失败时保留已有缓存并显示错误消息。
维护说明: UI 只提交意图，不直接访问 Repository 或 Android 服务。
*/
class HomeViewModel(
    private val repository: GoldRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()
    private val refreshUiMutex = Mutex()

    init {
        observeRepositoryState()
    }

    fun refresh() {
        viewModelScope.launch {
            refreshOnce(showLoading = true, force = true)
        }
    }

    fun setNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setNotificationEnabled(enabled)
        }
    }

    fun setRefreshIntervalSeconds(seconds: Int) {
        viewModelScope.launch {
            repository.setRefreshIntervalSeconds(seconds)
        }
    }

    private fun observeRepositoryState() {
        viewModelScope.launch {
            repository.cachedGoldPrice.collect { price ->
                _uiState.update { it.copy(price = price ?: it.price) }
            }
        }
        viewModelScope.launch {
            repository.notificationEnabled.collect { enabled ->
                _uiState.update { it.copy(notificationEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            repository.refreshIntervalSeconds.collect { seconds ->
                _uiState.update { it.copy(refreshIntervalSeconds = seconds) }
            }
        }
    }

    suspend fun runForegroundAutoRefreshLoop() {
        while (currentCoroutineContext().isActive) {
            val startedAtNanos = System.nanoTime()
            refreshOnce(showLoading = _uiState.value.price == null, force = false)
            delay(nextRefreshDelayMillis(startedAtNanos))
        }
    }

    private suspend fun refreshOnce(
        showLoading: Boolean,
        force: Boolean,
    ) {
        refreshUiMutex.withLock {
            if (showLoading) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            }
            val result = repository.refreshGoldPrice(force = force)
            _uiState.update { state ->
                state.copy(
                    price = result.price ?: state.price,
                    isLoading = false,
                    errorMessage = result.message,
                )
            }
        }
    }

    private suspend fun nextRefreshDelayMillis(startedAtNanos: Long): Long {
        val intervalMillis = repository.refreshIntervalSeconds.first()
            .coerceAtLeast(UserSettingsDataStore.MIN_REFRESH_INTERVAL_SECONDS) * 1000L
        val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)
        return (intervalMillis - elapsedMillis).coerceAtLeast(0L)
    }

    companion object {
        fun factory(repository: GoldRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(repository) as T
                }
            }
    }
}

data class HomeUiState(
    val price: GoldPrice? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val notificationEnabled: Boolean = false,
    val refreshIntervalSeconds: Int = UserSettingsDataStore.DEFAULT_REFRESH_INTERVAL_SECONDS,
)
