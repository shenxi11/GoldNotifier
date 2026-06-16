package com.example.goldnotifier.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.goldnotifier.domain.model.GoldCandle
import com.tradingview.lightweightcharts.api.chart.models.color.toIntColor
import com.tradingview.lightweightcharts.api.chart.models.color.surface.SolidColor
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi
import com.tradingview.lightweightcharts.api.options.models.candlestickSeriesOptions
import com.tradingview.lightweightcharts.api.options.models.chartOptions
import com.tradingview.lightweightcharts.api.options.models.gridLineOptions
import com.tradingview.lightweightcharts.api.options.models.gridOptions
import com.tradingview.lightweightcharts.api.options.models.layoutOptions
import com.tradingview.lightweightcharts.api.options.models.priceScaleOptions
import com.tradingview.lightweightcharts.api.options.models.timeScaleOptions
import com.tradingview.lightweightcharts.api.series.common.SeriesData
import com.tradingview.lightweightcharts.api.series.models.CandlestickData
import com.tradingview.lightweightcharts.api.series.models.Time
import com.tradingview.lightweightcharts.view.ChartsView

/*
模块名: TradingViewKLineChart
功能概述: 在 Compose 首页中嵌入 TradingView Lightweight Charts K 线视图。
对外接口: TradingViewKLineChart
依赖关系: Compose AndroidView、TradingView lightweightcharts Android wrapper
输入输出: 输入服务端聚合后的 GoldCandle 列表，输出可交互的 WebView K 线图。
异常与错误: WebView 能力不足或 wrapper 初始化失败时展示卡片内错误态，不影响折线图回退。
维护说明: 本组件只做渲染，K 线数据加载和实时更新由 HomeViewModel 负责。
*/
@Composable
fun TradingViewKLineChart(
    candles: List<GoldCandle>,
    modifier: Modifier = Modifier,
) {
    val latestCandles by rememberUpdatedState(candles)
    var seriesApi by remember { mutableStateOf<SeriesApi?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(TradingViewPanel),
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            factory = { context ->
                var configured = false
                ChartsView(context).apply {
                    setBackgroundColor(TradingViewPanel.toArgb())
                    subscribeOnChartStateChange { state ->
                        when (state) {
                            is ChartsView.State.Ready -> {
                                if (configured) return@subscribeOnChartStateChange
                                configured = true
                                loadError = null
                                api.applyOptions(tradingViewOptions())
                                api.addCandlestickSeries(
                                    options = tradingViewCandlestickOptions(),
                                ) { series ->
                                    seriesApi = series
                                    series.setData(latestCandles.toTradingViewData())
                                    api.timeScale.fitContent()
                                }
                            }
                            is ChartsView.State.Error -> {
                                loadError = "K线图初始化失败"
                            }
                            is ChartsView.State.Preparing -> Unit
                        }
                    }
                }
            },
            update = { view ->
                if (view.state is ChartsView.State.Ready) {
                    seriesApi?.setData(latestCandles.toTradingViewData())
                    view.api.timeScale.fitContent()
                }
            },
        )

        loadError?.let { message ->
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleSmall,
                    color = TradingViewTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "可切回折线查看实时趋势",
                    style = MaterialTheme.typography.bodySmall,
                    color = TradingViewTextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun tradingViewOptions() = chartOptions {
    layout = layoutOptions {
        background = SolidColor(TradingViewPanel.toArgb())
        textColor = TradingViewTextSecondary.toArgb().toIntColor()
        fontSize = 11
    }
    grid = gridOptions {
        vertLines = gridLineOptions {
            color = TradingViewGrid.toArgb().toIntColor()
            visible = true
        }
        horzLines = gridLineOptions {
            color = TradingViewGrid.toArgb().toIntColor()
            visible = true
        }
    }
    leftPriceScale = priceScaleOptions {
        visible = false
        borderVisible = false
    }
    rightPriceScale = priceScaleOptions {
        visible = true
        borderVisible = false
        ticksVisible = true
    }
    timeScale = timeScaleOptions {
        borderVisible = false
        timeVisible = true
        secondsVisible = false
        rightOffset = 2f
    }
}

private fun tradingViewCandlestickOptions() = candlestickSeriesOptions {
    upColor = TradingViewRed.toArgb().toIntColor()
    downColor = TradingViewGreen.toArgb().toIntColor()
    borderVisible = false
    wickVisible = true
    wickUpColor = TradingViewRed.toArgb().toIntColor()
    wickDownColor = TradingViewGreen.toArgb().toIntColor()
}

private fun List<GoldCandle>.toTradingViewData(): List<SeriesData> =
    filter { candle ->
        candle.timestampMillis > 0L &&
            candle.open > 0.0 &&
            candle.high > 0.0 &&
            candle.low > 0.0 &&
            candle.close > 0.0
    }
        .sortedBy { candle -> candle.timestampMillis }
        .map { candle ->
            CandlestickData(
                time = Time.Utc(candle.timestampMillis / 1000L),
                open = candle.open.toFloat(),
                high = candle.high.toFloat(),
                low = candle.low.toFloat(),
                close = candle.close.toFloat(),
            )
        }

private val TradingViewPanel = Color(0xFF111827)
private val TradingViewGrid = Color(0xFF1F2937)
private val TradingViewTextPrimary = Color(0xFFF9FAFB)
private val TradingViewTextSecondary = Color(0xFF9CA3AF)
private val TradingViewGreen = Color(0xFF10B981)
private val TradingViewRed = Color(0xFFEF4444)
