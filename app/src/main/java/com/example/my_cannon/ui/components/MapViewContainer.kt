@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

package com.example.my_cannon.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.my_cannon.ui.viewmodel.CannonViewModel
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.MapViewportState
import com.mapbox.maps.extension.compose.annotation.IconImage
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import androidx.compose.ui.platform.LocalConfiguration
import com.example.my_cannon.ui.viewmodel.RouteInfo
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.KeyboardArrowUp
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.plugin.LocationPuck2D
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateOptions
import com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateBearing
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.gestures.OnMoveListener
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import androidx.core.graphics.createBitmap

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(MapboxExperimental::class)
@Composable
fun MapViewContainer(
    viewModel: CannonViewModel,
    mapViewportState: MapViewportState,
    locationPermissionGranted: Boolean,
    allRoutes: List<RouteInfo> = emptyList(),
    selectedRouteIndex: Int = 0,
    destinationPoint: Point? = null,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val routeGeometry = if (allRoutes.isNotEmpty()) allRoutes[selectedRouteIndex].geometry else null
    
    val cannonBitmap = rememberIconBitmap(Icons.Default.GpsFixed, Color.Green)
    val targetBitmap = rememberIconBitmap(Icons.Default.TrackChanges, Color.Red)
    val refBitmap = rememberIconBitmap(Icons.Default.Flag, Color.Blue)

    val carTopBitmap = rememberIconBitmap(Icons.Default.KeyboardArrowUp, Color.White)
    val carBodyBitmap = rememberIconBitmap(Icons.Default.Navigation, Color(0xFF007AFF))
    val carShadowBitmap = rememberIconBitmap(Icons.Default.Navigation, Color.Black.copy(alpha = 0.2f))

    // توليد ملصقات الوقت لكافة المسارات مسبقاً لضمان الأداء
    val routeLabels = allRoutes.map { route ->
        val labelText = " د ${route.durationMinutes}   -   " + String.format(Locale.US, "%.1f كم", route.distanceKm)
        rememberTextBitmap(labelText, Color(0xFF004AAD))
    }

    val configuration = LocalConfiguration.current
    val screenHeightPx = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx() }

    var isNavLocked by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(0L) }

    val lockNavigation: () -> Unit = {
        if (locationPermissionGranted && allRoutes.isNotEmpty()) {
            mapViewportState.transitionToFollowPuckState(
                followPuckViewportStateOptions = FollowPuckViewportStateOptions.Builder()
                    .bearing(FollowPuckViewportStateBearing.SyncWithLocationPuck)
                    // الرؤية من الأعلى مباشرة (Flat) مع بقاء السيارة في الأسفل (10%)
                    .padding(EdgeInsets(screenHeightPx.toDouble() * 0.8, 0.0, 0.0, 0.0))
                    .zoom(15.5) // تقليل الزوم لعرض مساحة أكبر من الطريق
                    .pitch(0.0) 
                    .build()
            )
        }
    }

    LaunchedEffect(lastInteractionTime, isNavLocked) {
        if (isNavLocked && lastInteractionTime > 0) {
            delay(4.seconds)
            lockNavigation()
            lastInteractionTime = 0L
        }
    }

    LaunchedEffect(locationPermissionGranted, allRoutes, isNavLocked) {
        if (locationPermissionGranted) {
            if (allRoutes.isNotEmpty() && isNavLocked) {
                lockNavigation()
            } else if (!isNavLocked) {
                mapViewportState.transitionToFollowPuckState()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
            style = { MapStyle(style = "mapbox://styles/mapbox/satellite-streets-v12") },
            compass = {
                Compass(
                    alignment = Alignment.TopStart,
                    contentPadding = PaddingValues(top = 48.dp, start = 16.dp),
                    fadeWhenFacingNorth = false
                )
            },
            onMapClickListener = { point ->
                viewModel.updatePointFromMap(point.latitude(), point.longitude())
                true
            }
        ) {
            MapEffect(locationPermissionGranted, isNavLocked) { mapView ->
                mapView.gestures.addOnMoveListener(object : OnMoveListener {
                    override fun onMoveBegin(detector: com.mapbox.android.gestures.MoveGestureDetector) {
                        if (isNavLocked) lastInteractionTime = System.currentTimeMillis()
                    }
                    override fun onMove(detector: com.mapbox.android.gestures.MoveGestureDetector) = false
                    override fun onMoveEnd(detector: com.mapbox.android.gestures.MoveGestureDetector) {}
                })

                mapView.location.updateSettings {
                    enabled = locationPermissionGranted
                    puckBearingEnabled = true
                    puckBearing = PuckBearing.COURSE
                    locationPuck = if (isNavLocked) {
                        LocationPuck2D(
                            bearingImage = ImageHolder.from(carBodyBitmap),
                            topImage = ImageHolder.from(carTopBitmap),
                            shadowImage = ImageHolder.from(carShadowBitmap),
                            // تصغير الحجم ليكون أنيقاً وغير مغطٍ للطريق (1.3 بدلاً من 2.5)
                            scaleExpression = com.mapbox.maps.extension.style.expressions.generated.Expression.literal(1.3).toString()
                        )
                    } else {
                        createDefault2DPuck(withBearing = true)
                    }
                    showAccuracyRing = !isNavLocked
                }
                
                var firstFix = true
                mapView.location.addOnIndicatorPositionChangedListener { point ->
                    if (firstFix && locationPermissionGranted) {
                        if (viewModel.getLastLocation() == null) {
                            mapViewportState.setCameraOptions {
                                center(point)
                                zoom(14.0)
                            }
                        }
                        firstFix = false
                    }
                    viewModel.saveLastLocation(point.latitude(), point.longitude())
                }
            }
            
            viewModel.cannonPos?.let { cannon ->
                PointAnnotation(point = Point.fromLngLat(cannon.geoPoint.longitude, cannon.geoPoint.latitude)) {
                    interactionsState.onLongClicked { viewModel.openEditDialog(cannon); true }
                    iconImage = IconImage(cannonBitmap)
                    iconAnchor = IconAnchor.CENTER
                    iconSize = 1.2
                }
            }

            viewModel.targets.forEach { target ->
                PointAnnotation(point = Point.fromLngLat(target.geoPoint.longitude, target.geoPoint.latitude)) {
                    interactionsState.onLongClicked { viewModel.openEditDialog(target); true }
                    iconImage = IconImage(targetBitmap)
                    iconAnchor = IconAnchor.CENTER
                    iconSize = 1.4
                }
                viewModel.cannonPos?.let { cannon ->
                    PolylineAnnotation(points = listOf(Point.fromLngLat(cannon.geoPoint.longitude, cannon.geoPoint.latitude), Point.fromLngLat(target.geoPoint.longitude, target.geoPoint.latitude))) {
                        lineColor = Color.Red
                        lineWidth = 3.0
                    }
                }
            }

            viewModel.referencePoints.forEach { ref ->
                PointAnnotation(point = Point.fromLngLat(ref.geoPoint.longitude, ref.geoPoint.latitude)) {
                    interactionsState.onLongClicked { viewModel.openEditDialog(ref); true }
                    iconImage = IconImage(refBitmap)
                    iconAnchor = IconAnchor.BOTTOM
                    iconSize = 1.0
                }
            }

            // رسم كافة المسارات (Google Maps Style)
            allRoutes.forEachIndexed { index, route ->
                val isSelected = index == selectedRouteIndex
                val points = route.geometry.coordinates()
                
                if (!isSelected) {
                    // مسارات بديلة (أزرق غامق شفاف)
                    PolylineAnnotation(points = points) {
                        lineColor = Color(0xFF1A5A99).copy(alpha = 0.4f)
                        lineWidth = 8.0
                    }
                    
                    // ملصق الوقت للمسارات البديلة (Tooltip)
                    if (points.size > 10) {
                        val labelIndex = (points.size * 0.6).toInt() // وضع الملصق في الـ 60% من المسار
                        PointAnnotation(point = points[labelIndex]) {
                            iconImage = IconImage(routeLabels[index])
                            iconAnchor = IconAnchor.CENTER
                            iconSize = 0.8
                        }
                    }
                }
            }

            // رسم المسار المختار (أزرق ملكي غامق) فوق المسارات الأخرى
            routeGeometry?.let {
                val points = it.coordinates()
                
                // 1. الحدود
                PolylineAnnotation(points = points) {
                    lineColor = Color(0xFF003366) // كحلي غامق جداً
                    lineWidth = 14.0
                    lineOpacity = 0.9
                }
                
                // 2. المسار الأساسي
                PolylineAnnotation(points = points) {
                    lineColor = Color(0xFF004AAD) // أزرق ملكي
                    lineWidth = 8.0
                    lineOpacity = 1.0
                }

                // ملصق الوقت للمسار المختار (يظهر بوضوح)
                if (points.size > 10) {
                    val labelIndex = (points.size * 0.4).toInt() // وضعه في الـ 40% من المسار لتمييزه
                    PointAnnotation(point = points[labelIndex]) {
                        iconImage = IconImage(routeLabels[selectedRouteIndex])
                        iconAnchor = IconAnchor.CENTER
                        iconSize = 1.1
                    }
                }
            }

            destinationPoint?.let {
                PointAnnotation(point = it) {
                    iconImage = IconImage(targetBitmap)
                    iconAnchor = IconAnchor.CENTER
                    iconSize = 1.5
                }
            }
        }
        CardinalDirectionsOverlay()

        // زر قفل الملاحة الاحترافي (يظهر عند وجود مسار)
        if (routeGeometry != null) {
            Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                FilledIconButton(
                    onClick = { isNavLocked = !isNavLocked },
                    modifier = Modifier
                        .align(Alignment.TopEnd) // وضعه في الجهة المقابلة للبوصلة (أعلى اليمين)
                        .padding(top = 48.dp, end = 16.dp) // نفس مستوى ارتفاع البوصلة
                        .size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isNavLocked) Color(0xFF0A84FF) else Color.Black.copy(alpha = 0.7f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation, // أيقونة السهم في كلتا الحالتين كما طلبت
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CardinalDirectionsOverlay() {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("N", Modifier.align(Alignment.TopCenter).padding(top = 40.dp), color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
        Text("S", Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp), color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
        Text("E", Modifier.align(Alignment.CenterEnd), color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
        Text("W", Modifier.align(Alignment.CenterStart), color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
    }
}

/**
 * وظيفة لتحويل النص إلى Bitmap لعرضه كملصق (Tooltip) على الخريطة
 */
@Composable
fun rememberTextBitmap(text: String, bgColor: Color): Bitmap {
    val density = LocalDensity.current
    return remember(text, bgColor) {
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = with(density) { 14.sp.toPx() }
            color = android.graphics.Color.WHITE
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        
        val bounds = android.graphics.Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        
        val padding = with(density) { 8.dp.toPx() }
        val width = bounds.width() + (padding * 2)
        val height = bounds.height() + (padding * 2)
        
        val bitmap = createBitmap(width.toInt(), height.toInt())
        val canvas = android.graphics.Canvas(bitmap)
        
        val bgPaint = android.graphics.Paint().apply {
            color = bgColor.toArgb()
            style = android.graphics.Paint.Style.FILL
        }
        
        val rect = android.graphics.RectF(0f, 0f, width, height)
        canvas.drawRoundRect(rect, padding, padding, bgPaint)
        canvas.drawText(text, width / 2, (height / 2) - ((paint.descent() + paint.ascent()) / 2), paint)
        
        bitmap
    }
}

@Composable
fun rememberIconBitmap(imageVector: ImageVector, color: Color): Bitmap {
    val density = LocalDensity.current
    val painter = rememberVectorPainter(imageVector)
    return remember(imageVector, color) {
        val size = 48.dp
        val px = with(density) { size.toPx() }.toInt()
        val imageBitmap = ImageBitmap(px, px)
        val canvas = Canvas(imageBitmap)
        val drawScope = CanvasDrawScope()
        drawScope.draw(density = density, layoutDirection = LayoutDirection.Ltr, canvas = canvas, size = Size(px.toFloat(), px.toFloat())) {
            with(painter) { draw(size = Size(px.toFloat(), px.toFloat()), colorFilter = ColorFilter.tint(color)) }
        }
        imageBitmap.asAndroidBitmap()
    }
}
