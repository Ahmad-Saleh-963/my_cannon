package com.example.my_cannon.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my_cannon.data.model.UtmPoint
import com.example.my_cannon.ui.theme.TacticalBlue
import com.example.my_cannon.ui.theme.TacticalOlive
import com.example.my_cannon.ui.theme.TacticalRed
import com.example.my_cannon.ui.viewmodel.CannonViewModel
import java.util.Locale
import kotlin.math.*

@Composable
fun TacticalGeometryScreen(viewModel: CannonViewModel) {
    if (viewModel.cannonPos == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("الرجاء تحديد موقع المربط على الخريطة أولاً", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Text(
            "المخطط التكتيكي الهندسي",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp)
        )

        Box(modifier = Modifier.weight(1f).padding(16.dp)) {
            TacticalCanvas(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun TacticalCanvas(
    viewModel: CannonViewModel,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val cannonUtm = viewModel.cannonPos?.utmPoint ?: return
    val refPoints = viewModel.referencePoints.map { it.utmPoint }
    val targets = viewModel.targets.map { it.utmPoint }

    val allPoints = mutableListOf<UtmPoint>().apply {
        add(cannonUtm)
        addAll(targets)
        addAll(refPoints)
    }

    Canvas(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))) {
        val width = size.width
        val height = size.height
        val padding = 100f

        // 1. Math: Relative Coordinates and Scaling
        val relPoints = allPoints.map { 
            Offset((it.easting - cannonUtm.easting).toFloat(), (it.northing - cannonUtm.northing).toFloat())
        }

        val minX = relPoints.minOf { it.x }.coerceAtMost(-100f)
        val maxX = relPoints.maxOf { it.x }.coerceAtLeast(100f)
        val minY = relPoints.minOf { it.y }.coerceAtMost(-100f)
        val maxY = relPoints.maxOf { it.y }.coerceAtLeast(100f)

        val rangeX = (maxX - minX).coerceAtLeast(1f)
        val rangeY = (maxY - minY).coerceAtLeast(1f)

        val scale = min((width - 2 * padding) / rangeX, (height - 2 * padding) / rangeY)

        val toCanvas = { relX: Float, relY: Float ->
            Offset(
                (relX - minX) * scale + (width - rangeX * scale) / 2,
                height - ((relY - minY) * scale + (height - rangeY * scale) / 2)
            )
        }

        val batteryPos = toCanvas(0f, 0f)

        // 2. Draw Decorative Compass Circle
        drawCircle(
            color = Color.Gray.copy(alpha = 0.3f),
            radius = 200f * scale,
            center = batteryPos,
            style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f))
        )
        drawCircle(
            color = Color.Gray.copy(alpha = 0.2f),
            radius = 500f * scale,
            center = batteryPos,
            style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f))
        )

        // 3. Draw Grid and Axes
        val dashPathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        
        // Draw Grid
        val step = 500f * scale
        if (step > 20f) {
            var currX = batteryPos.x % step
            while (currX < width) {
                drawLine(Color.Gray.copy(alpha = 0.2f), Offset(currX, 0f), Offset(currX, height), 1f)
                currX += step
            }
            var currY = batteryPos.y % step
            while (currY < height) {
                drawLine(Color.Gray.copy(alpha = 0.2f), Offset(0f, currY), Offset(width, currY), 1f)
                currY += step
            }
        }

        // Main Axes through Battery
        drawLine(
            color = Color.Gray.copy(alpha = 0.5f),
            start = Offset(0f, batteryPos.y),
            end = Offset(width, batteryPos.y),
            strokeWidth = 2f,
            pathEffect = dashPathEffect
        )
        drawLine(
            color = Color.Gray.copy(alpha = 0.5f),
            start = Offset(batteryPos.x, 0f),
            end = Offset(batteryPos.x, height),
            strokeWidth = 2f,
            pathEffect = dashPathEffect
        )

        // 3. Draw Tactical Lines and Annotations
        viewModel.targets.forEachIndexed { index, target ->
            val tUtm = target.utmPoint
            val tPos = toCanvas((tUtm.easting - cannonUtm.easting).toFloat(), (tUtm.northing - cannonUtm.northing).toFloat())
            
            // Connection Line
            drawLine(TacticalRed, batteryPos, tPos, strokeWidth = 5f)
            
            // Distance Label
            val dist = sqrt((tUtm.easting - cannonUtm.easting).pow(2.0) + (tUtm.northing - cannonUtm.northing).pow(2.0))
            drawLabel(textMeasurer, String.format(Locale.US, "T%d: %.0fm", index + 1, dist), (batteryPos + tPos) / 2f, TacticalRed, isBackground = true)

            // Azimuth Label near Battery
            val res = viewModel.getTargetResult(target)
            val az = res?.normalizedAzimuth ?: 0.0
            drawLabel(textMeasurer, String.format(Locale.US, "T%d Az: %.1f°", index + 1, az), batteryPos + Offset(40f, -40f - (20f * index)), TacticalRed, isBackground = true)
            
            // Projections to Axes
            drawLine(TacticalRed.copy(alpha = 0.6f), tPos, Offset(batteryPos.x, tPos.y), pathEffect = dashPathEffect)
            drawLine(TacticalRed.copy(alpha = 0.6f), tPos, Offset(tPos.x, batteryPos.y), pathEffect = dashPathEffect)
        }

        viewModel.referencePoints.forEachIndexed { index, ref ->
            val utm = ref.utmPoint
            val rPos = toCanvas((utm.easting - cannonUtm.easting).toFloat(), (utm.northing - cannonUtm.northing).toFloat())
            drawLine(TacticalBlue, batteryPos, rPos, strokeWidth = 3f)
            
            val dist = sqrt((utm.easting - cannonUtm.easting).pow(2.0) + (utm.northing - cannonUtm.northing).pow(2.0))
            drawLabel(textMeasurer, String.format(Locale.US, "R%d: %.0fm", index + 1, dist), (batteryPos + rPos) / 2f, TacticalBlue, isBackground = true)

            // Ref Azimuth
            val refAz = viewModel.getRefResult(ref)?.normalizedAzimuth ?: 0.0
            drawLabel(textMeasurer, String.format(Locale.US, "R%d Az: %.1f°", index + 1, refAz), batteryPos + Offset(40f, 30f * (index + 1)), TacticalBlue, isBackground = true)
        }

        // 4. Draw Markers
        // Battery (B)
        drawCircle(TacticalOlive, radius = 15f, center = batteryPos)
        drawCircle(Color.White, radius = 7f, center = batteryPos)
        drawLabel(textMeasurer, "B (Battery)", batteryPos + Offset(20f, 20f), TacticalOlive, isBackground = true)

        // Targets (T)
        viewModel.targets.forEachIndexed { i, t ->
            val tUtm = t.utmPoint
            val tPos = toCanvas((tUtm.easting - cannonUtm.easting).toFloat(), (tUtm.northing - cannonUtm.northing).toFloat())
            drawMarker(tPos, TacticalRed, isTarget = true)
            drawLabel(textMeasurer, String.format(Locale.US, "T%d (%.0f, %.0f)", i + 1, tUtm.easting, tUtm.northing), tPos + Offset(20f, -20f), TacticalRed, isBackground = true)
        }

        // Refs (R)
        viewModel.referencePoints.forEachIndexed { i, ref ->
            val utm = ref.utmPoint
            val rPos = toCanvas((utm.easting - cannonUtm.easting).toFloat(), (utm.northing - cannonUtm.northing).toFloat())
            drawMarker(rPos, TacticalBlue, isTarget = false)
            drawLabel(textMeasurer, String.format(Locale.US, "R%d (%.0f, %.0f)", i + 1, utm.easting, utm.northing), rPos + Offset(20f, 20f), TacticalBlue, isBackground = true)
        }
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMarker(center: Offset, color: Color, isTarget: Boolean) {
    if (isTarget) {
        // Draw Triangle for Target
        val path = Path().apply {
            moveTo(center.x, center.y - 20f)
            lineTo(center.x - 20f, center.y + 20f)
            lineTo(center.x + 20f, center.y + 20f)
            close()
        }
        drawPath(path, color)
        drawPath(path, Color.White, style = Stroke(width = 2f))
    } else {
        // Draw Square for Ref Points
        drawRect(color, center - Offset(15f, 15f), size = Size(30f, 30f))
        drawRect(Color.White, center - Offset(15f, 15f), size = Size(30f, 30f), style = Stroke(width = 2f))
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabel(
    textMeasurer: TextMeasurer,
    text: String,
    position: Offset,
    color: Color,
    isBackground: Boolean = false
) {
    val textLayoutResult = textMeasurer.measure(
        text = text,
        style = TextStyle(color = color, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    )
    
    if (isBackground) {
        drawRect(
            color = Color.Black.copy(alpha = 0.6f),
            topLeft = position,
            size = Size(textLayoutResult.size.width.toFloat() + 8f, textLayoutResult.size.height.toFloat() + 4f)
        )
    }

    drawText(
        textLayoutResult = textLayoutResult,
        topLeft = position + Offset(4f, 2f)
    )
}
