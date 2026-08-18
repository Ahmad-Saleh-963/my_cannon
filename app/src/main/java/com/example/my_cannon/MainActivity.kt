package com.example.my_cannon

import android.Manifest
import android.app.Activity
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.my_cannon.data.model.PointType
import java.util.Locale
import com.example.my_cannon.ui.components.CompassVisualizer
import com.example.my_cannon.ui.components.CoordinateInputDialog
import com.example.my_cannon.ui.components.MapViewContainer
import com.example.my_cannon.ui.components.ResultsDashboard
import com.example.my_cannon.ui.screens.TacticalGeometryScreen
import com.example.my_cannon.ui.screens.CannonSimulationScreen
import com.example.my_cannon.ui.theme.My_cannonTheme
import com.example.my_cannon.ui.viewmodel.CannonViewModel
import com.example.my_cannon.data.model.CannonPosition
import com.example.my_cannon.data.model.TargetPosition
import com.example.my_cannon.data.model.ReferencePoint
import com.example.my_cannon.ui.screens.OfflineMapsScreen
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.example.my_cannon.ui.viewmodel.MapOfflineViewModel
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // إبقاء الشاشة مضيئة دائماً أثناء عمل التطبيق
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // إخفاء أشرطة النظام (الحالة والتنقل) للحصول على تجربة كاملة الشاشة
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        enableEdgeToEdge()
        setContent {
            My_cannonTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: CannonViewModel = viewModel(), offlineViewModel: MapOfflineViewModel = viewModel()) {
    val context = LocalContext.current
    
    var destinationPoint by remember { mutableStateOf<com.mapbox.geojson.Point?>(null) }
    var isSearchBarVisible by remember { mutableStateOf(false) }

    val searchQuery by offlineViewModel.searchQuery.collectAsState()
    val searchResults by offlineViewModel.proResults.collectAsState() // استخدام النتائج الجديدة
    val isSearching by offlineViewModel.isSearching.collectAsState()
    val routeGeometry by offlineViewModel.currentRoute.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // مؤقت لإخفاء شريط البحث تلقائياً
    LaunchedEffect(isSearchBarVisible) {
        if (isSearchBarVisible) {
            delay(5.seconds)
            if (searchQuery.isEmpty()) isSearchBarVisible = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.initPrefs(context)
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    
    // حالة ظهور شريط التنقل
    var isBottomBarVisible by remember { mutableStateOf(false) }

    // مؤقت لإخفاء الشريط تلقائياً بعد 3 ثوانٍ
    LaunchedEffect(isBottomBarVisible) {
        if (isBottomBarVisible) {
            delay(3.seconds)
            isBottomBarVisible = false
        }
    }

    // Location Permission State
    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    // التعامل مع زر الرجوع في النظام لمنع إغلاق التطبيق فجأة
    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    // Hoisted Map State - initialized from cache if available
    val initialLoc = remember { viewModel.getLastLocation() }
    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            if (initialLoc != null) {
                center(Point.fromLngLat(initialLoc.second, initialLoc.first))
                zoom(14.0)
            } else {
                center(Point.fromLngLat(36.2765, 33.5138)) // Fallback
                zoom(12.0)
            }
        }
    }

    // حالة حوار تفعيل الـ GPS
    var showLocationDisabledDialog by remember { mutableStateOf(false) }
    var resolvableException by remember { mutableStateOf<ResolvableApiException?>(null) }

    val settingResultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // جلب الموقع والحفظ فور التفعيل
            fetchAndSaveLocation(context, viewModel, mapViewportState)
        }
    }

    fun checkAndEnableGPS() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500) // تحديث فائق السرعة كل نصف ثانية
            .setMinUpdateIntervalMillis(200) // السماح بتحديثات أسرع إذا توفرت
            .setWaitForAccurateLocation(true)
            .build()
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)
        
        val client: SettingsClient = LocationServices.getSettingsClient(context)
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            // إذا كان مفعل مسبقاً، ننتقل فوراً
            fetchAndSaveLocation(context, viewModel, mapViewportState)
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                resolvableException = exception
                showLocationDisabledDialog = true
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        
        if (locationPermissionGranted) {
            checkAndEnableGPS()
        }
    }

    // طلب صلاحية الإشعارات لأندرويد 13+ لضمان ظهور شريط تحميل الخرائط
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // سنقوم برسم الشريط يدوياً داخل الـ Box ليكون عائماً ومتحركاً
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            when (selectedTab) {
                0 -> {
                    // الخريطة تأخذ كامل الشاشة
                    MapViewContainer(
                        viewModel = viewModel,
                        mapViewportState = mapViewportState,
                        locationPermissionGranted = locationPermissionGranted,
                        routeGeometry = routeGeometry,
                        destinationPoint = destinationPoint,
                        modifier = Modifier.fillMaxSize()
                    )

                    // منطقة حساسة للسحب من الأعلى للأسفل لإظهار البحث
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .align(Alignment.TopCenter)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    if (dragAmount > 10) isSearchBarVisible = true
                                }
                            }
                    )

                    // شريط البحث العلوي التفاعلي
                    AnimatedVisibility(
                        visible = isSearchBarVisible,
                        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                        modifier = Modifier.align(Alignment.TopCenter)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 48.dp)
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                color = Color.Black.copy(alpha = 0.8f),
                                tonalElevation = 8.dp
                            ) {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { 
                                        val currentPoint = viewModel.getLastLocation()?.let { Point.fromLngLat(it.second, it.first) }
                                        offlineViewModel.onSearchQueryChanged(it, currentPoint) 
                                    },
                                    placeholder = { Text("بحث عن منطقة...", color = Color.Gray, fontSize = 14.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    leadingIcon = { 
                                        if (isSearching) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = Color.Cyan
                                            )
                                        } else {
                                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.Cyan)
                                        }
                                    },
                                    trailingIcon = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (searchQuery.isNotEmpty()) {
                                                IconButton(onClick = { 
                                                    offlineViewModel.onSearchQueryChanged("")
                                                    destinationPoint = null
                                                    offlineViewModel.clearRoute()
                                                }) {
                                                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray)
                                                }
                                            }
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(
                                        onSearch = {
                                            if (searchResults.isNotEmpty()) {
                                                val result = searchResults.first()
                                                destinationPoint = result.point
                                                offlineViewModel.onSearchQueryChanged(result.name)
                                                mapViewportState.setCameraOptions {
                                                    center(result.point)
                                                    zoom(14.0)
                                                }
                                                val currentLoc = viewModel.getLastLocation()
                                                val start = if (currentLoc != null) {
                                                    Point.fromLngLat(currentLoc.second, currentLoc.first)
                                                } else {
                                                    viewModel.cannonPos?.geoPoint?.let { 
                                                        Point.fromLngLat(it.longitude, it.latitude) 
                                                    } ?: Point.fromLngLat(36.2765, 33.5138)
                                                }
                                                offlineViewModel.calculateDrivingRoute(start, result.point)
                                                
                                                keyboardController?.hide()
                                                isSearchBarVisible = false
                                            }
                                        }
                                    )
                                )
                            }

                            // نتائج البحث
                            if (searchResults.isNotEmpty()) {
                                Card(
                                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.9f)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column {
                                        searchResults.forEach { result ->
                                            ListItem(
                                                headlineContent = { 
                                                    Text(result.name, color = Color.White, fontWeight = FontWeight.Bold) 
                                                },
                                                supportingContent = {
                                                    Column {
                                                        Text(result.province, color = Color.Cyan, fontSize = 10.sp)
                                                        Text(result.fullAddress, color = Color.Gray, fontSize = 11.sp, maxLines = 1)
                                                    }
                                                },
                                                leadingContent = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Red) },
                                                modifier = Modifier.combinedClickable(
                                                    onClick = {
                                                        destinationPoint = result.point
                                                        offlineViewModel.onSearchQueryChanged(result.name)
                                                        mapViewportState.setCameraOptions {
                                                            center(result.point)
                                                            zoom(14.0)
                                                        }
                                                        val currentLoc = viewModel.getLastLocation()
                                                        val start = if (currentLoc != null) {
                                                            Point.fromLngLat(currentLoc.second, currentLoc.first)
                                                        } else {
                                                            viewModel.cannonPos?.geoPoint?.let { 
                                                                Point.fromLngLat(it.longitude, it.latitude) 
                                                            } ?: Point.fromLngLat(36.2765, 33.5138)
                                                        }
                                                        offlineViewModel.calculateDrivingRoute(start, result.point)
                                                        isSearchBarVisible = false
                                                    }
                                                ),
                                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                            )
                                            HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    CannonSimulationScreen(onExit = { selectedTab = 0 })
                }
                4 -> {
                    OfflineMapsScreen(onBack = { selectedTab = 0 })
                }
                else -> {
                    Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        when (selectedTab) {
                            1 -> ResultsScreen(viewModel)
                            2 -> TacticalGeometryScreen(viewModel)
                        }
                    }
                }
            }

            // منطقة حساسة للسحب في الأسفل فقط
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp) // ارتفاع كافٍ لاستشعار السحب من الأسفل
                    .align(Alignment.BottomCenter)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount < -10) { // سحب للأعلى حصراً
                                isBottomBarVisible = true
                            }
                        }
                    }
            )

            // شريط التنقل العائم والمتحرك
            AnimatedVisibility(
                visible = isBottomBarVisible && selectedTab != 3,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 32.dp, vertical = 16.dp) // جعل الشريط يبدو ككبسولة طافية
                        .height(52.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    tonalElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val items = listOf(
                            Triple(0, Icons.Default.Map, "خريطة"),
                            Triple(1, Icons.Default.Calculate, "حساب"),
                            Triple(2, Icons.Default.Timeline, "تكتيك"),
                            Triple(3, Icons.Default.SportsEsports, "محاكي")
                        )

                        items.forEach { (index, icon, _) ->
                            val isSelected = selectedTab == index
                            IconButton(
                                onClick = { 
                                    selectedTab = index
                                    isBottomBarVisible = false // إخفاء بعد الاختيار
                                }
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = if (isSelected) {
                                        when(index) {
                                            0 -> Color.Green
                                            1 -> Color.Red
                                            2 -> Color.Blue
                                            else -> Color.Cyan
                                        }
                                    } else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(if (isSelected) 28.dp else 24.dp)
                                )
                            }
                        }
                    }
                }
            }

            // أزرار الخريطة والتحكم - استخدام safeDrawingPadding لضمان عدم التداخل مع النظام
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding() // هذا السحر يمنع التداخل مع الساعة وأزرار النظام
            ) {
                if (selectedTab == 0) {
                    // 1. زر GPS في أسفل اليسار
                    SmallFloatingActionButton(
                        onClick = {
                            if (locationPermissionGranted) {
                                checkAndEnableGPS()
                            } else {
                                launcher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 64.dp), // مسافة أقل لتناسب الشريط الجديد
                        containerColor = Color.Black.copy(alpha = 0.7f),
                        contentColor = if (locationPermissionGranted) Color.Cyan else Color.Gray,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "تحديد موقعي")
                    }

                    // 2. قائمة Speed Dial في أسفل اليمين
                    SpeedDialFab(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 64.dp), // مسافة أقل لتناسب الشريط الجديد
                        viewModel = viewModel,
                        currentType = viewModel.selectedPointType,
                        onNavigateToOffline = { selectedTab = 4 }
                    )
                }
            }
        }

        if (viewModel.showEditDialog) {
            val point = viewModel.pointToEdit
            if (point != null) {
                val (title, geo, utm) = when (point) {
                    is CannonPosition -> Triple("المربط", point.geoPoint, point.utmPoint)
                    is TargetPosition -> Triple(point.name, point.geoPoint, point.utmPoint)
                    is ReferencePoint -> Triple(point.name, point.geoPoint, point.utmPoint)
                    else -> Triple("النقطة", com.example.my_cannon.data.model.GeoPoint(0.0, 0.0), com.example.my_cannon.data.model.UtmPoint(0.0, 0.0))
                }

                CoordinateInputDialog(
                    title = title,
                    initialGeo = geo,
                    initialUtm = utm,
                    onConfirm = { newGeo, newUtm ->
                        viewModel.updatePointManually(newGeo, newUtm)
                    },
                    onDismiss = { viewModel.showEditDialog = false }
                )
            }
        }

        // حوار تنبيه خدمات الموقع معطلة
        if (showLocationDisabledDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showLocationDisabledDialog = false
                    resolvableException = null
                },
                title = { Text("خدمات الموقع معطلة") },
                text = { Text("يرجى تفعيل خدمات الموقع (GPS) لتتمكن من تحديد موقعك بدقة على الخريطة.") },
                confirmButton = {
                    Button(
                        onClick = {
                            val exception = resolvableException
                            if (exception != null) {
                                try {
                                    val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution.intentSender).build()
                                    settingResultLauncher.launch(intentSenderRequest)
                                } catch (e: IntentSender.SendIntentException) {
                                    e.printStackTrace()
                                }
                            }
                            showLocationDisabledDialog = false
                            resolvableException = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan, contentColor = Color.Black)
                    ) {
                        Text("موافق")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showLocationDisabledDialog = false
                        resolvableException = null
                    }) {
                        Text("إلغاء", color = Color.Gray)
                    }
                },
                containerColor = Color.DarkGray,
                titleContentColor = Color.White,
                textContentColor = Color.White
            )
        }
    }
}

/**
 * وظيفة لجلب الموقع الحالي وحفظه وتحريك الكاميرا إليه فوراً
 */
private fun fetchAndSaveLocation(
    context: android.content.Context, 
    viewModel: CannonViewModel, 
    mapViewportState: com.mapbox.maps.extension.compose.animation.viewport.MapViewportState
) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    // 1. حفظ الموقع في الكاش
                    viewModel.saveLastLocation(it.latitude, it.longitude)
                    
                    // 2. تحديث المربط تلقائياً إذا كان فارغاً
                    if (viewModel.cannonPos == null) {
                        viewModel.updatePointManually(com.example.my_cannon.data.model.GeoPoint(it.latitude, it.longitude))
                    }
                    
                    // 3. تحريك الكاميرا فوراً للموقع
                    mapViewportState.transitionToFollowPuckState()
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpeedDialFab(
    modifier: Modifier, 
    viewModel: CannonViewModel, 
    currentType: PointType,
    onNavigateToOffline: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 45f else 0f, label = "fab_rotation")
    
    // لون الزر حسب وضع الإضافة الحالي
    val silverColor = Color(0xFFC0C2C9)
    val fabColor = when (currentType) {
        PointType.CANNON -> Color.Green
        PointType.TARGET -> Color.Red
        PointType.REFERENCE -> Color.Blue
        PointType.NONE -> silverColor
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // خيارات القائمة (تظهر للأعلى)
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically() + slideInVertically { it / 2 },
            exit = fadeOut() + shrinkVertically() + slideOutVertically { it / 2 }
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // وضع الاستطلاع (لا شيء) - أعلى القائمة
                SpeedDialItem(
                    label = "استطلاع (لا شيء)",
                    icon = Icons.Default.Explore,
                    color = silverColor,
                    onClick = {
                        viewModel.selectedPointType = PointType.NONE
                        expanded = false
                    }
                )
                SpeedDialItem(
                    label = "المربط",
                    icon = Icons.Default.GpsFixed,
                    color = Color.Green,
                    onClick = {
                        viewModel.selectedPointType = PointType.CANNON
                        expanded = false
                    },
                    onLongClick = {
                        viewModel.openManualAddDialog(PointType.CANNON)
                        expanded = false
                    }
                )
                SpeedDialItem(
                    label = "الهدف",
                    icon = Icons.Default.TrackChanges,
                    color = Color.Red,
                    onClick = {
                        viewModel.selectedPointType = PointType.TARGET
                        expanded = false
                    },
                    onLongClick = {
                        viewModel.openManualAddDialog(PointType.TARGET)
                        expanded = false
                    }
                )
                SpeedDialItem(
                    label = "نقطة علام",
                    icon = Icons.Default.Flag,
                    color = Color.Blue,
                    onClick = {
                        viewModel.selectedPointType = PointType.REFERENCE
                        expanded = false
                    },
                    onLongClick = {
                        viewModel.openManualAddDialog(PointType.REFERENCE)
                        expanded = false
                    }
                )
                SpeedDialItem(
                    label = "خرائط أوفلاين",
                    icon = Icons.Default.Settings,
                    color = Color.Cyan,
                    onClick = {
                        onNavigateToOffline()
                        expanded = false
                    }
                )
                SpeedDialItem(
                    label = "مسح الكل",
                    icon = Icons.Default.DeleteSweep,
                    color = Color.DarkGray,
                    onClick = {
                        viewModel.clearPoints()
                        expanded = false
                    }
                )
            }
        }

        // الزر الرئيسي (+)
        FloatingActionButton(
            onClick = { expanded = !expanded },
            containerColor = fabColor,
            contentColor = Color.White,
            modifier = Modifier.rotate(rotation)
        ) {
            Icon(if (expanded) Icons.Default.Close else Icons.Default.Add, contentDescription = "فتح القائمة")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpeedDialItem(
    label: String, 
    icon: ImageVector, 
    color: Color, 
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.7f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        
        Surface(
            modifier = Modifier
                .size(44.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon, 
                    contentDescription = null, 
                    modifier = Modifier.size(24.dp),
                    tint = color
                )
            }
        }
    }
}

@Composable
fun ResultsScreen(viewModel: CannonViewModel) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().padding(8.dp).verticalScroll(scrollState)) {
        CompassVisualizer(viewModel.mainResult, modifier = Modifier.height(300.dp))
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        
        if (viewModel.targets.isNotEmpty()) {
            Text("الأهداف العسكرية المكتشفة", style = MaterialTheme.typography.titleLarge, color = Color.Red, modifier = Modifier.padding(8.dp))
            viewModel.targets.forEach { target ->
                val result = viewModel.getTargetResult(target)
                ExpandablePointDashboard(
                    name = target.name,
                    icon = Icons.Default.TrackChanges,
                    color = Color.Red,
                    result = result,
                    viewModel = viewModel
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("لا توجد أهداف محددة حالياً", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
        }
        
        if (viewModel.referencePoints.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text("نقاط العلام المضافة", style = MaterialTheme.typography.titleLarge, color = Color.Blue, modifier = Modifier.padding(8.dp))
            
            viewModel.referencePoints.forEach { ref ->
                val refResult = viewModel.getRefResult(ref)
                ExpandablePointDashboard(
                    name = ref.name,
                    icon = Icons.Default.Flag,
                    color = Color.Blue,
                    result = refResult,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
fun ExpandablePointDashboard(
    name: String,
    icon: ImageVector,
    color: Color,
    result: com.example.my_cannon.data.model.CalculationResult?,
    viewModel: CannonViewModel
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(name, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                }
            }
            
            result?.let {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("المسافة (Range)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text("${String.format(Locale.US, "%.1f", it.distance)} m", fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("السمت (Azimuth)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text("${String.format(Locale.US, "%.2f", it.normalizedAzimuth)}°", fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("الربع", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(it.quadrant.nameAr, fontWeight = FontWeight.Bold)
                    }
                }
                
                if (expanded) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    // حساب قراءات العلامات لهذا الهدف
                    val readings = viewModel.referencePoints.mapNotNull { ref ->
                        viewModel.calculateReading(it, ref)
                    }
                    
                    ResultsDashboard(result = it, pointName = name, showTitle = false, readings = readings)
                }
            }
        }
    }
}
