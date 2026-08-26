@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

package com.example.my_cannon.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.my_cannon.data.model.*
import com.example.my_cannon.domain.calculator.UtmConverter
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.extension.localization.localizeLabels
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
    
    // تعريف الأشكال والرموز للعلامات الجديدة
    // سيتم توليد أهداف وعلامات بشكل ديناميكي داخل الحلقة لضمان تحديث الأسماء
    
    val targetBitmapFallback = rememberIconBitmap(Icons.Default.TrackChanges, Color.Red)

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

    var isNavLocked by remember { mutableStateOf(value = false) }
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
        if (isNavLocked && (lastInteractionTime > 0)) {
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
                mapView.mapboxMap.getStyle { style ->
                    style.localizeLabels(Locale.forLanguageTag("ar"))
                }

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
                val marker = rememberMarkerBitmap(MarkerShape.CIRCLE, cannon.name, Color(0xFF2E7D32), Icons.Default.GpsFixed)
                PointAnnotation(point = Point.fromLngLat(cannon.geoPoint.longitude, cannon.geoPoint.latitude)) {
                    interactionsState.onLongClicked { viewModel.openEditDialog(cannon); true }
                    iconImage = IconImage(marker)
                    iconAnchor = IconAnchor.CENTER
                    iconSize = 1.0
                }
            }

            viewModel.targets.forEachIndexed { index, target ->
                key(target.id) {
                    val targetColor = getTargetColor(index)
                    val marker = rememberMarkerBitmap(MarkerShape.SQUARE, target.name, targetColor, Icons.Default.TrackChanges)
                    
                    PointAnnotation(point = Point.fromLngLat(target.geoPoint.longitude, target.geoPoint.latitude)) {
                        interactionsState.onLongClicked { viewModel.openEditDialog(target); true }
                        iconImage = IconImage(marker)
                        iconAnchor = IconAnchor.CENTER
                        iconSize = 1.0
                    }

                    viewModel.cannonPos?.let { cannon ->
                        val start = Point.fromLngLat(cannon.geoPoint.longitude, cannon.geoPoint.latitude)
                        val end = Point.fromLngLat(target.geoPoint.longitude, target.geoPoint.latitude)
                        val linePoints = listOf(start, end)
                        
                        // 1. رسم الخط المستقيم الاحترافي
                        PolylineAnnotation(points = linePoints) {
                            lineColor = targetColor
                            lineWidth = 3.5
                            lineOpacity = 0.9
                        }
                        
                        // 2. حساب وعرض البيانات على الخط بدقة متناهية
                        val result = viewModel.getTargetResult(target)
                        if (result != null) {
                            // وضع الملصق في منتصف الخط تماماً
                            val midLng = (start.longitude() + end.longitude()) / 2.0
                            val midLat = (start.latitude() + end.latitude()) / 2.0
                            val midPoint = Point.fromLngLat(midLng, midLat)
                            
                            val labelText = String.format(Locale.US, "%.0f مل  -  %.1f م", result.azimuthMils6000, result.distance)
                            val labelBitmap = rememberTextBitmap(labelText, targetColor.copy(alpha = 0.9f))
                            
                            PointAnnotation(point = midPoint) {
                                iconImage = IconImage(labelBitmap)
                                iconAnchor = IconAnchor.CENTER
                                iconSize = 0.9
                            }
                        }
                    }
                }
            }

            viewModel.referencePoints.forEach { ref ->
                key(ref.id) {
                    val refColor = Color(0xFF1976D2)
                    val marker = rememberMarkerBitmap(MarkerShape.TRIANGLE, ref.name, refColor)
                    
                    PointAnnotation(point = Point.fromLngLat(ref.geoPoint.longitude, ref.geoPoint.latitude)) {
                        interactionsState.onLongClicked { viewModel.openEditDialog(ref); true }
                        iconImage = IconImage(marker)
                        iconAnchor = IconAnchor.CENTER
                        iconSize = 1.0
                    }

                    // إضافة خط واصل لنقطة العلام أيضاً كما طلب المستخدم
                    viewModel.cannonPos?.let { cannon ->
                        val start = Point.fromLngLat(cannon.geoPoint.longitude, cannon.geoPoint.latitude)
                        val end = Point.fromLngLat(ref.geoPoint.longitude, ref.geoPoint.latitude)
                        val linePoints = listOf(start, end)

                        PolylineAnnotation(points = linePoints) {
                            lineColor = refColor
                            lineWidth = 2.5
                            lineOpacity = 0.7
                        }

                        val result = viewModel.getRefResult(ref)
                        if (result != null) {
                            val midLng = (start.longitude() + end.longitude()) / 2.0
                            val midLat = (start.latitude() + end.latitude()) / 2.0
                            
                            val labelText = String.format(Locale.US, "%.0f مل  -  %.1f م", result.azimuthMils6000, result.distance)
                            val labelBitmap = rememberTextBitmap(labelText, refColor.copy(alpha = 0.8f))
                            
                            PointAnnotation(point = Point.fromLngLat(midLng, midLat)) {
                                iconImage = IconImage(labelBitmap)
                                iconAnchor = IconAnchor.CENTER
                                iconSize = 0.8
                            }
                        }
                    }
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
                    iconImage = IconImage(targetBitmapFallback)
                    iconAnchor = IconAnchor.CENTER
                    iconSize = 1.5
                }
            }
        }
        CardinalDirectionsOverlay()

        if (viewModel.showEditDialog) {
            UnifiedEditDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.showEditDialog = false }
            )
        }

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
fun UnifiedEditDialog(
    viewModel: CannonViewModel,
    onDismiss: () -> Unit
) {
    val point = viewModel.pointToEdit
    val type = viewModel.manualAddType
    
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var elevation by remember { mutableStateOf("") }
    
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    
    var easting by remember { mutableStateOf("") }
    var northing by remember { mutableStateOf("") }

    LaunchedEffect(point, type) {
        if (point != null) {
            when (point) {
                is CannonPosition -> {
                    name = point.name
                    description = point.description
                    elevation = point.elevation.toString()
                    lat = String.format(Locale.US, "%.7f", point.geoPoint.latitude)
                    lon = String.format(Locale.US, "%.7f", point.geoPoint.longitude)
                    easting = String.format(Locale.US, "%.2f", point.utmPoint.easting)
                    northing = String.format(Locale.US, "%.2f", point.utmPoint.northing)
                }
                is TargetPosition -> {
                    name = point.name
                    description = point.description
                    elevation = point.elevation.toString()
                    lat = String.format(Locale.US, "%.7f", point.geoPoint.latitude)
                    lon = String.format(Locale.US, "%.7f", point.geoPoint.longitude)
                    easting = String.format(Locale.US, "%.2f", point.utmPoint.easting)
                    northing = String.format(Locale.US, "%.2f", point.utmPoint.northing)
                }
                is ReferencePoint -> {
                    name = point.name
                    description = point.description
                    elevation = "0"
                    lat = String.format(Locale.US, "%.7f", point.geoPoint.latitude)
                    lon = String.format(Locale.US, "%.7f", point.geoPoint.longitude)
                    easting = String.format(Locale.US, "%.2f", point.utmPoint.easting)
                    northing = String.format(Locale.US, "%.2f", point.utmPoint.northing)
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Text(
                    text = if (point != null) "تعديل النقطة" else "إضافة نقطة يدوياً",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Name & Description Section
                EditSectionTitle("المعلومات الأساسية", Icons.Default.Info)
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("الاسم") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("الوصف") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2
                )

                if (point !is ReferencePoint && type != PointType.REFERENCE) {
                    OutlinedTextField(
                        value = elevation,
                        onValueChange = { elevation = it },
                        label = { Text("الارتفاع (م)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        leadingIcon = { Icon(Icons.Default.Height, null) }
                    )
                }

                // Coordinates Section
                EditSectionTitle("الإحداثيات (Geo)", Icons.Default.LocationOn)
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lat,
                        onValueChange = { lat = it },
                        label = { Text("خط العرض") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = lon,
                        onValueChange = { lon = it },
                        label = { Text("خط الطول") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                EditSectionTitle("الإحداثيات (UTM)", Icons.Default.LocationOn)
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = easting,
                        onValueChange = { easting = it },
                        label = { Text("Easting (X)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = northing,
                        onValueChange = { northing = it },
                        label = { Text("Northing (Y)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (point != null) {
                        IconButton(
                            onClick = { viewModel.deletePoint(point) },
                            modifier = Modifier
                                .height(50.dp)
                                .weight(0.5f),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف")
                        }
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.7f).height(50.dp)
                    ) {
                        Text("إلغاء", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    
                    Button(
                        onClick = {
                            val finalLat = lat.toDoubleOrNull() ?: 0.0
                            val finalLon = lon.toDoubleOrNull() ?: 0.0
                            val finalGeo = GeoPoint(finalLat, finalLon)
                            
                            val finalE = easting.toDoubleOrNull() ?: 0.0
                            val finalN = northing.toDoubleOrNull() ?: 0.0
                            val finalUtm = if (finalE != 0.0) UtmPoint(finalE, finalN, 37, 'N') else UtmConverter.fromGeoToUtm(finalGeo)
                            
                            viewModel.updatePointFull(
                                point = point,
                                type = type,
                                name = name,
                                description = description,
                                elevation = elevation.toDoubleOrNull() ?: 0.0,
                                geo = finalGeo,
                                utm = finalUtm
                            )
                        },
                        modifier = Modifier.weight(1.3f).height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("حفظ", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun EditSectionTitle(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

enum class MarkerShape { SQUARE, CIRCLE, TRIANGLE }

@Composable
fun rememberMarkerBitmap(
    shape: MarkerShape,
    name: String,
    color: Color,
    icon: ImageVector? = null
): Bitmap {
    val density = LocalDensity.current
    val painter = icon?.let { rememberVectorPainter(it) }
    
    return remember(shape, name, color, icon) {
        val sizePx = with(density) { 50.dp.toPx() }
        val padding = with(density) { 4.dp.toPx() }
        val fontSize = with(density) { 11.sp.toPx() }
        
        val textPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = fontSize
            this.color = android.graphics.Color.WHITE
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(6f, 0f, 2f, android.graphics.Color.BLACK)
        }
        
        val textBounds = android.graphics.Rect()
        textPaint.getTextBounds(name, 0, name.length, textBounds)
        
        val shapeSize = sizePx * 0.7f
        val width = maxOf(shapeSize, textBounds.width().toFloat() + padding * 4)
        val height = shapeSize + textBounds.height() + padding * 4
        
        val bitmap = createBitmap(width.toInt(), height.toInt())
        val canvas = android.graphics.Canvas(bitmap)
        val composeCanvas = Canvas(bitmap.asImageBitmap())
        
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
            style = android.graphics.Paint.Style.FILL
        }
        
        val centerX = width / 2f
        val shapeCenterY = if (shape == MarkerShape.TRIANGLE) shapeSize * 0.66f else shapeSize / 2f
        
        // Draw Shape with shadow/border
        val borderPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            this.color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 4f
        }

        when (shape) {
            MarkerShape.SQUARE -> {
                val rect = android.graphics.RectF(centerX - shapeSize/2, 0f, centerX + shapeSize/2, shapeSize)
                canvas.drawRoundRect(rect, 12f, 12f, paint)
                canvas.drawRoundRect(rect, 12f, 12f, borderPaint)
            }
            MarkerShape.CIRCLE -> {
                canvas.drawCircle(centerX, shapeCenterY, shapeSize / 2, paint)
                canvas.drawCircle(centerX, shapeCenterY, shapeSize / 2, borderPaint)
            }
            MarkerShape.TRIANGLE -> {
                val path = android.graphics.Path()
                path.moveTo(centerX, 0f)
                path.lineTo(centerX - shapeSize/2, shapeSize)
                path.lineTo(centerX + shapeSize/2, shapeSize)
                path.close()
                canvas.drawPath(path, paint)
                canvas.drawPath(path, borderPaint)
            }
        }
        
        // Draw Center Dot
        val dotPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            this.color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawCircle(centerX, shapeCenterY, shapeSize * 0.12f, dotPaint)
        
        // Draw Icon if exists
        painter?.let { p ->
            val iconSize = shapeSize * 0.55f
            val drawScope = CanvasDrawScope()
            drawScope.draw(
                density = density,
                layoutDirection = LayoutDirection.Ltr,
                canvas = composeCanvas,
                size = Size(width, height)
            ) {
                translate(left = centerX - iconSize / 2, top = shapeCenterY - iconSize / 2) {
                    with(p) {
                        draw(size = Size(iconSize, iconSize), colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.9f)))
                    }
                }
            }
        }
        
        // Draw Label
        canvas.drawText(name, centerX, shapeSize + textBounds.height() + padding * 2, textPaint)
        
        bitmap
    }
}

fun getTargetColor(index: Int): Color {
    val colors = listOf(
        Color(0xFFD32F2F), // Red
        Color(0xFFF57C00), // Orange
        Color(0xFFFBC02D), // Yellow
        Color(0xFF388E3C), // Green
        Color(0xFF1976D2), // Blue
        Color(0xFF7B1FA2), // Purple
        Color(0xFFC2185B), // Pink
        Color(0xFF0097A7)  // Cyan
    )
    return colors[index % colors.size]
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
