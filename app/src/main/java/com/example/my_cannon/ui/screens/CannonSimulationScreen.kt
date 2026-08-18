package com.example.my_cannon.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextFieldDefaults
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.my_cannon.ui.viewmodel.CannonViewModel
import com.example.my_cannon.data.model.BallisticParams
import java.util.Locale
import kotlin.math.sqrt

@Composable
fun CannonSimulationScreen(onExit: () -> Unit, viewModel: CannonViewModel = viewModel()) {
    // States for Cannon (0-50000m horizontal, 0-3000m vertical)
    var cannonDist by remember { mutableFloatStateOf(2000f) }
    var cannonHeight by remember { mutableFloatStateOf(200f) }

    // States for Target
    var targetDist by remember { mutableFloatStateOf(15000f) }
    var targetHeight by remember { mutableFloatStateOf(100f) }

    var simulationSize by remember { mutableStateOf(IntSize.Zero) }
    var showBallisticDialog by remember { mutableStateOf(false) }

    // Missile Simulation States
    var isFiring by remember { mutableStateOf(false) }
    var missileX by remember { mutableFloatStateOf(0f) }
    var missileY by remember { mutableFloatStateOf(0f) }
    var missileAngle by remember { mutableFloatStateOf(0f) }
    var flameScale by remember { mutableFloatStateOf(0f) }
    var showExplosion by remember { mutableStateOf(false) }
    val smokeTrail = remember { mutableStateListOf<Offset>() }

    // Professional Animations - Spring-based for direct, fluid interaction
    val animCannonX by animateFloatAsState(
        targetValue = cannonDist,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "cannonX"
    )
    val animCannonY by animateFloatAsState(
        targetValue = cannonHeight,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "cannonY"
    )
    val animTargetX by animateFloatAsState(
        targetValue = targetDist,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "targetX"
    )
    val animTargetY by animateFloatAsState(
        targetValue = targetHeight,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "targetY"
    )

    // Animation for clouds
    val infiniteTransition = rememberInfiniteTransition(label = "clouds")
    val cloudOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(40000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cloudOffset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF87CEEB)) // Sky Blue Fallback
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val widthPx = simulationSize.width.toFloat()
                        val heightPx = simulationSize.height.toFloat()
                        val isRightSide = change.position.x > widthPx / 2
                        
                        // Scale 50km (50000m)
                        if (!isRightSide) {
                            cannonDist = (cannonDist + (dragAmount.x / widthPx) * 50000f).coerceIn(0f, 25000f)
                            cannonHeight = (cannonHeight - (dragAmount.y / heightPx) * 3000f).coerceIn(0f, 3000f)
                        } else {
                            targetDist = (targetDist + (dragAmount.x / widthPx) * 50000f).coerceIn(25000f, 50000f)
                            targetHeight = (targetHeight - (dragAmount.y / heightPx) * 3000f).coerceIn(0f, 3000f)
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            simulationSize = IntSize(size.width.toInt(), size.height.toInt())
            
            // 1. Draw Nature Background (Sky, Sun, Clouds)
            drawNatureBackground(cloudOffset)
            
            // 2. Draw Grid (Subtle overlay)
            drawTacticalGrid()

            // 3. Draw Trajectory Preview
            drawTrajectoryPreview(animCannonX, animCannonY, animTargetX, animTargetY)
            
            // 4. Draw Detailed Terrain
            drawNaturalTerrain()
        }

        // Cannon Visual
        TacticalObject(
            dist = animCannonX,
            height = animCannonY,
            isCannon = true,
            color = Color(0xFF00E5FF) // Neon Cyan
        )

        // Target Visual
        TacticalObject(
            dist = animTargetX,
            height = animTargetY,
            isCannon = false,
            color = Color(0xFFFF1744) // Neon Red
        )

        // Minimized HUD Overlay
        SimulationHUD(
            animCannonX, animCannonY,
            animTargetX, animTargetY
        )

        // Connection Line
        ConnectionLine(animCannonX, animCannonY, animTargetX, animTargetY)

        // Missile Rendering
        if (isFiring) {
            MissileVisual(
                x = missileX,
                y = missileY,
                angle = missileAngle,
                flameScale = flameScale,
                smokeTrail = smokeTrail
            )
        }

        // Explosion Effect
        if (showExplosion) {
            ExplosionVisual(animTargetX, animTargetY)
        }

        // Settings Button (Top Left)
        IconButton(
            onClick = { showBallisticDialog = true },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                .border(1.dp, Color.Cyan.copy(alpha = 0.3f), CircleShape)
        ) {
            Icon(Icons.Default.Settings, contentDescription = "إعدادات الرماية", tint = Color.Cyan)
        }

        // Exit Button (Back to Map)
        IconButton(
            onClick = onExit,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "خروج", tint = Color.White)
        }

        // FIRE BUTTON (Action)
        Button(
            onClick = {
                if (!isFiring) {
                    isFiring = true
                }
            },
            enabled = !isFiring,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .height(56.dp)
                .width(120.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Red,
                disabledContainerColor = Color.DarkGray
            ),
            shape = RoundedCornerShape(8.dp),
            elevation = ButtonDefaults.buttonElevation(8.dp)
        ) {
            Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("إطلاق", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }

    // Missile Logic (Physics Engine Simulation)
    if (isFiring) {
        LaunchedEffect(Unit) {
            val startX = cannonDist
            val startY = cannonHeight
            val endX = targetDist
            val endY = targetHeight

            val duration = 4000L // 4 seconds flight for better visibility
            val startTime = System.currentTimeMillis()

            smokeTrail.clear()
            showExplosion = false

            while (System.currentTimeMillis() - startTime < duration) {
                val progress = (System.currentTimeMillis() - startTime).toFloat() / duration

                // Ballistic Trajectory (Parabolic) - Keep inside 3000m ceiling
                val currentX = startX + (endX - startX) * progress
                val dist = Math.abs(endX - startX)
                val peakHeight = (dist * 0.15f).coerceAtMost(1500f) // Dynamic peak, max 1500m extra

                val currentY = startY + (endY - startY) * progress + (peakHeight * 4 * progress * (1 - progress))

                // Calculate Angle
                val nextProgress = (progress + 0.005f).coerceAtMost(1f)
                val nextX = startX + (endX - startX) * nextProgress
                val nextY = startY + (endY - startY) * nextProgress + (peakHeight * 4 * nextProgress * (1 - nextProgress))

                missileX = currentX
                missileY = currentY
                missileAngle = Math.toDegrees(Math.atan2((nextY - currentY).toDouble(), (nextX - currentX).toDouble())).toFloat()

                flameScale = if (progress < 0.25f) (1f - progress / 0.25f) else 0f
                smokeTrail.add(Offset(currentX, currentY))

                kotlinx.coroutines.delay(16)
            }

            isFiring = false
            showExplosion = true
            kotlinx.coroutines.delay(1000)
            showExplosion = false
        }
    }

    if (showBallisticDialog) {
        BallisticSettingsDialog(
            params = viewModel.ballisticParams,
            onDismiss = { showBallisticDialog = false },
            onConfirm = {
                viewModel.ballisticParams = it
                showBallisticDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BallisticSettingsDialog(
    params: BallisticParams,
    onDismiss: () -> Unit,
    onConfirm: (BallisticParams) -> Unit
) {
    var longWindActive by remember { mutableStateOf(params.longWindActive.toString()) }
    var longWindInactive by remember { mutableStateOf(params.longWindInactive.toString()) }
    var crossWindActive by remember { mutableStateOf(params.crossWindActive.toString()) }
    var crossWindInactive by remember { mutableStateOf(params.crossWindInactive.toString()) }
    var airTempDelta by remember { mutableStateOf(params.airTempDelta.toString()) }
    var airPressureDelta by remember { mutableStateOf(params.airPressureDelta.toString()) }
    var powderTemp by remember { mutableStateOf(params.powderTemp.toString()) }
    var mvDelta by remember { mutableStateOf(params.muzzleVelocityDelta.toString()) }
    var windFromLeft by remember { mutableStateOf(params.crossWindFromLeft) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Color.Cyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
        containerColor = Color(0xFF0D1117),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = Color.Cyan)
                Spacer(Modifier.width(12.dp))
                Text("تصحيحات الرماية والظروف الجوية", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("سرعة الرياح الطولية (m/s)", color = Color.Gray, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BallisticField(label = "فعال", value = longWindActive, onValueChange = { longWindActive = it }, modifier = Modifier.weight(1f))
                    BallisticField(label = "غير فعال", value = longWindInactive, onValueChange = { longWindInactive = it }, modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(12.dp))
                Text("سرعة الرياح العرضية (m/s)", color = Color.Gray, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BallisticField(label = "فعال", value = crossWindActive, onValueChange = { crossWindActive = it }, modifier = Modifier.weight(1f))
                    BallisticField(label = "غير فعال", value = crossWindInactive, onValueChange = { crossWindInactive = it }, modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("اتجاه الرياح العرضية:", color = Color.White, fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    FilterChip(
                        selected = windFromLeft,
                        onClick = { windFromLeft = true },
                        label = { Text("يسار -> يمين", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.Cyan.copy(alpha = 0.3f), selectedLabelColor = Color.Cyan)
                    )
                    Spacer(Modifier.width(4.dp))
                    FilterChip(
                        selected = !windFromLeft,
                        onClick = { windFromLeft = false },
                        label = { Text("يمين -> يسار", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.Cyan.copy(alpha = 0.3f), selectedLabelColor = Color.Cyan)
                    )
                }

                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color.Gray.copy(alpha = 0.2f))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BallisticField(label = "تبدل حرارة الجو (ΔT)", value = airTempDelta, onValueChange = { airTempDelta = it }, modifier = Modifier.weight(1f))
                    BallisticField(label = "تبدل ضغط الجو (ΔP)", value = airPressureDelta, onValueChange = { airPressureDelta = it }, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BallisticField(label = "حرارة الحشوة", value = powderTemp, onValueChange = { powderTemp = it }, modifier = Modifier.weight(1f))
                    BallisticField(label = "تبدل الدفع النوعي (ΔV0)", value = mvDelta, onValueChange = { mvDelta = it }, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(BallisticParams(
                        longWindActive = longWindActive.toDoubleOrNull() ?: 0.0,
                        longWindInactive = longWindInactive.toDoubleOrNull() ?: 0.0,
                        crossWindActive = crossWindActive.toDoubleOrNull() ?: 0.0,
                        crossWindInactive = crossWindInactive.toDoubleOrNull() ?: 0.0,
                        airTempDelta = airTempDelta.toDoubleOrNull() ?: 0.0,
                        airPressureDelta = airPressureDelta.toDoubleOrNull() ?: 0.0,
                        powderTemp = powderTemp.toDoubleOrNull() ?: 0.0,
                        muzzleVelocityDelta = mvDelta.toDoubleOrNull() ?: 0.0,
                        crossWindFromLeft = windFromLeft
                    ))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan, contentColor = Color.Black)
            ) {
                Text("تطبيق التعديلات", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = Color.Gray)
            }
        }
    )
}

@Composable
fun BallisticField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 10.sp) },
        modifier = modifier,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Cyan,
            unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
            focusedLabelColor = Color.Cyan,
            unfocusedLabelColor = Color.Gray
        ),
        shape = RoundedCornerShape(8.dp)
    )
}

fun DrawScope.drawNatureBackground(cloudOffset: Float) {
    // Sky Gradient
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF1A237E), Color(0xFF0288D1), Color(0xFF81D4FA))
        ),
        size = size
    )

    // Sun/Glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFD600), Color.Transparent),
            center = Offset(size.width * 0.8f, size.height * 0.15f),
            radius = 120.dp.toPx()
        ),
        radius = 120.dp.toPx(),
        center = Offset(size.width * 0.8f, size.height * 0.15f)
    )

    // Clouds (Stylized)
    val cloudColor = Color.White.copy(alpha = 0.3f)
    for (i in 0..5) {
        val x = ((cloudOffset + i * 300.dp.toPx()) % (size.width + 200.dp.toPx())) - 100.dp.toPx()
        val y = 50.dp.toPx() + (i * 40.dp.toPx())
        drawCircle(cloudColor, 30.dp.toPx(), Offset(x, y))
        drawCircle(cloudColor, 40.dp.toPx(), Offset(x + 25.dp.toPx(), y + 10.dp.toPx()))
        drawCircle(cloudColor, 30.dp.toPx(), Offset(x + 50.dp.toPx(), y))
    }
}

fun DrawScope.drawNaturalTerrain() {
    // Far Mountains
    val mountainPath = Path().apply {
        moveTo(0f, size.height)
        lineTo(0f, size.height * 0.7f)
        lineTo(size.width * 0.2f, size.height * 0.55f)
        lineTo(size.width * 0.4f, size.height * 0.75f)
        lineTo(size.width * 0.6f, size.height * 0.5f)
        lineTo(size.width * 0.8f, size.height * 0.7f)
        lineTo(size.width, size.height * 0.6f)
        lineTo(size.width, size.height)
        close()
    }
    drawPath(mountainPath, Color(0xFF37474F).copy(alpha = 0.4f))

    // Middle Hills
    val hillsPath = Path().apply {
        moveTo(0f, size.height)
        lineTo(0f, size.height - 100.dp.toPx())
        quadraticTo(size.width * 0.3f, size.height - 150.dp.toPx(), size.width * 0.5f, size.height - 80.dp.toPx())
        quadraticTo(size.width * 0.8f, size.height - 180.dp.toPx(), size.width, size.height - 120.dp.toPx())
        lineTo(size.width, size.height)
        close()
    }
    drawPath(
        path = hillsPath,
        brush = Brush.verticalGradient(listOf(Color(0xFF2E7D32), Color(0xFF1B5E20)))
    )

    // Foreground Ground
    drawRect(
        color = Color(0xFF1B5E20),
        topLeft = Offset(0f, size.height - 40.dp.toPx()),
        size = Size(size.width, 40.dp.toPx())
    )
}

fun DrawScope.drawTacticalGrid() {
    val step = 60.dp.toPx()
    val color = Color.White.copy(alpha = 0.05f)
    for (i in 0..(size.width / step).toInt()) {
        val x = i * step
        drawLine(color, Offset(x, 0f), Offset(x, size.height))
    }
    for (i in 0..(size.height / step).toInt()) {
        val y = i * step
        drawLine(color, Offset(0f, y), Offset(size.width, y))
    }
}

fun DrawScope.drawTrajectoryPreview(sX: Float, sY: Float, eX: Float, eY: Float) {
    val path = Path().apply {
        val w = size.width
        val h = size.height
        val dist = Math.abs(eX - sX)
        val peak = (dist * 0.15f).coerceAtMost(1500f)

        moveTo((sX / 50000f) * w, h - (sY / 3000f) * (h * 0.8f) - 60.dp.toPx())

        for (i in 1..20) {
            val p = i / 20f
            val curX = sX + (eX - sX) * p
            val curY = sY + (eY - sY) * p + (peak * 4 * p * (1 - p))
            lineTo((curX / 50000f) * w, h - (curY / 3000f) * (h * 0.8f) - 60.dp.toPx())
        }
    }
    drawPath(
        path = path,
        color = Color.White.copy(alpha = 0.15f),
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
    )
}

@Composable
fun SimulationHUD(cDist: Float, cHeight: Float, tDist: Float, tHeight: Float) {
    Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // Cannon HUD
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .border(0.5.dp, Color.Cyan.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.GpsFixed, contentDescription = null, tint = Color.Cyan, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("${cDist.toInt()}m | ${cHeight.toInt()}m", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        // Target HUD
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .border(0.5.dp, Color.Red.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${tDist.toInt()}m | ${tHeight.toInt()}m", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Default.TrackChanges, contentDescription = null, tint = Color.Red, modifier = Modifier.size(14.dp))
        }

        // Calculated Info
        val directDist = sqrt((tDist - cDist).let { it * it } + (tHeight - cHeight).let { it * it })
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            color = Color.Black.copy(alpha = 0.8f),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
        ) {
            Text(
                text = "RANGE: ${String.format(Locale.US, "%.0f", directDist)}m",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                color = Color.Green,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
fun TacticalObject(dist: Float, height: Float, isCannon: Boolean, color: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val screenHeight = size.height

        // Updated Scale to 50000f
        val xPos = (dist / 50000f) * width
        val yPos = screenHeight - (height / 3000f) * (screenHeight * 0.8f) - 60.dp.toPx()

        // Shadow on ground
        drawOval(
            color = Color.Black.copy(alpha = 0.2f),
            topLeft = Offset(xPos - 15.dp.toPx(), screenHeight - 45.dp.toPx()),
            size = Size(30.dp.toPx(), 10.dp.toPx())
        )

        if (isCannon) {
            drawCircle(color.copy(alpha = 0.3f), radius = 25.dp.toPx(), center = Offset(xPos, yPos))
            drawCircle(color, radius = 5.dp.toPx(), center = Offset(xPos, yPos))
            rotate(-30f, pivot = Offset(xPos, yPos)) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(xPos, yPos - 3.dp.toPx()),
                    size = Size(35.dp.toPx(), 6.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
        } else {
            drawCircle(color.copy(alpha = 0.3f), radius = 20.dp.toPx(), center = Offset(xPos, yPos))
            drawLine(color, Offset(xPos - 12.dp.toPx(), yPos), Offset(xPos + 12.dp.toPx(), yPos), strokeWidth = 2.5.dp.toPx())
            drawLine(color, Offset(xPos, yPos - 12.dp.toPx()), Offset(xPos, yPos + 12.dp.toPx()), strokeWidth = 2.5.dp.toPx())
            drawCircle(color, radius = 7.dp.toPx(), center = Offset(xPos, yPos), style = Stroke(width = 2.dp.toPx()))
        }

        // Tactical Indicator Line
        drawLine(
            color = color.copy(alpha = 0.4f),
            start = Offset(xPos, yPos),
            end = Offset(xPos, screenHeight - 40.dp.toPx()),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )
    }
}

@Composable
fun MissileVisual(x: Float, y: Float, angle: Float, flameScale: Float, smokeTrail: List<Offset>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. Draw Smoke Trail
        smokeTrail.forEachIndexed { index, offset ->
            val pX = (offset.x / 50000f) * w
            val pY = h - (offset.y / 3000f) * (h * 0.8f) - 60.dp.toPx()
            drawCircle(
                color = Color.White.copy(alpha = 0.2f * (index.toFloat() / smokeTrail.size)),
                radius = (2.dp.toPx() + (index.toFloat() / smokeTrail.size) * 8.dp.toPx()),
                center = Offset(pX, pY)
            )
        }

        // 2. Scale position for visual
        val visualX = (x / 50000f) * w
        val visualY = h - (y / 3000f) * (h * 0.8f) - 60.dp.toPx()

        rotate(degrees = -angle, pivot = Offset(visualX, visualY)) {
            // Missile Body
            drawRoundRect(
                color = Color.LightGray,
                topLeft = Offset(visualX - 15.dp.toPx(), visualY - 3.dp.toPx()),
                size = Size(30.dp.toPx(), 6.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
            // Missile Head
            drawPath(
                path = Path().apply {
                    moveTo(visualX + 15.dp.toPx(), visualY - 3.dp.toPx())
                    lineTo(visualX + 22.dp.toPx(), visualY)
                    lineTo(visualX + 15.dp.toPx(), visualY + 3.dp.toPx())
                    close()
                },
                color = Color.Red
            )

            // Engine Flame
            if (flameScale > 0) {
                drawPath(
                    path = Path().apply {
                        moveTo(visualX - 15.dp.toPx(), visualY - 4.dp.toPx())
                        lineTo(visualX - (15 + 20 * flameScale).dp.toPx(), visualY)
                        lineTo(visualX - 15.dp.toPx(), visualY + 4.dp.toPx())
                        close()
                    },
                    brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Yellow, Color.Red))
                )
            }
        }
    }
}

@Composable
fun ExplosionVisual(targetX: Float, targetY: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "explosion")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(200), RepeatMode.Reverse),
        label = "scale"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val vX = (targetX / 50000f) * w
        val vY = h - (targetY / 3000f) * (h * 0.8f) - 60.dp.toPx()

        drawCircle(
            brush = Brush.radialGradient(listOf(Color.Yellow, Color.Red, Color.Transparent)),
            radius = 40.dp.toPx() * scale,
            center = Offset(vX, vY)
        )
    }
}

@Composable
fun ConnectionLine(cDist: Float, cHeight: Float, tDist: Float, tHeight: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // Updated Scale to 50000f
        val p1 = Offset((cDist/50000f)*w, h - (cHeight/3000f)*(h*0.8f) - 60.dp.toPx())
        val p2 = Offset((tDist/50000f)*w, h - (tHeight/3000f)*(h*0.8f) - 60.dp.toPx())
        drawLine(
            color = Color.White.copy(alpha = 0.15f),
            start = p1, end = p2,
            strokeWidth = 1.5.dp.toPx()
        )
    }
}
