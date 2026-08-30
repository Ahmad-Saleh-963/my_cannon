package com.ahmadsaleh.map

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
import androidx.compose.foundation.BorderStroke
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
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahmadsaleh.map.data.model.PointType
import java.util.Locale
import com.ahmadsaleh.map.ui.components.CompassVisualizer
import com.ahmadsaleh.map.ui.components.UnifiedEditDialog
import com.ahmadsaleh.map.ui.components.MapViewContainer
import com.ahmadsaleh.map.ui.components.ResultsDashboard
import com.ahmadsaleh.map.ui.screens.TacticalGeometryScreen
import com.ahmadsaleh.map.ui.screens.CannonSimulationScreen
import com.ahmadsaleh.map.ui.theme.My_cannonTheme
import com.ahmadsaleh.map.ui.viewmodel.CannonViewModel
import com.ahmadsaleh.map.data.model.*
import com.ahmadsaleh.map.domain.calculator.UtmConverter
import com.ahmadsaleh.map.ui.screens.OfflineMapsScreen
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.ahmadsaleh.map.ui.viewmodel.MapOfflineViewModel
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.ahmadsaleh.map.ui.screens.*

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

    var destinationPoint by remember { mutableStateOf<Point?>(null) }
    var isSearchBarVisible by remember { mutableStateOf(value = false) }

    // ─── حالة الاستيراد/التصدير ───────────────────────────────────────────────

    val searchQuery by offlineViewModel.searchQuery.collectAsState()
    val searchResults by offlineViewModel.proResults.collectAsState()
    val isSearching by offlineViewModel.isSearching.collectAsState()
    val allRoutes by offlineViewModel.currentRoutes.collectAsState()
    val selectedRouteIndex by offlineViewModel.selectedRouteIndex.collectAsState()
    
    val keyboardController = LocalSoftwareKeyboardController.current

    // مؤقت لإخفاء شريط البحث تلقائياً
    LaunchedEffect(isSearchBarVisible) {
        if (isSearchBarVisible) {
            delay(5.seconds)
            if (searchQuery.isEmpty()) isSearchBarVisible = false
        }
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
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // التعامل مع زر الرجوع في النظام لمنع إغلاق التطبيق فجأة
    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    val initialLoc = remember { viewModel.getLastLocation() }
    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            if (initialLoc != null) {
                // إذا وجد موقع مخزن، نفتح عليه مباشرة وبدقة عالية دون المرور بدمشق
                center(Point.fromLngLat(initialLoc.second, initialLoc.first))
                zoom(15.5)
            } else {
                // إذا لم يوجد، نفتح على دمشق كخيار افتراضي
                center(Point.fromLngLat(36.2765, 33.5138))
                zoom(12.0)
            }
        }
    }

    // استماع لطلبات تحريك الكاميرا من القوائم
    LaunchedEffect(Unit) {
        viewModel.cameraMoveEvent.collect { geo ->
            selectedTab = 0
            mapViewportState.setCameraOptions {
                center(Point.fromLngLat(geo.longitude, geo.latitude))
                zoom(15.5)
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
        locationPermissionGranted = (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        
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
                        allRoutes = allRoutes,
                        selectedRouteIndex = selectedRouteIndex,
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
                                    onValueChange = { it ->
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
                    SettingsScreens(
                        onBack = { selectedTab = 0 },
                        onNavigateToOffline = { selectedTab = 5 },
                        onNavigateToLists = { selectedTab = 6 }
                    )
                }
                5 -> {
                    OfflineMapsScreen(onBack = { selectedTab = 4 })
                }
                6 -> {
                    ListsScreen(
                        onBack = { selectedTab = 4 },
                        onNavigateToTargets = { selectedTab = 7 },
                        onNavigateToRefs = { selectedTab = 8 }
                    )
                }
                7 -> {
                    ListDetailsScreen(
                        title = "قائمة الأهداف",
                        type = PointType.TARGET,
                        viewModel = viewModel,
                        onBack = { selectedTab = 6 }
                    )
                }
                8 -> {
                    ListDetailsScreen(
                        title = "نقاط العلام",
                        type = PointType.REFERENCE,
                        viewModel = viewModel,
                        onBack = { selectedTab = 6 }
                    )
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
                    // 1. زر GPS الموحد الأنيق في أسفل اليسار
                    Surface(
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
                            .padding(start = 16.dp, bottom = 68.dp)
                            .size(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        border = BorderStroke(
                            1.2.dp,
                            if (locationPermissionGranted) Color.Cyan.copy(alpha = 0.7f) else Color.Gray.copy(alpha = 0.4f)
                        ),
                        tonalElevation = 6.dp,
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "تحديد موقعي",
                                tint = if (locationPermissionGranted) Color.Cyan else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // 2. قائمة Speed Dial الزر الرئيسي الموحد في أسفل اليمين
                    SpeedDialFab(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 68.dp),
                        viewModel = viewModel,
                        currentType = viewModel.selectedPointType,
                        onNavigateToOffline = { selectedTab = 4 }
                    )
                }
            }
        }

        if (viewModel.showEditDialog) {
            UnifiedEditDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.showEditDialog = false }
            )
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
                        val geo = GeoPoint(it.latitude, it.longitude)
                        val utm = UtmConverter.fromGeoToUtm(geo)
                        viewModel.updatePointFull(
                            point = null,
                            type = PointType.CANNON,
                            name = "المربط",
                            description = "",
                            elevation = 0.0,
                            geo = geo,
                            utm = utm
                        )
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
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var showClearAllConfirmationDialog by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(if (expanded) 45f else 0f, label = "fab_rotation")
    
    // لون الزر حسب وضع الإضافة الحالي
    val silverColor = Color(0xFFC0C2C9)
    val fabColor = when (currentType) {
        PointType.CANNON -> Color.Green
        PointType.TARGET -> Color.Red
        PointType.REFERENCE -> Color.Blue
        PointType.NONE -> silverColor
    }

    if (showClearAllConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirmationDialog = false },
            icon = {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFF453A).copy(alpha = 0.15f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = Color(0xFFFF453A),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "تأكيد مسح كافة البيانات",
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "سيتم مسح كافة البيانات النقاط والجلسة الحالية بشكل نهائي ولن يمكنك استعادتها:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        color = Color(0xFFFF453A).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFFF453A).copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.GpsFixed, contentDescription = null, tint = Color.Green, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (viewModel.cannonPos != null) "المربط الرئيسي: ${viewModel.cannonPos?.name}" else "المربط الرئيسي: غير محدد",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TrackChanges, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "قائمة الأهداف: ${viewModel.targets.size} هدف محدد",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Flag, contentDescription = null, tint = Color.Blue, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "نقاط العلام: ${viewModel.referencePoints.size} نقطة علام",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Text(
                        text = "هل أنت أصلًا متأكد من استمرار عملية المسح النهائي؟",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF453A)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearPoints()
                        showClearAllConfirmationDialog = false
                        Toast.makeText(context, "✅ تم مسح كافة البيانات والجلسة بنجاح", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF453A),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("نعم، مسح الكل", fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showClearAllConfirmationDialog = false },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("إلغاء والاحتفاظ بالبيانات")
                }
            }
        )
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
                    label = "الإعدادات",
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
                        showClearAllConfirmationDialog = true
                        expanded = false
                    }
                )
            }
        }

        // الزر الرئيسي الموحد (+)
        Surface(
            onClick = { expanded = !expanded },
            modifier = Modifier
                .size(42.dp)
                .rotate(rotation),
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.75f),
            border = BorderStroke(1.2.dp, fabColor.copy(alpha = 0.8f)),
            tonalElevation = 6.dp,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (expanded) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = "فتح القائمة",
                    tint = fabColor,
                    modifier = Modifier.size(22.dp)
                )
            }
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
        modifier = Modifier.padding(end = 2.dp)
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.8f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(0.5.dp, color.copy(alpha = 0.4f))
        ) {
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        
        Surface(
            modifier = Modifier
                .size(38.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            shape = RoundedCornerShape(10.dp),
            color = Color.Black.copy(alpha = 0.75f),
            border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
            tonalElevation = 4.dp,
            shadowElevation = 3.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon, 
                    contentDescription = null, 
                    modifier = Modifier.size(18.dp),
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
    result: CalculationResult?,
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
                        Text("${String.format(Locale.US, "%.1f", it.distance)} متر ", fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("السمت (Azimuth)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text("${String.format(Locale.US, "%.2f", it.azimuthMils6000)} مليم ", fontWeight = FontWeight.Bold)
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
