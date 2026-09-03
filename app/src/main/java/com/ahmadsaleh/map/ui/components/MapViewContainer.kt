@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

package com.ahmadsaleh.map.ui.components

import com.ahmadsaleh.map.MainActivity
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.location.Location
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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import android.os.Build
import android.widget.Toast
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ahmadsaleh.map.ui.viewmodel.CannonViewModel
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
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import androidx.compose.ui.platform.LocalConfiguration
import com.ahmadsaleh.map.ui.viewmodel.RouteInfo
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
import android.os.Looper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.ahmadsaleh.map.data.model.*
import com.ahmadsaleh.map.domain.calculator.UtmConverter
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.extension.localization.localizeLabels
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import androidx.core.graphics.createBitmap
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.android.gestures.StandardScaleGestureDetector
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.plugin.gestures.OnScaleListener

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(MapboxExperimental::class)
@Composable
fun MapViewContainer(
    viewModel: CannonViewModel,
    mapViewportState: MapViewportState,
    locationPermissionGranted: Boolean,
    allRoutes: List<RouteInfo> = emptyList(),
    selectedRouteIndex: Int = 0,
    onSelectRoute: (Int) -> Unit = {},
    destinationPoint: Point? = null,
    destinationLabel: String = "",
    isInPipMode: Boolean = false,
    onEnableGps: () -> Unit = {},
    onClearRoute: () -> Unit = {},
    onReroute: (Point, Point) -> Unit = { _, _ -> },
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val routeGeometry = if (allRoutes.isNotEmpty()) allRoutes[selectedRouteIndex.coerceIn(0, allRoutes.lastIndex)].geometry else null

    // تعريف الأشكال والرموز للعلامات الجديدة
    // سيتم توليد أهداف وعلامات بشكل ديناميكي داخل الحلقة لضمان تحديث الأسماء

    val destinationBitmap = rememberDestinationPinBitmap(
        label = destinationLabel.ifBlank { "الوجهة المطلوبة" }
    )

    val carTopBitmap = rememberIconBitmap(Icons.Default.KeyboardArrowUp, Color.White)
    val carBodyBitmap = rememberIconBitmap(Icons.Default.Navigation, Color(0xFF007AFF))
    val carShadowBitmap = rememberIconBitmap(Icons.Default.Navigation, Color.Black.copy(alpha = 0.2f))

    // توليد ملصقات متميزة ومخصصة لكل مسار (الرئيسي والبديل) بتنسيق عربي فصيح متناسق
    val routeLabels = allRoutes.mapIndexed { index, route ->
        val timeStr = formatDurationArabic(route.durationMinutes)
        val distStr = String.format(Locale.US, "%.1f كم", route.distanceKm)
        val isSelected = index == selectedRouteIndex
        val summaryStr = if (route.summary.isNotBlank() && !route.summary.startsWith("مسار")) route.summary else null

        val labelText = when {
            isSelected && summaryStr != null -> "الرئيسي ($summaryStr)  •  $timeStr  •  $distStr"
            isSelected -> "الرئيسي  •  $timeStr  •  $distStr"
            summaryStr != null -> "بديل ($summaryStr)  •  $timeStr  •  $distStr"
            else -> "بديل ${index + 1}  •  $timeStr  •  $distStr"
        }

        val bgColor = if (isSelected) Color(0xFF004AAD) else Color(0xFF1E293B)
        rememberTextBitmap(labelText, bgColor)
    }

    val configuration = LocalConfiguration.current
    val screenHeightPx = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx() }

    val context = LocalContext.current

    // استماع للتحديثات فائقة السرعة للموقع والسرعة اللحظية المباشرة (150ms-300ms)
    DisposableEffect(locationPermissionGranted) {
        if (!locationPermissionGranted) return@DisposableEffect onDispose {}

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 300L)
            .setMinUpdateIntervalMillis(150L)
            .setMinUpdateDistanceMeters(0.0f)
            .setWaitForAccurateLocation(false)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    viewModel.processLocationUpdate(location)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (_: SecurityException) {
            // تجاهل الاستثناء في حال تغير صلاحيات النظام
        }

        onDispose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    var userDrivingZoom by remember { mutableDoubleStateOf(13.8) }
    var isNavLocked by remember { mutableStateOf(value = false) }
    var lastInteractionTime by remember { mutableLongStateOf(0L) }

    val lockNavigation: () -> Unit = {
        if (locationPermissionGranted) {
            // قراءة الزوم المباشر من الكاميرا إذا كان المستخدم قد عدله، للحفاظ عليه كما هو
            mapViewportState.cameraState?.zoom?.let { currentZoom ->
                if (currentZoom > 0) userDrivingZoom = currentZoom
            }

            viewModel.getLastLocation()?.let { (lat, lon) ->
                mapViewportState.setCameraOptions {
                    center(Point.fromLngLat(lon, lat))
                    zoom(userDrivingZoom)
                }
            }

            mapViewportState.transitionToFollowPuckState(
                followPuckViewportStateOptions = FollowPuckViewportStateOptions.Builder()
                    .bearing(FollowPuckViewportStateBearing.SyncWithLocationPuck)
                    // وضع السيارة في الأسفل 30% وإتاحة 70% لرؤية مسافة أكبر من الطريق
                    .padding(EdgeInsets(screenHeightPx.toDouble() * 0.70, 0.0, 0.0, 0.0))
                    .zoom(userDrivingZoom) // الحفاظ الكامل والمطلق على الزوم الذي حدده المستخدم بنفسه
                    .pitch(0.0)
                    .build()
            )
        }
    }

    val activity = remember(context) {
        var c = context
        while (c is android.content.ContextWrapper) {
            if (c is MainActivity) return@remember c
            c = c.baseContext
        }
        null
    }

    LaunchedEffect(isNavLocked) {
        viewModel.onDrivingModeToggled(isNavLocked)
        activity?.updatePipParams(isNavLocked)
        if (!isNavLocked) {
            onClearRoute()
        }
    }

    LaunchedEffect(lastInteractionTime, isNavLocked) {
        if (isNavLocked && (lastInteractionTime > 0)) {
            delay(4.seconds)
            mapViewportState.cameraState?.zoom?.let { currentZoom ->
                if (currentZoom > 0) userDrivingZoom = currentZoom
            }
            lockNavigation()
            lastInteractionTime = 0L
        }
    }

    LaunchedEffect(locationPermissionGranted, allRoutes, isNavLocked) {
        if (locationPermissionGranted) {
            if (isNavLocked) {
                lockNavigation()
            } else if (destinationPoint == null) {
                mapViewportState.transitionToFollowPuckState()
            }
        }
    }

    var lastOffRouteAlertTime by remember { mutableLongStateOf(0L) }
    var isOffRouteActiveState by remember { mutableStateOf(false) }

    // فحص وتنبيه الانحراف والخروج عن المسار أثناء القيادة مع إعادة الحساب والاهتزاز
    LaunchedEffect(isNavLocked, viewModel.getLastLocation(), routeGeometry) {
        if (isNavLocked && viewModel.isOffRouteAlertEnabled && routeGeometry != null) {
            val currentLoc = viewModel.getLastLocation()
            if (currentLoc != null) {
                val driverPoint = Point.fromLngLat(currentLoc.second, currentLoc.first)
                val points = routeGeometry.coordinates()

                if (points.isNotEmpty()) {
                    var minDistanceMeters = Double.MAX_VALUE
                    for (p in points) {
                        val distArray = FloatArray(1)
                        Location.distanceBetween(
                            driverPoint.latitude(), driverPoint.longitude(),
                            p.latitude(), p.longitude(),
                            distArray
                        )
                        val d = distArray[0].toDouble()
                        if (d < minDistanceMeters) {
                            minDistanceMeters = d
                        }
                    }

                    // إذا خرجت السيارة عن المسار بأكثر من 45 متراً
                    if (minDistanceMeters > 45.0) {
                        val now = System.currentTimeMillis()
                        if (now - lastOffRouteAlertTime > 12000L) {
                            lastOffRouteAlertTime = now
                            isOffRouteActiveState = true

                            // 1. اهتزاز تنبيهي
                            try {
                                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val vm = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                                    vm?.defaultVibrator
                                } else {
                                    @Suppress("DEPRECATION")
                                    context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                }
                                if (vibrator?.hasVibrator() == true) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(400L, 255))
                                    } else {
                                        @Suppress("DEPRECATION")
                                        vibrator.vibrate(400L)
                                    }
                                }
                            } catch (_: Exception) {}

                            // 2. إشعار تحذيري
                            Toast.makeText(context, "⚠️ انحراف عن المسار! جارٍ إعادة الحساب والرجوع للمسار الرئيسي...", Toast.LENGTH_SHORT).show()

                            // 3. إعادة حساب المسار تلقائياً من الموقع الجديد
                            destinationPoint?.let { dest ->
                                onReroute(driverPoint, dest)
                            }
                        }
                    } else {
                        isOffRouteActiveState = false
                    }
                }
            }
        } else {
            isOffRouteActiveState = false
        }
    }

    // حركة التردد والوميض للانعطاف القريب القادم
    val infiniteTransition = rememberInfiniteTransition(label = "TurnPulse")
    val turnPulseOpacity by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    // حساب قطاع الانعطاف القريب القادم على المسار (ضمن مسافة 15 إلى 150 متراً قبل الانعطاف)
    val upcomingTurnSegment = remember(routeGeometry, viewModel.getLastLocation(), isNavLocked) {
        if (isNavLocked && routeGeometry != null) {
            val currentLoc = viewModel.getLastLocation()
            val points = routeGeometry.coordinates()
            if (currentLoc != null && points.size >= 3) {
                val driverPoint = Point.fromLngLat(currentLoc.second, currentLoc.first)

                var closestIdx = 0
                var minDist = Double.MAX_VALUE
                for (i in points.indices) {
                    val distArray = FloatArray(1)
                    Location.distanceBetween(driverPoint.latitude(), driverPoint.longitude(), points[i].latitude(), points[i].longitude(), distArray)
                    if (distArray[0] < minDist) {
                        minDist = distArray[0].toDouble()
                        closestIdx = i
                    }
                }

                var turnIdx = -1
                for (k in (closestIdx + 1) until (points.size - 1)) {
                    val pPrev = points[k - 1]
                    val pCurr = points[k]
                    val pNext = points[k + 1]

                    val b1 = calculateBearing(pPrev, pCurr)
                    val b2 = calculateBearing(pCurr, pNext)
                    val angleDiff = kotlin.math.abs(b2 - b1)
                    val normalizedAngle = if (angleDiff > 180.0) 360.0 - angleDiff else angleDiff

                    if (normalizedAngle > 35.0) {
                        val distToTurn = FloatArray(1)
                        Location.distanceBetween(driverPoint.latitude(), driverPoint.longitude(), pCurr.latitude(), pCurr.longitude(), distToTurn)
                        if (distToTurn[0] in 15.0f..150.0f) {
                            turnIdx = k
                            break
                        }
                    }
                }

                if (turnIdx != -1) {
                    val startTurn = (turnIdx - 1).coerceAtLeast(0)
                    val endTurn = (turnIdx + 1).coerceAtMost(points.lastIndex)
                    points.subList(startTurn, endTurn + 1)
                } else null
            } else null
        } else null
    }

    Box(modifier = modifier.fillMaxSize()) {
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
            style = { MapStyle(style = "mapbox://styles/mapbox/satellite-streets-v12") },
            compass = {
                if (!isInPipMode) {
                    Compass(
                        alignment = Alignment.TopStart,
                        contentPadding = PaddingValues(top = 48.dp, start = 16.dp),
                        fadeWhenFacingNorth = false
                    )
                }
            },
            scaleBar = {},
            logo = {},
            attribution = {},
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
                    override fun onMoveBegin(detector: MoveGestureDetector) {
                        if (isNavLocked) {
                            lastInteractionTime = System.currentTimeMillis()
                            mapViewportState.cameraState?.zoom?.let { z -> if (z > 0) userDrivingZoom = z }
                        }
                    }
                    override fun onMove(detector: MoveGestureDetector): Boolean {
                        if (isNavLocked) {
                            lastInteractionTime = System.currentTimeMillis()
                        }
                        return false
                    }
                    override fun onMoveEnd(detector: MoveGestureDetector) {
                        if (isNavLocked) {
                            lastInteractionTime = System.currentTimeMillis()
                            mapViewportState.cameraState?.zoom?.let { z -> if (z > 0) userDrivingZoom = z }
                        }
                    }
                })

                mapView.gestures.addOnScaleListener(object : OnScaleListener {
                    override fun onScaleBegin(detector: StandardScaleGestureDetector) {
                        if (isNavLocked) {
                            lastInteractionTime = System.currentTimeMillis()
                        }
                    }
                    override fun onScale(detector: StandardScaleGestureDetector) {
                        if (isNavLocked) {
                            lastInteractionTime = System.currentTimeMillis()
                            mapViewportState.cameraState?.zoom?.let { z -> if (z > 0) userDrivingZoom = z }
                        }
                    }
                    override fun onScaleEnd(detector: StandardScaleGestureDetector) {
                        if (isNavLocked) {
                            lastInteractionTime = System.currentTimeMillis()
                            mapViewportState.cameraState?.zoom?.let { z -> if (z > 0) userDrivingZoom = z }
                        }
                    }
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
                            scaleExpression = Expression.literal(1.3).toString()
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
                val isLoadingElev = cannon.id in viewModel.elevationLoadingIds
                val elevLabel = when {
                    isLoadingElev -> "⏳ ${cannon.name}"
                    cannon.elevation != 0.0 -> "${cannon.name}\n${String.format(Locale.US, "%.1f م", cannon.elevation)}"
                    else -> cannon.name
                }
                val marker = rememberMarkerBitmap(MarkerShape.CIRCLE, elevLabel, Color(0xFF2E7D32), Icons.Default.GpsFixed)
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
                    val isLoadingElev = target.id in viewModel.elevationLoadingIds
                    val elevLabel = when {
                        isLoadingElev -> "⏳ ${target.name}"
                        target.elevation != 0.0 -> "${target.name}\n${String.format(Locale.US, "%.1f م", target.elevation)}"
                        else -> target.name
                    }
                    val marker = rememberMarkerBitmap(MarkerShape.SQUARE, elevLabel, targetColor, Icons.Default.TrackChanges)

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

                        // 1. حدود سفلية ملونة داكنة لإبراز الخط بشكل دائر ومجوف كالمسار
                        PolylineAnnotation(points = linePoints) {
                            lineColor = Color.Black.copy(alpha = 0.65f)
                            lineWidth = 9.0
                            lineJoin = LineJoin.ROUND
                        }

                        // 2. الخط الرئيسي العريض والأنيق والمستدير
                        PolylineAnnotation(points = linePoints) {
                            lineColor = targetColor
                            lineWidth = 5.5
                            lineOpacity = 0.95
                            lineJoin = LineJoin.ROUND
                        }

                        // 3. حساب وعرض البيانات على الخط بدقة متناهية
                        val result = viewModel.getTargetResult(target)
                        if (result != null) {
                            val midLng = (start.longitude() + end.longitude()) / 2.0
                            val midLat = (start.latitude() + end.latitude()) / 2.0
                            val midPoint = Point.fromLngLat(midLng, midLat)

                            val labelText = String.format(Locale.US, "%.0f مليم  -  %.1f م", result.azimuthMils6000, result.distance)
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
                    val isLoadingElev = ref.id in viewModel.elevationLoadingIds
                    val elevLabel = when {
                        isLoadingElev -> "⏳ ${ref.name}"
                        ref.elevation != 0.0 -> "${ref.name}\n${String.format(Locale.US, "%.1f م", ref.elevation)}"
                        else -> ref.name
                    }
                    val marker = rememberMarkerBitmap(MarkerShape.TRIANGLE, elevLabel, refColor)

                    PointAnnotation(point = Point.fromLngLat(ref.geoPoint.longitude, ref.geoPoint.latitude)) {
                        interactionsState.onLongClicked { viewModel.openEditDialog(ref); true }
                        iconImage = IconImage(marker)
                        iconAnchor = IconAnchor.CENTER
                        iconSize = 1.0
                    }

                    // إضافة خط واصل لنقطة العلام مستدير وعريض كالمسار
                    viewModel.cannonPos?.let { cannon ->
                        val start = Point.fromLngLat(cannon.geoPoint.longitude, cannon.geoPoint.latitude)
                        val end = Point.fromLngLat(ref.geoPoint.longitude, ref.geoPoint.latitude)
                        val linePoints = listOf(start, end)

                        // حدود سفلية داكنة
                        PolylineAnnotation(points = linePoints) {
                            lineColor = Color.Black.copy(alpha = 0.55f)
                            lineWidth = 8.0
                            lineJoin = LineJoin.ROUND
                        }

                        // الخط الأساسي
                        PolylineAnnotation(points = linePoints) {
                            lineColor = refColor
                            lineWidth = 4.5
                            lineOpacity = 0.9
                            lineJoin = LineJoin.ROUND
                        }

                        val result = viewModel.getRefResult(ref)
                        if (result != null) {
                            val midLng = (start.longitude() + end.longitude()) / 2.0
                            val midLat = (start.latitude() + end.latitude()) / 2.0

                            val labelText = String.format(Locale.US, "%.0f مليم  -  %.1f م", result.azimuthMils6000, result.distance)
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

            // 1. أولاً: رسم كافة الطرق الفرعية والبديلة بعرض أقل (6.0) ولون رمادي فولاذي متميز شبه معتم (0.88)
            allRoutes.forEachIndexed { index, route ->
                val isSelected = index == selectedRouteIndex
                val points = route.geometry.coordinates()

                if (!isSelected) {
                    // أ) حد داكن سفلي لتمييز الطريق الفرعي بعرض أقل (11.0)
                    PolylineAnnotation(points = points) {
                        interactionsState.onClicked {
                            onSelectRoute(index)
                            true
                        }
                        lineColor = Color(0xFF0F172A) // فحمي/كحلي داكن
                        lineWidth = 11.0
                        lineOpacity = 0.90
                        lineJoin = LineJoin.ROUND
                    }

                    // ب) جسم الطريق الفرعي بلون رمادي/فولاذي ملاحي متميز وشبه معتم بعرض أقل (6.0)
                    PolylineAnnotation(points = points) {
                        interactionsState.onClicked {
                            onSelectRoute(index)
                            true
                        }
                        lineColor = Color(0xFF64748B) // رمادي فولاذي ملاحي أنيق ومختلف كلياً عن الأزرق
                        lineWidth = 6.0
                        lineOpacity = 0.88 // أقل شفافية بقليل ليكون واضحاً ومشاهد مع التضاريس
                        lineJoin = LineJoin.ROUND
                    }
                }
            }

            // 2. ثانياً: رسم المسار الأساسي الرئيسي المختار بتباين عالي جداً وعريض (10.0 / 16.0) فوق الطرق الأخرى
            routeGeometry?.let { lineString ->
                val points = lineString.coordinates()
                val currentLoc = viewModel.getLastLocation()

                // تقسيم المسار إلى: قسم مقنوع مقطوع (خلف السائق) وقسم متبقي (أمام السائق) كأنظمة Google Maps
                val (traversedPoints, remainingPoints) = remember(lineString, currentLoc, isNavLocked) {
                    if (isNavLocked && currentLoc != null && points.size > 1) {
                        val driverPoint = Point.fromLngLat(currentLoc.second, currentLoc.first)

                        var minDistance = Double.MAX_VALUE
                        var closestIndex = 0

                        for (i in points.indices) {
                            val results = FloatArray(1)
                            Location.distanceBetween(
                                driverPoint.latitude(), driverPoint.longitude(),
                                points[i].latitude(), points[i].longitude(),
                                results
                            )
                            val dist = results[0].toDouble()
                            if (dist < minDistance) {
                                minDistance = dist
                                closestIndex = i
                            }
                        }

                        val traversed = points.subList(0, (closestIndex + 1).coerceAtMost(points.size)).toMutableList()
                        traversed.add(driverPoint)

                        val remaining = mutableListOf<Point>()
                        remaining.add(driverPoint)
                        remaining.addAll(points.subList(closestIndex.coerceAtLeast(0), points.size))

                        Pair(traversed, remaining)
                    } else {
                        Pair(emptyList<Point>(), points)
                    }
                }

                if (traversedPoints.size > 1) {
                    // أ) المسار المقطوع المتروك خلف السائق (شفاف رمادي/فولاذي خافت كأنظمة Google Maps)
                    PolylineAnnotation(points = traversedPoints) {
                        lineColor = Color(0xFF64748B)
                        lineWidth = 7.0
                        lineOpacity = 0.45
                        lineJoin = LineJoin.ROUND
                    }
                }

                if (remainingPoints.size > 1) {
                    // ب) الحد السفلي الداكن الحاد للمسار المتبقي أمام السائق
                    PolylineAnnotation(points = remainingPoints) {
                        lineColor = Color(0xFF001428)
                        lineWidth = 16.0
                        lineOpacity = 1.0
                        lineJoin = LineJoin.ROUND
                    }

                    // جـ) جسم المسار المتبقي الزاهي والتفاعلي المضيء أمام السائق (أزرق ملكي زاهٍ)
                    PolylineAnnotation(points = remainingPoints) {
                        lineColor = Color(0xFF0284C7)
                        lineWidth = 10.0
                        lineOpacity = 1.0
                        lineJoin = LineJoin.ROUND
                    }
                }
            }

            // 3. ثالثاً: رسم وميض تنبيه الانعطاف القريب المضيء (Pulsing Turn Warning Cue) قبل 15م - 150م من الانعطاف
            upcomingTurnSegment?.let { turnPoints ->
                if (turnPoints.size > 1) {
                    PolylineAnnotation(points = turnPoints) {
                        lineColor = Color(0xFF00E5FF) // أزرق سياني متألق ومضيء
                        lineWidth = 14.0
                        lineOpacity = turnPulseOpacity.toDouble()
                        lineJoin = LineJoin.ROUND
                    }
                }
            }

            // 3. ثالثاً: وضع ملصقات الطرق الفرعية والبديلة مقربة من موقع السائق والمفارق
            allRoutes.forEachIndexed { index, route ->
                val isSelected = index == selectedRouteIndex
                val points = route.geometry.coordinates()

                if (!isSelected && points.size > 5) {
                    val labelIndex = (points.size * 0.25).toInt().coerceAtLeast(1)
                    PointAnnotation(point = points[labelIndex]) {
                        interactionsState.onClicked {
                            onSelectRoute(index)
                            true
                        }
                        iconImage = IconImage(routeLabels[index])
                        iconAnchor = IconAnchor.CENTER
                        iconSize = 0.95
                    }
                }
            }

            // 4. رابعاً: وضع ملصق المسار الرئيسي المختار في أقرب موضع لموقع السائق المباشر (12% من بداية المسار)
            routeGeometry?.let {
                val points = it.coordinates()
                if (points.size > 5) {
                    val labelIndex = (points.size * 0.12).toInt().coerceAtLeast(1)
                    val validIndex = selectedRouteIndex.coerceIn(0, routeLabels.lastIndex)
                    PointAnnotation(point = points[labelIndex]) {
                        iconImage = IconImage(routeLabels[validIndex])
                        iconAnchor = IconAnchor.CENTER
                        iconSize = 1.05
                    }
                }
            }

            destinationPoint?.let {
                PointAnnotation(point = it) {
                    iconImage = IconImage(destinationBitmap)
                    iconAnchor = IconAnchor.BOTTOM
                    iconSize = 1.0
                }
            }
        }
        if (!isInPipMode) {
            CardinalDirectionsOverlay()

            if (viewModel.showEditDialog) {
                UnifiedEditDialog(
                    viewModel = viewModel,
                    onDismiss = { viewModel.showEditDialog = false }
                )
            }

            // زر تتبع القيادة وقفل الملاحة الموحد + لوحة عرض السرعة اللحظية المباشرة أسفله مباشرةً
            Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 48.dp, end = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. زر تتبع القيادة وقفل الملاحة
                    Surface(
                        onClick = {
                            onEnableGps()
                            isNavLocked = !isNavLocked
                            if (!isNavLocked) {
                                onClearRoute()
                            }
                        },
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = if (isNavLocked) Color(0xFF0A84FF) else Color(0xFF0D131D).copy(alpha = 0.85f),
                        border = BorderStroke(
                            1.5.dp,
                            if (isNavLocked) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.35f)
                        ),
                        tonalElevation = 8.dp,
                        shadowElevation = 6.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = "تتبع القيادة",
                                tint = if (isNavLocked) Color.White else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // 2. عداد السرعة والوقت والمسافة للوصول (يظهر حصراً وفقط عند تفعيل وضع القيادة isNavLocked = true)
                    AnimatedVisibility(
                        visible = isNavLocked,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(modifier = Modifier.height(8.dp))
                            RealtimeSpeedometerWidget(
                                viewModel = viewModel,
                                isNavLocked = isNavLocked,
                                locationPermissionGranted = locationPermissionGranted,
                                allRoutes = allRoutes,
                                selectedRouteIndex = selectedRouteIndex,
                                destinationPoint = destinationPoint
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CardinalDirectionsOverlay() {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("N", Modifier.align(Alignment.TopCenter).padding(top = 48.dp), color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
        Text("S", Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp), color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
        Text("E", Modifier.align(Alignment.CenterEnd), color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
        Text("W", Modifier.align(Alignment.CenterStart), color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
    }
}

data class EtaInfo(
    val totalDistanceKm: Double,
    val coveredDistanceKm: Double,
    val remainingDistanceKm: Double,
    val totalDurationMinutes: Int,
    val remainingMinutesInstant: Int?,
    val remainingMinutesAverage: Int?,
    val arrivalTimeInstant: String,
    val arrivalTimeAverage: String,
    val durationTextInstant: String,
    val durationTextAverage: String,
    val driverAverageSpeedKmh: Float
)

fun calculateLiveEta(
    currentSpeedKmh: Float,
    averageMovingSpeedKmh: Float,
    coveredDistanceKm: Double,
    allRoutes: List<RouteInfo>,
    selectedRouteIndex: Int,
    destinationPoint: Point?,
    currentLat: Double?,
    currentLon: Double?
): EtaInfo? {
    var routeTotalDistanceKm: Double
    var routeTotalDurationMinutes: Int

    val hasRoute = allRoutes.isNotEmpty() && selectedRouteIndex in allRoutes.indices

    if (hasRoute) {
        val route = allRoutes[selectedRouteIndex.coerceIn(0, allRoutes.lastIndex)]
        routeTotalDistanceKm = route.distanceKm
        routeTotalDurationMinutes = route.durationMinutes
    } else if (destinationPoint != null && currentLat != null && currentLon != null) {
        val results = FloatArray(1)
        Location.distanceBetween(
            currentLat, currentLon,
            destinationPoint.latitude(), destinationPoint.longitude(),
            results
        )
        val remKm = results[0] / 1000.0
        routeTotalDistanceKm = remKm + coveredDistanceKm
        routeTotalDurationMinutes = ((routeTotalDistanceKm / 45.0) * 60.0).roundToInt().coerceAtLeast(1)
    } else {
        return null
    }

    val remainingDistanceKm = (routeTotalDistanceKm - coveredDistanceKm).coerceAtLeast(0.0)

    if (remainingDistanceKm <= 0.05) {
        return EtaInfo(
            totalDistanceKm = routeTotalDistanceKm,
            coveredDistanceKm = routeTotalDistanceKm,
            remainingDistanceKm = 0.0,
            totalDurationMinutes = routeTotalDurationMinutes,
            remainingMinutesInstant = 0,
            remainingMinutesAverage = 0,
            arrivalTimeInstant = "وصلت للهدف",
            arrivalTimeAverage = "تم الوصول",
            durationTextInstant = "تم الوصول",
            durationTextAverage = "تم الوصول",
            driverAverageSpeedKmh = averageMovingSpeedKmh
        )
    }

    val timeFormat = SimpleDateFormat("hh:mm a", Locale("ar"))

    // 1. حساب توقيت السرعة اللحظية المباشرة (Instant Velocity ETA)
    val (durationInstantText, arrivalInstantText, minInstant) = if (currentSpeedKmh >= 3.0f) {
        val durMin = ((remainingDistanceKm / currentSpeedKmh.toDouble()) * 60.0).roundToInt().coerceAtLeast(1)
        val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, durMin) }
        Triple(formatDurationArabic(durMin), timeFormat.format(cal.time), durMin)
    } else {
        Triple("--", "--", null)
    }

    // 2. حساب توقيت معدل حركة القيادة للسائق (Moving Average Speed ETA)
    val (durationAvgText, arrivalAvgText, minAverage) = if (averageMovingSpeedKmh >= 5.0f) {
        val durMin = ((remainingDistanceKm / averageMovingSpeedKmh.toDouble()) * 60.0).roundToInt().coerceAtLeast(1)
        val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, durMin) }
        Triple(formatDurationArabic(durMin), timeFormat.format(cal.time), durMin)
    } else {
        Triple("--", "--", null)
    }

    return EtaInfo(
        totalDistanceKm = routeTotalDistanceKm,
        coveredDistanceKm = coveredDistanceKm,
        remainingDistanceKm = remainingDistanceKm,
        totalDurationMinutes = routeTotalDurationMinutes,
        remainingMinutesInstant = minInstant,
        remainingMinutesAverage = minAverage,
        arrivalTimeInstant = arrivalInstantText,
        arrivalTimeAverage = arrivalAvgText,
        durationTextInstant = durationInstantText,
        durationTextAverage = durationAvgText,
        driverAverageSpeedKmh = averageMovingSpeedKmh
    )
}

/**
 * عداد السرعة اللحظية والمدة المتوقعة للوصول المصمم خصيصاً لأنظمة الملاحة القيادية (Automotive HUD)
 * يحسب السرعة الحية محسوبة بالمسافة خلال وحدة الزمن بدقة فائقة ويحسب المدة الزمنية المتبقية بدقة متناهية
 */
@Composable
fun RealtimeSpeedometerWidget(
    viewModel: CannonViewModel,
    isNavLocked: Boolean,
    locationPermissionGranted: Boolean,
    allRoutes: List<RouteInfo> = emptyList(),
    selectedRouteIndex: Int = 0,
    destinationPoint: Point? = null
) {
    val speedDisplay by animateIntAsState(
        targetValue = viewModel.currentSpeedDisplay,
        animationSpec = tween(durationMillis = 200),
        label = "SpeedAnimation"
    )

    val isMoving = viewModel.isMoving
    val currentLoc = viewModel.getLastLocation()

    // حساب المدة المتوقعة الحقيقية ووقت الوصول بدقة متناهية عبر المعدل الذكي
    val etaInfo = remember(
        viewModel.currentSpeedKmh,
        viewModel.averageMovingSpeedKmh,
        viewModel.activeSessionDistanceKm,
        allRoutes,
        selectedRouteIndex,
        destinationPoint,
        currentLoc
    ) {
        calculateLiveEta(
            currentSpeedKmh = viewModel.currentSpeedKmh,
            averageMovingSpeedKmh = viewModel.averageMovingSpeedKmh,
            coveredDistanceKm = viewModel.activeSessionDistanceKm,
            allRoutes = allRoutes,
            selectedRouteIndex = selectedRouteIndex,
            destinationPoint = destinationPoint,
            currentLat = currentLoc?.first,
            currentLon = currentLoc?.second
        )
    }

    // الألوان التفاعلية حسب السرعة ووضع القيادة
    // الألوان التفاعلية حسب السرعة ووضع القيادة
    val speedColor = when {
        !locationPermissionGranted -> Color.Gray
        speedDisplay == 0 -> Color.White.copy(alpha = 0.88f)
        speedDisplay < 80 -> Color(0xFF00E5FF) // أزرق سياني متألق
        speedDisplay < 120 -> Color(0xFFFFD600) // أصفر/ذهبي
        else -> Color(0xFFFF3B30) // أحمر تنبيه
    }

    val borderColor = when {
        isNavLocked -> Color(0xFF00E5FF)
        isMoving -> Color(0xFF30D158)
        else -> Color.White.copy(alpha = 0.25f)
    }

    var showDetails by remember { mutableStateOf(false) }
    var liveElapsedSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isNavLocked, viewModel.isDrivingSessionActive) {
        while (isNavLocked && viewModel.isDrivingSessionActive) {
            liveElapsedSeconds = viewModel.activeSessionElapsedTimeSeconds
            delay(1.seconds)
        }
    }

    val widgetWidth by animateDpAsState(
        targetValue = if (showDetails) 96.dp else 78.dp,
        animationSpec = tween(durationMillis = 250),
        label = "WidgetWidth"
    )

    Surface(
        onClick = { showDetails = !showDetails },
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF090D16).copy(alpha = 0.90f),
        border = BorderStroke(
            width = if (isNavLocked || isMoving) 1.5.dp else 1.0.dp,
            color = borderColor
        ),
        shadowElevation = 8.dp,
        modifier = Modifier.width(widgetWidth)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. مؤشر النبض المباشر للـ GPS
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 1.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(
                            color = if (locationPermissionGranted) {
                                if (isMoving) Color(0xFF30D158) else Color(0xFF00E5FF)
                            } else Color.Red,
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = if (isNavLocked) "حي" else "GPS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                )
            }

            // 2. عرض السرعة الحقيقية اللحظية المباشرة
            Text(
                text = "$speedDisplay",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = speedColor
                )
            )

            Text(
                text = "كم/س",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f)
                )
            )

            // 3. في الوضع المدمج الافتراضي: عرض المسافة الكلية للمسار وسهم التوسيع
            etaInfo?.let { eta ->
                if (!showDetails) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 3.dp),
                        color = Color.White.copy(alpha = 0.15f)
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f كم", eta.totalDistanceKm),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF00E5FF)
                    )
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "توسيع التفاصيل",
                        tint = Color.Gray,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // 4. في الوضع الممتد التفاعلي (عند النقر للتوسيع): عرض كافة البيانات الشاملة والدقيقة
            AnimatedVisibility(
                visible = showDetails,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    )

                    // أ) متوسط السرعة وأقصى سرعة
                    val avgSpeed = viewModel.averageMovingSpeedKmh.roundToInt()
                    val topSpeed = viewModel.topSpeedKmh.roundToInt()

                    Text(
                        text = "المعدل: $avgSpeed كم/س",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF30D158)
                    )
                    Text(
                        text = "الأقصى: $topSpeed كم/س",
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD600)
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 3.dp),
                        color = Color.White.copy(alpha = 0.10f)
                    )

                    // ب) تفاصيل المسافات الثلاثة: الكلية، المقطوعة، المتبقية
                    etaInfo?.let { eta ->
                        Text(
                            text = "الكلية: " + String.format(Locale.US, "%.1f كم", eta.totalDistanceKm),
                            fontSize = 7.5.sp,
                            color = Color.LightGray
                        )
                        Text(
                            text = "المقطوع: " + String.format(Locale.US, "%.1f كم", eta.coveredDistanceKm),
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF30D158)
                        )
                        Text(
                            text = "المتبقي: " + String.format(Locale.US, "%.1f كم", eta.remainingDistanceKm),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00E5FF)
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 3.dp),
                            color = Color.White.copy(alpha = 0.10f)
                        )

                        // جـ) مؤقت الرحلة التفاعلي المباشر بالثواني والدقائق والساعات
                        if (liveElapsedSeconds > 0) {
                            Text(
                                text = "مؤقت الرحلة",
                                fontSize = 7.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = formatElapsedTimer(liveElapsedSeconds),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }

                        // د) توقيت الوصول حسب معدل السرعة والتوقيت اللحظي
                        Text(
                            text = "الوصول (بالمعدل)",
                            fontSize = 7.sp,
                            color = Color(0xFF30D158)
                        )
                        Text(
                            text = eta.arrivalTimeAverage,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF30D158)
                        )
                        if (eta.durationTextAverage != "--") {
                            Text(
                                text = "(${eta.durationTextAverage})",
                                fontSize = 7.5.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "الوصول (اللحظي)",
                            fontSize = 7.sp,
                            color = Color(0xFFFFD600)
                        )
                        Text(
                            text = eta.arrivalTimeInstant,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD600)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ExpandLess,
                        contentDescription = "طوي التفاصيل",
                        tint = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp).size(12.dp)
                    )
                }
            }
        }
    }
}

fun formatElapsedTimer(totalSeconds: Long): String {
    val hrs = totalSeconds / 3600L
    val mins = (totalSeconds % 3600L) / 60L
    val secs = totalSeconds % 60L
    return if (hrs > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}

/**
 * أيقونة وشارة الهدف الملاحي (Destination Target Pin) المصممة بدقة وجمالية متناهية ثلاثية الأبعاد
 */
@Composable
fun rememberDestinationPinBitmap(
    label: String = "الوجهة المطلوبة",
    color: Color = Color(0xFFFF2D55) // أحمر ياقوتي زاهٍ ومتألق
): Bitmap {
    val density = LocalDensity.current
    val iconPainter = rememberVectorPainter(Icons.Default.LocationOn)

    return remember(label, color) {
        val sizePx = with(density) { 54.dp.toPx() }
        val padding = with(density) { 4.dp.toPx() }
        val fontSize = with(density) { 10.5.sp.toPx() }

        val textPaint = Paint().apply {
            isAntiAlias = true
            textSize = fontSize
            this.color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(8f, 0f, 2f, android.graphics.Color.BLACK)
        }

        val textBounds = Rect()
        textPaint.getTextBounds(label, 0, label.length, textBounds)

        val pinWidth = sizePx * 0.72f
        val pinHeight = sizePx * 0.90f
        val labelHeight = textBounds.height() + padding * 3f

        val width = maxOf(pinWidth + padding * 6f, textBounds.width().toFloat() + padding * 8f)
        val height = pinHeight + labelHeight + padding * 2f

        val bitmap = createBitmap(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1))
        val canvas = android.graphics.Canvas(bitmap)
        val composeCanvas = Canvas(bitmap.asImageBitmap())

        val centerX = width / 2f

        // 1. رسم الظل الخارجي المضيء تحت الدبوس
        val shadowPaint = Paint().apply {
            isAntiAlias = true
            this.color = android.graphics.Color.argb(120, 0, 0, 0)
            style = Paint.Style.FILL
            maskFilter = android.graphics.BlurMaskFilter(12f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(centerX, pinHeight - 4f, pinWidth * 0.28f, shadowPaint)

        // 2. رسم الدبوس الملاحي الرئيسي (Drop Pin Path)
        val pinPath = Path().apply {
            moveTo(centerX, pinHeight)
            cubicTo(
                centerX - pinWidth * 0.45f, pinHeight * 0.60f,
                centerX - pinWidth * 0.50f, 0f,
                centerX, 0f
            )
            cubicTo(
                centerX + pinWidth * 0.50f, 0f,
                centerX + pinWidth * 0.45f, pinHeight * 0.60f,
                centerX, pinHeight
            )
            close()
        }

        val pinPaint = Paint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
            style = Paint.Style.FILL
        }

        val borderPaint = Paint().apply {
            isAntiAlias = true
            this.color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        canvas.drawPath(pinPath, pinPaint)
        canvas.drawPath(pinPath, borderPaint)

        // 3. رسم الحلقة البيضاء المركزية والأيقونة
        val innerCircleRadius = pinWidth * 0.28f
        val innerCircleY = pinHeight * 0.38f

        val whiteCirclePaint = Paint().apply {
            isAntiAlias = true
            this.color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, innerCircleY, innerCircleRadius, whiteCirclePaint)

        // رسم أيقونة الملاحة الداخلية
        val iconSize = innerCircleRadius * 1.5f
        val drawScope = CanvasDrawScope()
        drawScope.draw(
            density = density,
            layoutDirection = LayoutDirection.Ltr,
            canvas = composeCanvas,
            size = Size(width, height)
        ) {
            translate(left = centerX - iconSize / 2f, top = innerCircleY - iconSize / 2f) {
                with(iconPainter) {
                    draw(size = Size(iconSize, iconSize), colorFilter = ColorFilter.tint(color))
                }
            }
        }

        // 4. رسم ملصق الكبسولة الملاحي تحت الدبوس باسم المنطقة
        val labelBgPaint = Paint().apply {
            isAntiAlias = true
            this.color = android.graphics.Color.argb(230, 15, 23, 42) // كحلي زجاجي راقٍ
            style = Paint.Style.FILL
        }

        val labelBorderPaint = Paint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        val labelRect = RectF(
            centerX - (textBounds.width() / 2f) - padding * 3f,
            pinHeight + padding,
            centerX + (textBounds.width() / 2f) + padding * 3f,
            pinHeight + labelHeight
        )
        val cornerRadius = 12f
        canvas.drawRoundRect(labelRect, cornerRadius, cornerRadius, labelBgPaint)
        canvas.drawRoundRect(labelRect, cornerRadius, cornerRadius, labelBorderPaint)

        canvas.drawText(
            label,
            centerX,
            labelRect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f),
            textPaint
        )

        bitmap
    }
}

/**
 * وظيفة لتحويل النص إلى Bitmap لعرضه كملصق أنيق وصغير (Tooltip) على الخريطة
 */
@Composable
fun rememberTextBitmap(text: String, bgColor: Color): Bitmap {
    val density = LocalDensity.current
    return remember(text, bgColor) {
        val paint = Paint().apply {
            isAntiAlias = true
            textSize = with(density) { 11.sp.toPx() } // حجم أنيق وصغير جداً ومتناسق
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)

        val padX = with(density) { 6.dp.toPx() }
        val padY = with(density) { 3.dp.toPx() }
        val width = bounds.width() + (padX * 2f)
        val height = bounds.height() + (padY * 2.5f)

        val bitmap = createBitmap(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1))
        val canvas = android.graphics.Canvas(bitmap)

        val bgPaint = Paint().apply {
            isAntiAlias = true
            color = bgColor.toArgb()
            style = Paint.Style.FILL
        }

        val borderPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = with(density) { 1.2.dp.toPx() }
        }

        val rect = RectF(0f, 0f, width, height)
        val cornerRadius = with(density) { 8.dp.toPx() }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        canvas.drawText(text, width / 2f, (height / 2f) - ((paint.descent() + paint.ascent()) / 2f), paint)

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

    // تحميل القيم الأولية عند فتح الحوار
    LaunchedEffect(point, type) {
        if (point != null) {
            when (point) {
                is CannonPosition -> {
                    name = point.name
                    description = point.description
                    elevation = if (point.elevation != 0.0) String.format(Locale.US, "%.1f", point.elevation) else ""
                    lat = String.format(Locale.US, "%.7f", point.geoPoint.latitude)
                    lon = String.format(Locale.US, "%.7f", point.geoPoint.longitude)
                    easting = String.format(Locale.US, "%.2f", point.utmPoint.easting)
                    northing = String.format(Locale.US, "%.2f", point.utmPoint.northing)
                }
                is TargetPosition -> {
                    name = point.name
                    description = point.description
                    elevation = if (point.elevation != 0.0) String.format(Locale.US, "%.1f", point.elevation) else ""
                    lat = String.format(Locale.US, "%.7f", point.geoPoint.latitude)
                    lon = String.format(Locale.US, "%.7f", point.geoPoint.longitude)
                    easting = String.format(Locale.US, "%.2f", point.utmPoint.easting)
                    northing = String.format(Locale.US, "%.2f", point.utmPoint.northing)
                }
                is ReferencePoint -> {
                    name = point.name
                    description = point.description
                    elevation = if (point.elevation != 0.0) String.format(Locale.US, "%.1f", point.elevation) else ""
                    lat = String.format(Locale.US, "%.7f", point.geoPoint.latitude)
                    lon = String.format(Locale.US, "%.7f", point.geoPoint.longitude)
                    easting = String.format(Locale.US, "%.2f", point.utmPoint.easting)
                    northing = String.format(Locale.US, "%.2f", point.utmPoint.northing)
                }
            }
        }
    }

    // تحديث حقل الارتفاع تلقائياً عندما ينتهي جلب الارتفاع في الخلفية
    val pointId = when (point) {
        is CannonPosition -> point.id
        is TargetPosition -> point.id
        is ReferencePoint -> point.id
        else -> null
    }
    val isLoadingElev = pointId != null && pointId in viewModel.elevationLoadingIds

    LaunchedEffect(isLoadingElev) {
        // عندما ينتهي التحميل، حدّث القيمة المعروضة
        if (!isLoadingElev && pointId != null) {
            val freshElev: Double? = when (point) {
                is CannonPosition -> viewModel.cannonPos?.elevation
                is TargetPosition -> viewModel.targets.firstOrNull { it.id == pointId }?.elevation
                else -> viewModel.referencePoints.firstOrNull { it.id == pointId }?.elevation
            }
            if (freshElev != null && freshElev != 0.0) {
                elevation = String.format(Locale.US, "%.1f", freshElev)
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

                // حقل الارتفاع — يظهر لجميع أنواع النقاط
                OutlinedTextField(
                    value = elevation,
                    onValueChange = { elevation = it },
                    label = {
                        if (isLoadingElev)
                            Text("⏳ جارٍ جلب الارتفاع...")
                        else
                            Text("الارتفاع عن سطح البحر (م)")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    leadingIcon = { Icon(Icons.Default.Height, null) },
                    enabled = !isLoadingElev,
                    placeholder = { Text("يُجلب تلقائياً...") }
                )

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

        val textPaint = Paint().apply {
            isAntiAlias = true
            textSize = fontSize
            this.color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(6f, 0f, 2f, android.graphics.Color.BLACK)
        }

        val textBounds = Rect()
        if (name.isNotEmpty()) {
            textPaint.getTextBounds(name, 0, name.length, textBounds)
        }

        val shapeSize = sizePx * 0.7f
        val textExtra = if (name.isNotEmpty()) textBounds.height() + padding * 3f else 0f

        val width = maxOf(shapeSize + padding * 4f, textBounds.width().toFloat() + padding * 6f)
        val height = shapeSize + textExtra * 2f

        val bitmap = createBitmap(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1))
        val canvas = android.graphics.Canvas(bitmap)
        val composeCanvas = Canvas(bitmap.asImageBitmap())

        val centerX = width / 2f
        val shapeCenterY = height / 2f

        val paint = Paint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
            style = Paint.Style.FILL
        }

        // Draw Shape with shadow/border centered exactly at (centerX, shapeCenterY)
        val borderPaint = Paint().apply {
            isAntiAlias = true
            this.color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        when (shape) {
            MarkerShape.SQUARE -> {
                val rect = RectF(
                    centerX - shapeSize / 2f,
                    shapeCenterY - shapeSize / 2f,
                    centerX + shapeSize / 2f,
                    shapeCenterY + shapeSize / 2f
                )
                canvas.drawRoundRect(rect, 12f, 12f, paint)
                canvas.drawRoundRect(rect, 12f, 12f, borderPaint)
            }
            MarkerShape.CIRCLE -> {
                canvas.drawCircle(centerX, shapeCenterY, shapeSize / 2f, paint)
                canvas.drawCircle(centerX, shapeCenterY, shapeSize / 2f, borderPaint)
            }
            MarkerShape.TRIANGLE -> {
                val path = Path()
                path.moveTo(centerX, shapeCenterY - shapeSize / 2f)
                path.lineTo(centerX - shapeSize / 2f, shapeCenterY + shapeSize / 2f)
                path.lineTo(centerX + shapeSize / 2f, shapeCenterY + shapeSize / 2f)
                path.close()
                canvas.drawPath(path, paint)
                canvas.drawPath(path, borderPaint)
            }
        }

        // Draw Center Dot at exact center
        val dotPaint = Paint().apply {
            isAntiAlias = true
            this.color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, shapeCenterY, shapeSize * 0.12f, dotPaint)
        
        // Draw Icon if exists at exact center
        painter?.let { p ->
            val iconSize = shapeSize * 0.55f
            val drawScope = CanvasDrawScope()
            drawScope.draw(
                density = density,
                layoutDirection = LayoutDirection.Ltr,
                canvas = composeCanvas,
                size = Size(width, height)
            ) {
                translate(left = centerX - iconSize / 2f, top = shapeCenterY - iconSize / 2f) {
                    with(p) {
                        draw(size = Size(iconSize, iconSize), colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.9f)))
                    }
                }
            }
        }
        
        // Draw Label symmetric below the shape
        if (name.isNotEmpty()) {
            canvas.drawText(name, centerX, shapeCenterY + shapeSize / 2f + textBounds.height() + padding, textPaint)
        }
        
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

fun formatDurationArabic(totalMinutes: Int): String {
    if (totalMinutes <= 0) return "أقل من دقيقة"
    
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60

    return when {
        hours == 0 -> {
            when (minutes) {
                1 -> "دقيقة واحدة"
                2 -> "دقيقتان"
                in 3..10 -> "$minutes دقائق"
                else -> "$minutes دقيقة"
            }
        }
        hours == 1 -> {
            when (minutes) {
                0 -> "ساعة واحدة"
                1 -> "ساعة ودقيقة"
                2 -> "ساعة ودقيقتان"
                in 3..10 -> "ساعة و $minutes دقائق"
                else -> "ساعة و $minutes دقيقة"
            }
        }
        hours == 2 -> {
            when (minutes) {
                0 -> "ساعتان"
                1 -> "ساعتان ودقيقة"
                2 -> "ساعتان ودقيقتان"
                in 3..10 -> "ساعتان و $minutes دقائق"
                else -> "ساعتان و $minutes دقيقة"
            }
        }
        hours in 3..10 -> {
            when (minutes) {
                0 -> "$hours ساعات"
                1 -> "$hours ساعات ودقيقة"
                2 -> "$hours ساعات ودقيقتان"
                in 3..10 -> "$hours ساعات و $minutes دقائق"
                else -> "$hours ساعات و $minutes دقيقة"
            }
        }
        else -> {
            when (minutes) {
                0 -> "$hours ساعة"
                1 -> "$hours ساعة ودقيقة"
                2 -> "$hours ساعة ودقيقتان"
                in 3..10 -> "$hours ساعة و $minutes دقائق"
                else -> "$hours ساعة و $minutes دقيقة"
            }
        }
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

fun calculateBearing(p1: Point, p2: Point): Double {
    val lat1 = Math.toRadians(p1.latitude())
    val lon1 = Math.toRadians(p1.longitude())
    val lat2 = Math.toRadians(p2.latitude())
    val lon2 = Math.toRadians(p2.longitude())
    val dLon = lon2 - lon1
    val y = kotlin.math.sin(dLon) * kotlin.math.cos(lat2)
    val x = kotlin.math.cos(lat1) * kotlin.math.sin(lat2) - kotlin.math.sin(lat1) * kotlin.math.cos(lat2) * kotlin.math.cos(dLon)
    return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360.0) % 360.0
}
