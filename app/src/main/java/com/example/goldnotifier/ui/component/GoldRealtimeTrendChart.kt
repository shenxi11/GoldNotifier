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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

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

        val chartPoints = validPoints.map { point ->
            TrendChartPoint(
                x = xOf(point.timestampMillis),
                y = yOf(point.price),
            )
        }
        val curveSegments = buildTrendCurveSegments(
            interpolateTrendChartPoints(
                points = chartPoints,
                maxDrawPoints = calculateTrendCurveMaxDrawPoints(chartWidth),
            ),
        )
        val path = Path().apply {
            curveSegments.firstOrNull()?.let { firstSegment ->
                moveTo(firstSegment.start.x, firstSegment.start.y)
            }
            curveSegments.forEach { segment ->
                if (segment.isCurved) {
                    cubicTo(
                        segment.firstControl.x,
                        segment.firstControl.y,
                        segment.secondControl.x,
                        segment.secondControl.y,
                        segment.end.x,
                        segment.end.y,
                    )
                } else {
                    lineTo(segment.end.x, segment.end.y)
                }
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

internal data class TrendChartPoint(
    val x: Float,
    val y: Float,
)

internal data class TrendCurveSegment(
    val start: TrendChartPoint,
    val firstControl: TrendChartPoint,
    val secondControl: TrendChartPoint,
    val end: TrendChartPoint,
    val isCurved: Boolean,
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

internal fun calculateTrendCurveMaxDrawPoints(chartWidth: Float): Int {
    if (!chartWidth.isFinite() || chartWidth <= 0f) return MIN_TREND_DRAW_POINTS
    return (chartWidth / TREND_POINT_PIXEL_STEP)
        .roundToInt()
        .coerceIn(MIN_TREND_DRAW_POINTS, MAX_TREND_DRAW_POINTS)
}

internal fun interpolateTrendChartPoints(
    points: List<TrendChartPoint>,
    maxDrawPoints: Int,
): List<TrendChartPoint> {
    if (maxDrawPoints < 2) return emptyList()
    val normalizedPoints = normalizeTrendChartPoints(points)
    if (normalizedPoints.size <= maxDrawPoints) return normalizedPoints

    val criticalPoints = buildCriticalTrendChartPoints(
        points = normalizedPoints,
        maxPointCount = maxDrawPoints,
    )
    val interpolationSlots = (maxDrawPoints - criticalPoints.size).coerceAtLeast(0)
    val interpolatedPoints = if (interpolationSlots == 0) {
        emptyList()
    } else {
        val firstX = normalizedPoints.first().x
        val lastX = normalizedPoints.last().x
        val width = (lastX - firstX).coerceAtLeast(CHART_POINT_EPSILON)
        (1..interpolationSlots).map { index ->
            val x = firstX + width * index / (interpolationSlots + 1)
            TrendChartPoint(
                x = x,
                y = interpolateTrendYAtX(normalizedPoints, x),
            )
        }
    }

    return normalizeTrendChartPoints(interpolatedPoints + criticalPoints)
}

internal fun buildTrendCurveSegments(points: List<TrendChartPoint>): List<TrendCurveSegment> {
    val normalizedPoints = normalizeTrendChartPoints(points)
    if (normalizedPoints.size < 2) return emptyList()
    if (normalizedPoints.size < 3) return normalizedPoints.lineSegments()

    val slopes = calculateMonotoneSlopes(normalizedPoints)
    return (0 until normalizedPoints.lastIndex).map { index ->
        val start = normalizedPoints[index]
        val end = normalizedPoints[index + 1]
        val dx = end.x - start.x
        if (dx <= CHART_POINT_EPSILON) {
            return@map start.lineSegmentTo(end)
        }
        val minY = min(start.y, end.y)
        val maxY = max(start.y, end.y)
        val firstControl = TrendChartPoint(
            x = start.x + dx / 3f,
            y = (start.y + slopes[index] * dx / 3f).coerceIn(minY, maxY),
        )
        val secondControl = TrendChartPoint(
            x = end.x - dx / 3f,
            y = (end.y - slopes[index + 1] * dx / 3f).coerceIn(minY, maxY),
        )
        TrendCurveSegment(
            start = start,
            firstControl = firstControl,
            secondControl = secondControl,
            end = end,
            isCurved = true,
        )
    }
}

private fun normalizeTrendChartPoints(points: List<TrendChartPoint>): List<TrendChartPoint> =
    points
        .filter { point -> point.x.isFinite() && point.y.isFinite() }
        .sortedBy { point -> point.x }
        .fold(mutableListOf<TrendChartPoint>()) { result, point ->
            if (result.isNotEmpty() && abs(result.last().x - point.x) <= CHART_POINT_EPSILON) {
                result[result.lastIndex] = point
            } else {
                result += point
            }
            result
        }

private fun buildCriticalTrendChartPoints(
    points: List<TrendChartPoint>,
    maxPointCount: Int,
): List<TrendChartPoint> {
    val candidates = listOfNotNull(
        points.firstOrNull(),
        points.lastOrNull(),
        points.minByOrNull { point -> point.y },
        points.maxByOrNull { point -> point.y },
    )
    return candidates.fold(mutableListOf<TrendChartPoint>()) { result, point ->
        if (
            result.size < maxPointCount &&
            result.none { existing -> abs(existing.x - point.x) <= CHART_POINT_EPSILON }
        ) {
            result += point
        }
        result
    }
}

private fun interpolateTrendYAtX(
    points: List<TrendChartPoint>,
    x: Float,
): Float {
    if (x <= points.first().x + CHART_POINT_EPSILON) return points.first().y
    if (x >= points.last().x - CHART_POINT_EPSILON) return points.last().y

    for (index in 0 until points.lastIndex) {
        val start = points[index]
        val end = points[index + 1]
        if (x > end.x) continue

        val dx = end.x - start.x
        if (dx <= CHART_POINT_EPSILON) return end.y
        val progress = ((x - start.x) / dx).coerceIn(0f, 1f)
        return start.y + (end.y - start.y) * progress
    }
    return points.last().y
}

private fun calculateMonotoneSlopes(points: List<TrendChartPoint>): FloatArray {
    val deltas = FloatArray(points.lastIndex)
    for (index in 0 until points.lastIndex) {
        val dx = points[index + 1].x - points[index].x
        deltas[index] = if (dx <= CHART_POINT_EPSILON) {
            0f
        } else {
            (points[index + 1].y - points[index].y) / dx
        }
    }

    val slopes = FloatArray(points.size)
    slopes[0] = deltas.first()
    slopes[slopes.lastIndex] = deltas.last()
    for (index in 1 until slopes.lastIndex) {
        val previous = deltas[index - 1]
        val next = deltas[index]
        slopes[index] = if (previous * next <= 0f) 0f else (previous + next) / 2f
    }

    for (index in deltas.indices) {
        val delta = deltas[index]
        if (abs(delta) <= CHART_POINT_EPSILON) {
            slopes[index] = 0f
            slopes[index + 1] = 0f
            continue
        }
        val firstRatio = slopes[index] / delta
        val secondRatio = slopes[index + 1] / delta
        val magnitude = sqrt(firstRatio * firstRatio + secondRatio * secondRatio)
        if (magnitude > MONOTONE_SLOPE_LIMIT) {
            val scale = MONOTONE_SLOPE_LIMIT / magnitude
            slopes[index] = scale * firstRatio * delta
            slopes[index + 1] = scale * secondRatio * delta
        }
    }
    return slopes
}

private fun List<TrendChartPoint>.lineSegments(): List<TrendCurveSegment> =
    zipWithNext { start, end -> start.lineSegmentTo(end) }

private fun TrendChartPoint.lineSegmentTo(end: TrendChartPoint): TrendCurveSegment =
    TrendCurveSegment(
        start = this,
        firstControl = this,
        secondControl = end,
        end = end,
        isCurved = false,
    )

private const val GRID_LINE_COUNT = 4
private const val CHART_POINT_EPSILON = 0.001f
private const val TREND_POINT_PIXEL_STEP = 4f
private const val MIN_TREND_DRAW_POINTS = 80
private const val MAX_TREND_DRAW_POINTS = 240
private const val MONOTONE_SLOPE_LIMIT = 3f
