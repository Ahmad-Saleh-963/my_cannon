package com.example.my_cannon

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.unit.dp
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
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // إبقاء الشاشة مضيئة دائماً أثناء عمل التطبيق
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
fun MainScreen(viewModel: CannonViewModel = viewModel()) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.initPrefs(context)
    }

    var selectedTab by remember { mutableIntStateOf(0) }

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

    // Location Permission State
    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationPermissionGranted) {
            mapViewportState.transitionToFollowPuckState()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (selectedTab != 3) {
                NavigationBar(
                    containerColor = Color.Black.copy(alpha = 0.7f), // جعلها أكثر وضوحاً قليلاً
                    contentColor = Color.White,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars) // التأكد من عدم التداخل مع شريط التنقل
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Map, contentDescription = null) },
                        label = { Text("الخريطة") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Green,
                            unselectedIconColor = Color.Gray,
                            selectedTextColor = Color.Green,
                            indicatorColor = Color.DarkGray
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Calculate, contentDescription = null) },
                        label = { Text("الحسابات") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Red,
                            indicatorColor = Color.DarkGray
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Timeline, contentDescription = null) },
                        label = { Text("تكتيكي") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Blue,
                            indicatorColor = Color.DarkGray
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.SportsEsports, contentDescription = null) },
                        label = { Text("المحاكي") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Cyan,
                            indicatorColor = Color.DarkGray
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> {
                    // الخريطة تأخذ كامل الشاشة
                    MapViewContainer(
                        viewModel = viewModel,
                        mapViewportState = mapViewportState,
                        locationPermissionGranted = locationPermissionGranted,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                3 -> {
                    // المحاكي يأخذ كامل الشاشة
                    CannonSimulationScreen(onExit = { selectedTab = 0 })
                }
                else -> {
                    // الشاشات الأخرى تلتزم بالبادينج
                    Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        when (selectedTab) {
                            1 -> ResultsScreen(viewModel)
                            2 -> TacticalGeometryScreen(viewModel)
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
                                mapViewportState.transitionToFollowPuckState()
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
                            .padding(start = 16.dp, bottom = 80.dp), // فوق شريط التنقل الداخلي
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
                            .padding(end = 16.dp, bottom = 80.dp), // فوق شريط التنقل الداخلي
                        viewModel = viewModel,
                        currentType = viewModel.selectedPointType
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
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpeedDialFab(modifier: Modifier, viewModel: CannonViewModel, currentType: PointType) {
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
