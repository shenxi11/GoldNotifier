package com.example.goldnotifier.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.goldnotifier.data.local.UserSettingsDataStore
import com.example.goldnotifier.data.repository.GoldRepository
import com.example.goldnotifier.domain.model.GoldPrice
import com.example.goldnotifier.domain.model.GoldTrendPoint
import com.example.goldnotifier.domain.model.GoldTrendSnapshot
import com.example.goldnotifier.domain.trend.AppendResult
import com.example.goldnotifier.domain.trend.TrendPointSampler
import com.example.goldnotifier.domain.trend.TrendPointBuffer
import com.example.goldnotifier.domain.trend.TrendTimeRange
import kotlinx.coroutines.Job
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
功能概述: 管理首页金价、趋势时间尺度、刷新状态、通知开关和刷新频率。
对外接口: uiState、refresh、selectTrendTimeRange、setNotificationEnabled、setRefreshIntervalSeconds
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
    private val trendBuffer = TrendPointBuffer()
    private var loadedTrendHistoryPoints: List<GoldTrendPoint> = emptyList()
    private var trendHistoryJob: Job? = null

    @Volatile
    private var lastTrendSnapshotSavedAtMillis: Long = 0L

    init {
        restoreTrendSnapshot()
        observeRepositoryState()
        loadTrendHistory(TrendTimeRange.FiveMinutes)
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

    fun selectTrendTimeRange(range: TrendTimeRange) {
        _uiState.update { state ->
            val display = trendDisplay(range)
            state.copy(
                selectedTrendRange = range,
                trendPoints = display.points,
                trendPointCount = display.totalCount,
                trendMessage = null,
            )
        }
        loadTrendHistory(range)
    }

    fun saveTrendSnapshot(force: Boolean = false) {
        viewModelScope.launch {
            saveTrendSnapshotIfNeeded(force = force)
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
            val appendResult = trendBuffer.appendIfValid(
                price = result.price,
                fromCache = result.fromCache,
            )
            appendLatestPointToLoadedHistory(appendResult)
            val display = trendDisplay(_uiState.value.selectedTrendRange)
            _uiState.update { state ->
                state.copy(
                    price = result.price ?: state.price,
                    trendPoints = display.points,
                    trendPointCount = display.totalCount,
                    trendMessage = trendMessageOf(appendResult, result.message),
                    isLoading = false,
                    errorMessage = result.message,
                )
            }
            saveTrendSnapshotIfNeeded(force = false)
        }
    }

    private fun loadTrendHistory(range: TrendTimeRange) {
        trendHistoryJob?.cancel()
        trendHistoryJob = viewModelScope.launch {
            _uiState.update { state ->
                if (state.selectedTrendRange == range) {
                    state.copy(isTrendLoading = true)
                } else {
                    state
                }
            }
            val result = repository.fetchTrendHistory(range = range)
            if (_uiState.value.selectedTrendRange != range) return@launch

            loadedTrendHistoryPoints = result.points
            val display = trendDisplay(range)
            _uiState.update { state ->
                state.copy(
                    trendPoints = display.points,
                    trendPointCount = display.totalCount,
                    trendMessage = result.message,
                    isTrendLoading = false,
                )
            }
        }
    }

    private fun restoreTrendSnapshot() {
        viewModelScope.launch {
            val snapshot = repository.latestTrendSnapshot() ?: return@launch
            val nowMillis = wallClockMillis()
            if (nowMillis - snapshot.savedAtMillis > MAX_RESTORE_AGE_MILLIS) {
                return@launch
            }
            trendBuffer.restore(snapshot.points)
            val display = trendDisplay(_uiState.value.selectedTrendRange)
            lastTrendSnapshotSavedAtMillis = snapshot.savedAtMillis
            _uiState.update { state ->
                state.copy(
                    trendPoints = display.points,
                    trendPointCount = display.totalCount,
                    trendMessage = null,
                )
            }
        }
    }

    private suspend fun saveTrendSnapshotIfNeeded(force: Boolean) {
        val trendPoints = trendBuffer.snapshot()
        if (trendPoints.isEmpty()) return
        val nowMillis = wallClockMillis()
        if (!force && nowMillis - lastTrendSnapshotSavedAtMillis < TREND_SNAPSHOT_SAVE_INTERVAL_MILLIS) {
            return
        }
        repository.cacheTrendSnapshot(
            GoldTrendSnapshot(
                savedAtMillis = nowMillis,
                windowMillis = TrendPointBuffer.DEFAULT_WINDOW_MILLIS,
                points = trendPoints,
            ),
        )
        lastTrendSnapshotSavedAtMillis = nowMillis
    }

    private fun trendMessageOf(
        appendResult: AppendResult,
        repositoryMessage: String?,
    ): String? = when (appendResult) {
        AppendResult.SkippedStaleOrCache -> repositoryMessage ?: "行情数据可能延迟，趋势线暂不追加新点"
        AppendResult.SkippedInvalidPrice -> "行情价格异常，趋势线暂不追加新点"
        AppendResult.SkippedNoPrice -> repositoryMessage ?: "暂无行情数据"
        AppendResult.SkippedTooSoon,
        AppendResult.Appended -> null
    }

    private fun appendLatestPointToLoadedHistory(appendResult: AppendResult) {
        if (appendResult != AppendResult.Appended) return
        val latestPoint = trendBuffer.snapshot().lastOrNull() ?: return
        val selectedRange = _uiState.value.selectedTrendRange
        loadedTrendHistoryPoints = mergeTrendPoints(
            points = loadedTrendHistoryPoints + latestPoint,
            range = selectedRange,
        )
    }

    private fun trendDisplay(range: TrendTimeRange): TrendDisplay {
        val mergedPoints = mergeTrendPoints(
            points = loadedTrendHistoryPoints + trendBuffer.snapshot(range.windowMillis),
            range = range,
        )
        return TrendDisplay(
            points = TrendPointSampler.sample(mergedPoints),
            totalCount = mergedPoints.size,
        )
    }

    private fun mergeTrendPoints(
        points: List<GoldTrendPoint>,
        range: TrendTimeRange,
    ): List<GoldTrendPoint> {
        val startMillis = wallClockMillis() - range.windowMillis
        return points
            .filter { point ->
                point.price > 0.0 &&
                    point.timestampMillis >= startMillis &&
                    !point.isStale &&
                    !point.source.equals("cache", ignoreCase = true)
            }
            .distinctBy { point -> point.timestampMillis }
            .sortedBy { point -> point.timestampMillis }
    }

    private suspend fun nextRefreshDelayMillis(startedAtNanos: Long): Long {
        val intervalMillis = repository.refreshIntervalSeconds.first()
            .coerceAtLeast(UserSettingsDataStore.MIN_REFRESH_INTERVAL_SECONDS) * 1000L
        val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)
        return (intervalMillis - elapsedMillis).coerceAtLeast(0L)
    }

    private fun wallClockMillis(): Long = System.currentTimeMillis()

    companion object {
        private const val TREND_SNAPSHOT_SAVE_INTERVAL_MILLIS = 60_000L
        private const val MAX_RESTORE_AGE_MILLIS = 15 * 60_000L

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
    val selectedTrendRange: TrendTimeRange = TrendTimeRange.FiveMinutes,
    val trendPoints: List<GoldTrendPoint> = emptyList(),
    val trendPointCount: Int = 0,
    val trendMessage: String? = null,
    val isTrendLoading: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val notificationEnabled: Boolean = false,
    val refreshIntervalSeconds: Int = UserSettingsDataStore.DEFAULT_REFRESH_INTERVAL_SECONDS,
)

private data class TrendDisplay(
    val points: List<GoldTrendPoint>,
    val totalCount: Int,
)
