package com.ahmadsaleh.map.ui.screens

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahmadsaleh.map.ui.viewmodel.CannonViewModel
import com.ahmadsaleh.map.data.model.PointType
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreens(
    viewModel: CannonViewModel = viewModel(),
    onBack: () -> Unit,
    onNavigateToOffline: () -> Unit,
    onNavigateToLists: () -> Unit,
    onNavigateToDrivingRecords: () -> Unit = {},
    onExport: () -> Unit = {},
    onImport: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("الإعدادات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = Color.Unspecified,
                    navigationIconContentColor = Color.Unspecified,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = Color.Unspecified
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsCard(
                title = "سجلات وتاريخ الرحلات",
                subtitle = "عرض توثيق حركات وقيادة المركبة والمواقع والسرعة",
                icon = Icons.Default.DirectionsCar,
                color = Color(0xFF00E5FF),
                onClick = onNavigateToDrivingRecords
            )

            SettingsCard(
                title = "القوائم (الأهداف والعلامات)",
                subtitle = "عرض وتعديل كافة النقاط المحفوظة",
                icon = Icons.AutoMirrored.Filled.List,
                color = Color(0xFF1976D2),
                onClick = onNavigateToLists
            )

            SettingsCard(
                title = "الخرائط بدون إنترنت",
                subtitle = "تحميل وإدارة مناطق الخرائط أوفلاين",
                icon = Icons.Default.Map,
                color = Color(0xFF388E3C),
                onClick = onNavigateToOffline
            )

            var showPermissionsDialog by remember { mutableStateOf(false) }

            SettingsCard(
                title = "إدارة صلاحيات وأذونات التطبيق",
                subtitle = "عرض حالة وإدارة أذونات الموقع، الإشعارات، والنافذة العائمة",
                icon = Icons.Default.VerifiedUser,
                color = Color(0xFF30D158),
                onClick = { showPermissionsDialog = true }
            )

            if (showPermissionsDialog) {
                PermissionsManagerDialog(
                    onDismiss = { showPermissionsDialog = false }
                )
            }

            // بطاقة السلايدر والمفتاح التفاعلي الديناميكي لتشغيل/إطفاء وتحديد حد السرعة للتنبيه والاهتزاز
            var sliderPosition by remember(viewModel.maxSpeedLimit) { mutableFloatStateOf(viewModel.maxSpeedLimit.toFloat()) }
            var isAlarmActive by remember(viewModel.isSpeedAlarmEnabled) { mutableStateOf(viewModel.isSpeedAlarmEnabled) }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, if (isAlarmActive) Color(0xFFFF3B30).copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isAlarmActive) Icons.Default.Speed else Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = null,
                                tint = if (isAlarmActive) Color(0xFFFF3B30) else Color.Gray,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "حد تحذير السرعة الفائقة",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isAlarmActive) MaterialTheme.colorScheme.onSurface else Color.Gray
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = if (isAlarmActive) Color(0xFFFF3B30).copy(alpha = 0.18f) else Color.Gray.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isAlarmActive) Color(0xFFFF3B30).copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = if (isAlarmActive) "⚠️ ${sliderPosition.roundToInt()} كم/س" else "🔕 معطل",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isAlarmActive) Color(0xFFFF3B30) else Color.Gray,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Switch(
                                checked = isAlarmActive,
                                onCheckedChange = { checked ->
                                    isAlarmActive = checked
                                    viewModel.toggleSpeedAlarm(checked)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFFFF3B30),
                                    uncheckedThumbColor = Color.LightGray,
                                    uncheckedTrackColor = Color.DarkGray
                                )
                            )
                        }
                    }

                    Text(
                        text = if (isAlarmActive) "عند بلوغ السرعة المحددة أثناء القيادة، سيهتز الهاتف 3 مرات متتالية مع إظهار تنبيه تحذيري فوري." else "المنبه معطل حالياً ولن يهتز الهاتف أثناء القيادة السرعة العالية.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )

                    if (isAlarmActive) {
                        Slider(
                            value = sliderPosition,
                            onValueChange = { sliderPosition = it },
                            onValueChangeFinished = {
                                viewModel.updateMaxSpeedLimit(sliderPosition.roundToInt())
                            },
                            valueRange = 60f..180f,
                            steps = 23,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFF3B30),
                                activeTrackColor = Color(0xFFFF3B30),
                                inactiveTrackColor = Color.DarkGray
                            )
                        )
                    }
                }
            }

            // بطاقة تنبيه الخروج عن المسار والعودة الذكية
            var isOffRouteActive by remember(viewModel.isOffRouteAlertEnabled) { mutableStateOf(viewModel.isOffRouteAlertEnabled) }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, if (isOffRouteActive) Color(0xFFFF9500).copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WrongLocation,
                                contentDescription = null,
                                tint = if (isOffRouteActive) Color(0xFFFF9500) else Color.Gray,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "تنبيه الخروج عن المسار والرجوع الذكي",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isOffRouteActive) MaterialTheme.colorScheme.onSurface else Color.Gray
                            )
                        }

                        Switch(
                            checked = isOffRouteActive,
                            onCheckedChange = { checked ->
                                isOffRouteActive = checked
                                viewModel.toggleOffRouteAlert(checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFFF9500),
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = Color.DarkGray
                            )
                        )
                    }

                    Text(
                        text = if (isOffRouteActive) "عند الانحراف أو الخروج خارج المسار أثناء الملاحة، سيهتز الهاتف مع تنبيهك فورياً ورسم مسار رجوع تصحيحي بلون برتقالي متميز للعودة للمسار." else "تنبيه الخروج عن المسار وإعادة الحساب معطل حالياً.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Text(
                "إدارة الجلسات",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )

            SettingsCard(
                title = "تصدير الجلسة الحالية",
                subtitle = "حفظ المربط والأهداف ونقاط العلام في ملف قابل للمشاركة",
                icon = Icons.Default.FileUpload,
                color = Color(0xFF7B1FA2),
                onClick = onExport
            )

            SettingsCard(
                title = "استيراد جلسة",
                subtitle = "استيراد ملف جلسة من شخص آخر أو نسخة احتياطية",
                icon = Icons.Default.FileDownload,
                color = Color(0xFFE65100),
                onClick = onImport
            )
        }
    }
}

@Composable
fun SettingsCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clickable(onClick = onClick),

    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(14.dp),
                color = color.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            
            Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = Color.Black)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    onBack: () -> Unit,
    onNavigateToTargets: () -> Unit,
    onNavigateToRefs: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("القوائم", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsCard(
                title = "قائمة الأهداف",
                subtitle = "إدارة الأهداف العسكرية المكتشفة",
                icon = Icons.Default.TrackChanges,
                color = Color.Red,
                onClick = onNavigateToTargets
            )

            SettingsCard(
                title = "نقاط العلام",
                subtitle = "إدارة نقاط العلام والرموز المضافة",
                icon = Icons.Default.Flag,
                color = Color.Blue,
                onClick = onNavigateToRefs
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailsScreen(
    title: String,
    type: PointType,
    viewModel: CannonViewModel,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val items = when (type) {
        PointType.TARGET -> viewModel.targets
        PointType.REFERENCE -> viewModel.referencePoints
        else -> emptyList()
    }

    val filteredItems = if (searchQuery.isEmpty()) {
        items
    } else {
        items.filter { item ->
            val name = when (item) {
                is com.ahmadsaleh.map.data.model.TargetPosition -> item.name
                is com.ahmadsaleh.map.data.model.ReferencePoint -> item.name
                else -> ""
            }
            val desc = when (item) {
                is com.ahmadsaleh.map.data.model.TargetPosition -> item.description
                is com.ahmadsaleh.map.data.model.ReferencePoint -> item.description
                else -> ""
            }
            name.contains(searchQuery, ignoreCase = true) || desc.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text(title, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                    }
                )
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("بحث...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = Color.LightGray,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
            }
        }
    ) { padding ->
        if (filteredItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("لا توجد نتائج", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(filteredItems) { item ->
                    PointItemCard(
                        item = item,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
fun PointItemCard(
    item: Any,
    viewModel: CannonViewModel
) {
    val name = when (item) {
        is com.ahmadsaleh.map.data.model.TargetPosition -> item.name
        is com.ahmadsaleh.map.data.model.ReferencePoint -> item.name
        else -> ""
    }
    val desc = when (item) {
        is com.ahmadsaleh.map.data.model.TargetPosition -> item.description
        is com.ahmadsaleh.map.data.model.ReferencePoint -> item.description
        else -> ""
    }
    val geo = when (item) {
        is com.ahmadsaleh.map.data.model.TargetPosition -> item.geoPoint
        is com.ahmadsaleh.map.data.model.ReferencePoint -> item.geoPoint
        else -> com.ahmadsaleh.map.data.model.GeoPoint(0.0, 0.0)
    }
    
    val color = if (item is com.ahmadsaleh.map.data.model.TargetPosition) Color.Red else Color.Blue

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (item is com.ahmadsaleh.map.data.model.TargetPosition) Icons.Default.TrackChanges else Icons.Default.Flag,
                        null,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    if (desc.isNotEmpty()) {
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 2)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp), color = Color.LightGray.copy(alpha = 0.2f))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.moveToLocation(geo) }) {
                    Icon(Icons.Default.MyLocation, "انتقال", tint = MaterialTheme.colorScheme.primary)
                }
                
                IconButton(onClick = { viewModel.openEditDialog(item) }) {
                    Icon(Icons.Default.Edit, "تعديل", tint = Color(0xFF673AB7))
                }
                
                IconButton(onClick = { viewModel.deletePoint(item) }) {
                    Icon(Icons.Default.Delete, "حذف", tint = Color.Red)
                }
            }
        }
    }
}

fun checkLocationGranted(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

fun checkNotificationsGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

fun checkVibrationGranted(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.VIBRATE) == PackageManager.PERMISSION_GRANTED
}

fun checkPipGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
    return try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, android.os.Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, android.os.Process.myUid(), context.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        true
    }
}

fun openAppSettings(context: Context) {
    try {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "يرجى فتح إعدادات الجهاز لمنح الصلاحية", Toast.LENGTH_LONG).show()
    }
}

fun openPipSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        try {
            val intent = Intent(
                "android.settings.PICTURE_IN_PICTURE_SETTINGS",
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppSettings(context)
        }
    }
}

@Composable
fun PermissionsManagerDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isLocationGranted by remember { mutableStateOf(checkLocationGranted(context)) }
    var isNotificationGranted by remember { mutableStateOf(checkNotificationsGranted(context)) }
    var isPipGrantedState by remember { mutableStateOf(checkPipGranted(context)) }
    var isVibrationGranted by remember { mutableStateOf(checkVibrationGranted(context)) }

    fun refreshPermissions() {
        isLocationGranted = checkLocationGranted(context)
        isNotificationGranted = checkNotificationsGranted(context)
        isPipGrantedState = checkPipGranted(context)
        isVibrationGranted = checkVibrationGranted(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        refreshPermissions()
        val anyGranted = results.values.any { it }
        if (!anyGranted && results.isNotEmpty()) {
            openAppSettings(context)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = Color(0xFF30D158),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "إدارة صلاحيات التطبيق",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "يوضح هذا الجدول الصلاحيات المطلوبة لعمل التطبيق، ويمكنك طلب وتفعيل أي صلاحية مباشرةً بنقرة واحدة:",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = Color.Gray
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // 1. صلاحية الموقع الجغرافي (طلب مباشر حقيقي بنقرة واحدة)
                PermissionCardItem(
                    title = "الموقع الجغرافي العالي الدقة (GPS)",
                    description = "ضروري لتحديد موقعك المباشر ورسم مسار الملاحة وحساب السرعة بدقة.",
                    isGranted = isLocationGranted,
                    icon = Icons.Default.GpsFixed,
                    color = Color.Cyan,
                    onGrantClick = {
                        if (!isLocationGranted) {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    }
                )

                // 2. النافذة العائمة (Picture-in-Picture)
                PermissionCardItem(
                    title = "النافذة العائمة (Picture-in-Picture)",
                    description = "تتيح استمرار عرض الخريطة والسرعة في إطار طافٍ فوق التطبيقات.",
                    isGranted = isPipGrantedState,
                    icon = Icons.Default.PictureInPicture,
                    color = Color(0xFFFF9500),
                    onGrantClick = {
                        if (!isPipGrantedState) {
                            openPipSettings(context)
                        }
                    }
                )

                // 3. إشعارات الخدمة والتحميل (طلب مباشر حقيقي بنقرة واحدة)
                PermissionCardItem(
                    title = "إشعارات الخدمة والتحميل",
                    description = "ضرورية لإظهار نسبة وسرعة تحميل الخرائط أوفلاين في شريط الإشعارات.",
                    isGranted = isNotificationGranted,
                    icon = Icons.Default.Notifications,
                    color = Color(0xFF1976D2),
                    onGrantClick = {
                        if (!isNotificationGranted) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                            } else {
                                openAppSettings(context)
                            }
                        }
                    }
                )

                // 4. صلاحية اهتزاز التنبيهات (طلب مباشر)
                PermissionCardItem(
                    title = "اهتزاز التنبيهات والسرعة",
                    description = "تتيح اهتزاز الهاتف عند تجاوز حد السرعة المسموح أو الانحراف عن المسار.",
                    isGranted = isVibrationGranted,
                    icon = Icons.Default.Vibration,
                    color = Color(0xFFFF3B30),
                    onGrantClick = {
                        if (!isVibrationGranted) {
                            permissionLauncher.launch(arrayOf(Manifest.permission.VIBRATE))
                        }
                    }
                )

                Spacer(modifier = Modifier.height(2.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("تم - موافق", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
fun PermissionCardItem(
    title: String,
    description: String,
    isGranted: Boolean,
    icon: ImageVector,
    color: Color,
    onGrantClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, if (isGranted) Color(0xFF30D158).copy(alpha = 0.4f) else Color(0xFFFF9500).copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = color.copy(alpha = 0.12f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, fontSize = 12.5.sp)
                }

                Surface(
                    color = if (isGranted) Color(0xFF30D158).copy(alpha = 0.15f) else Color(0xFFFF9500).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, if (isGranted) Color(0xFF30D158).copy(alpha = 0.5f) else Color(0xFFFF9500).copy(alpha = 0.5f))
                ) {
                    Text(
                        text = if (isGranted) "✅ ممنوحة" else "⚠️ يحتاج تفعيل",
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isGranted) Color(0xFF30D158) else Color(0xFFFF9500),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(description, fontSize = 10.sp, color = Color.Gray, lineHeight = 14.sp)

            if (!isGranted) {
                Button(
                    onClick = onGrantClick,
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9500))
                ) {
                    Icon(Icons.Default.TouchApp, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("طلب وتفعيل الصلاحية الآن", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
