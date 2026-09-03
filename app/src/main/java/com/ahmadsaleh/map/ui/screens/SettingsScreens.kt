package com.ahmadsaleh.map.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahmadsaleh.map.MainActivity
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

            val context = LocalContext.current
            val activity = remember(context) {
                var c = context
                while (c is android.content.ContextWrapper) {
                    if (c is MainActivity) return@remember c
                    c = c.baseContext
                }
                null
            }

            SettingsCard(
                title = "النافذة العائمة (Picture-in-Picture)",
                subtitle = "تفعيل إتاحة عرض الخريطة والسرعة كإطار طافٍ فوق التطبيقات",
                icon = Icons.Default.PictureInPicture,
                color = Color(0xFFFF9500),
                onClick = { activity?.openPipSystemSettings() }
            )

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
                                imageVector = if (isAlarmActive) Icons.Default.Speed else Icons.Default.VolumeOff,
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
