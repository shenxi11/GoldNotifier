package com.example.goldnotifier.ui.component

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.goldnotifier.domain.model.GoldTrendPoint
import java.util.Locale
import kotlin.math.max

/*
模块名: GoldRealtimeTrendChart
功能概述: 使用 Compose Canvas 绘制首页所选时间尺度的金价趋势折线。
对外接口: GoldRealtimeTrendChart
依赖关系: Compose Canvas、GoldTrendPoint
输入输出: 输入趋势点列表和线条颜色，输出轻量级趋势图。
异常与错误: 少于 2 个有效点时直接跳过绘制，由外层卡片展示空状态。
维护说明: 坐标轴仅基于传入点计算，调用方负责按时间尺度裁剪和抽样。
*/
@Composable
fun GoldRealtimeTrendChart(
    points: List<GoldTrendPoint>,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    val gridColor = Color(0xFF374151)
    val baselineColor = Color(0xFF4B5563)
    val axisTextColor = Color(0xFF6B7280)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
    ) {
        val validPoints = points
            .filter { it.price > 0.0 }
            .sortedBy { it.timestampMillis }
        if (validPoints.size < 2) return@Canvas

        val horizontalPadding = 6.dp.toPx()
        val axisWidth = 48.dp.toPx()
        val axisGap = 8.dp.toPx()
        val verticalPadding = 10.dp.toPx()
        val chartLeft = horizontalPadding + axisWidth + axisGap
        val chartRight = size.width - horizontalPadding
        val chartWidth = (chartRight - chartLeft).coerceAtLeast(1f)
        val chartHeight = (size.height - verticalPadding * 2).coerceAtLeast(1f)
        val minPrice = validPoints.minOf { it.price }
        val maxPrice = validPoints.maxOf { it.price }
        val priceRange = maxPrice - minPrice
        val minTime = validPoints.first().timestampMillis
        val maxTime = validPoints.last().timestampMillis
        val timeRange = max(1L, maxTime - minTime)
        val axisLabels = calculateTrendPriceAxisLabels(validPoints, GRID_LINE_COUNT)
        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = axisTextColor.toArgb()
            textAlign = Paint.Align.RIGHT
            textSize = 10.sp.toPx()
        }

        axisLabels.forEachIndexed { index, label ->
            val y = verticalPadding + chartHeight * index / (GRID_LINE_COUNT - 1)
            drawLine(
                color = gridColor,
                start = Offset(chartLeft, y),
                end = Offset(chartRight, y),
                strokeWidth = 1.dp.toPx(),
            )
            drawContext.canvas.nativeCanvas.drawText(
                label.text,
                horizontalPadding + axisWidth,
                y - (axisPaint.ascent() + axisPaint.descent()) / 2f,
                axisPaint,
            )
        }

        fun xOf(timestampMillis: Long): Float {
            val progress = (timestampMillis - minTime).toFloat() / timeRange.toFloat()
            return chartLeft + chartWidth * progress.coerceIn(0f, 1f)
        }

        fun yOf(price: Double): Float {
            if (priceRange == 0.0) {
                return verticalPadding + chartHeight / 2f
            }
            val progress = ((maxPrice - price) / priceRange).toFloat()
            return verticalPadding + chartHeight * progress.coerceIn(0f, 1f)
        }

        val baselineY = yOf(validPoints.first().price)
        drawLine(
            color = baselineColor,
            start = Offset(chartLeft, baselineY),
            end = Offset(chartRight, baselineY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(
                intervals = floatArrayOf(6.dp.toPx(), 6.dp.toPx()),
                phase = 0f,
            ),
        )

        val path = Path()
        validPoints.forEachIndexed { index, point ->
            val x = xOf(point.timestampMillis)
            val y = yOf(point.price)
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        val last = validPoints.last()
        drawCircle(
            color = lineColor,
            radius = 4.dp.toPx(),
            center = Offset(xOf(last.timestampMillis), yOf(last.price)),
        )
    }
}

internal data class TrendPriceAxisLabel(
    val price: Double,
    val text: String,
)

internal fun calculateTrendPriceAxisLabels(
    points: List<GoldTrendPoint>,
    labelCount: Int = GRID_LINE_COUNT,
): List<TrendPriceAxisLabel> {
    if (labelCount < 2) return emptyList()
    val validPoints = points.filter { it.price > 0.0 }
    if (validPoints.isEmpty()) return emptyList()

    val minPrice = validPoints.minOf { it.price }
    val maxPrice = validPoints.maxOf { it.price }
    val step = (maxPrice - minPrice) / (labelCount - 1)
    return (0 until labelCount).map { index ->
        val price = if (step == 0.0) maxPrice else maxPrice - step * index
        TrendPriceAxisLabel(
            price = price,
            text = String.format(Locale.CHINA, "%.2f", price),
        )
    }
}

private const val GRID_LINE_COUNT = 4
