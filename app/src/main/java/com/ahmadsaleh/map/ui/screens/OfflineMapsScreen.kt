package com.ahmadsaleh.map.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahmadsaleh.map.ui.viewmodel.MapOfflineViewModel
import com.ahmadsaleh.map.ui.viewmodel.ProvinceOfflineState

enum class OfflineFilter { ALL, DOWNLOADED, DOWNLOADING, AVAILABLE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapsScreen(
    onBack: () -> Unit,
    viewModel: MapOfflineViewModel = viewModel()
) {
    val provinces by viewModel.provinces.collectAsState()
    val offlineSearchQuery by viewModel.offlineSearchQuery.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshDownloadedStates()
    }

    var activeFilter by remember { mutableStateOf(OfflineFilter.ALL) }
    var showDownloadAllDialog by remember { mutableStateOf(false) }
    var provinceForDownloadDialog by remember { mutableStateOf<ProvinceOfflineState?>(null) }
    var globalZoomAll by remember { mutableIntStateOf(16) }

    val filteredProvinces = remember(provinces, offlineSearchQuery, activeFilter) {
        provinces.filter { province ->
            val matchesSearch = offlineSearchQuery.isBlank() ||
                    province.name.contains(offlineSearchQuery, ignoreCase = true) ||
                    province.nameAr.contains(offlineSearchQuery)

            val matchesFilter = when (activeFilter) {
                OfflineFilter.ALL -> true
                OfflineFilter.DOWNLOADED -> province.isDownloaded
                OfflineFilter.DOWNLOADING -> province.isDownloading
                OfflineFilter.AVAILABLE -> !province.isDownloaded && !province.isDownloading
            }

            matchesSearch && matchesFilter
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.importMapFromFolder(it.toString()) }
    }

    provinceForDownloadDialog?.let { targetProvince ->
        ProvinceDownloadDialog(
            provinceNameAr = targetProvince.nameAr,
            initialZoom = targetProvince.targetZoom,
            onConfirm = { chosenZoom ->
                viewModel.downloadProvince(targetProvince.name, chosenZoom)
                provinceForDownloadDialog = null
            },
            onDismiss = { provinceForDownloadDialog = null }
        )
    }

    if (showDownloadAllDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadAllDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFF0A84FF))
                    Spacer(Modifier.width(8.dp))
                    Text("تحميل كافة المحافظات والمدن", color = Color.White, fontWeight = FontWeight.ExtraBold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "اختر مستوى الدقة الجغرافية لتنزيل كافة المحافظات الـ 14 أوفلاين:",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )

                    ZoomOptionItem("🚀 دقة فائقة الميدان (Zoom 18)", "أقصى دقة تفصيلية لأرقام المباني والمواقع التكتيكية (~350 MB)", globalZoomAll == 18) { globalZoomAll = 18 }
                    ZoomOptionItem("🌟 دقة الشارع والبناء (Zoom 16 - موصى به)", "الشوارع الفرعية والأزقة والبنايات والمعالم المحلية (~120 MB)", globalZoomAll == 16) { globalZoomAll = 16 }
                    ZoomOptionItem("⚡ دقة المحاور والمدن (Zoom 14)", "المدن والمحاور والطرق الرئيسية والبديلة (~45 MB)", globalZoomAll == 14) { globalZoomAll = 14 }
                    ZoomOptionItem("📦 دقة الطرق السريعة (Zoom 12)", "الطرق الدولية والبلدات والحدود الإدارية (~18 MB)", globalZoomAll == 12) { globalZoomAll = 12 }
                    ZoomOptionItem("🗺️ دقة المدن الكبرى (Zoom 10)", "المدن الكبرى والمحاور الجغرافية الرئيسية (~8 MB)", globalZoomAll == 10) { globalZoomAll = 10 }
                    ZoomOptionItem("🌍 دقة الإقليم (Zoom 8)", "النطاق الإقليمي للمحافظة والحدود الخارجي (~3 MB)", globalZoomAll == 8) { globalZoomAll = 8 }
                    ZoomOptionItem("🌐 دقة خاطفة (Zoom 5)", "الحدود والمسارات الإقليمية الخاطفة (~1 MB)", globalZoomAll == 5) { globalZoomAll = 5 }
                    ZoomOptionItem("🏳️ دقة خريطة الدولة (Zoom 1)", "نظرة عامة على مستوى القطر والدولة (< 1 MB)", globalZoomAll == 1) { globalZoomAll = 1 }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.downloadAllProvinces(globalZoomAll)
                        showDownloadAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF), contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("بدء تحميل الكل (Zoom $globalZoomAll)", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadAllDialog = false }) {
                    Text("إلغاء", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF161E2E),
            shape = RoundedCornerShape(22.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("خرائط الميدان والمحافظات", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        Text("تحميل أوفلاين كامل مع تحديد الدقة (Zoom 1 - 18)", fontSize = 11.sp, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { showDownloadAllDialog = true }) {
                        Icon(Icons.Default.CloudDownload, contentDescription = "تحميل الكل", tint = Color(0xFF0A84FF))
                    }
                    IconButton(onClick = { importLauncher.launch(null) }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "استيراد", tint = Color.Cyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121622),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF090D16)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // لوحة قيادة التغطية الكلية (Offline Download Dashboard Header Card)
            val downloadedCount = provinces.count { it.isDownloaded }
            val overallProgress = downloadedCount / 14f

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF121826),
                border = BorderStroke(1.dp, Color(0xFF0A84FF).copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(26.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "لوحة جاهزية التغطية أوفلاين",
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                        }

                        Surface(
                            color = Color(0xFF30D158).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "$downloadedCount / 14 محافظة",
                                color = Color(0xFF30D158),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { overallProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = Color(0xFF30D158),
                        trackColor = Color(0xFF1E2838)
                    )

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = Color.White.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "🛡️ تغطية 100% أوفلاين",
                                fontSize = 10.sp,
                                color = Color.LightGray,
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp)
                            )
                        }
                        Surface(
                            color = Color.White.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "📍 خيارات الدقة (Zoom 1 إلى 18)",
                                fontSize = 10.sp,
                                color = Color.LightGray,
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp)
                            )
                        }
                    }
                }
            }

            // حقل البحث التفاعلي الخاص بشاشة الخرائط أوفلاين
            OutlinedTextField(
                value = offlineSearchQuery,
                onValueChange = { viewModel.onOfflineSearchQueryChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .height(52.dp),
                placeholder = { Text("ابحث عن مدينة أو محافظة سورية...", color = Color.Gray, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Cyan, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (offlineSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onOfflineSearchQueryChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF0A84FF),
                    unfocusedBorderColor = Color(0xFF1E2838),
                    focusedContainerColor = Color(0xFF121826),
                    unfocusedContainerColor = Color(0xFF121826),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF0A84FF)
                ),
                singleLine = true
            )

            // أزرار الفلترة السريعة
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val downloadingCount = provinces.count { it.isDownloading }
                val availableCount = provinces.count { !it.isDownloaded && !it.isDownloading }

                item {
                    FilterChipItem(
                        text = "الكل (${provinces.size})",
                        selected = activeFilter == OfflineFilter.ALL,
                        onClick = { activeFilter = OfflineFilter.ALL }
                    )
                }
                item {
                    FilterChipItem(
                        text = "جاهزة أوفلاين ($downloadedCount)",
                        selected = activeFilter == OfflineFilter.DOWNLOADED,
                        onClick = { activeFilter = OfflineFilter.DOWNLOADED }
                    )
                }
                if (downloadingCount > 0) {
                    item {
                        FilterChipItem(
                            text = "جاري التحميل ($downloadingCount)",
                            selected = activeFilter == OfflineFilter.DOWNLOADING,
                            onClick = { activeFilter = OfflineFilter.DOWNLOADING }
                        )
                    }
                }
                item {
                    FilterChipItem(
                        text = "متاحة للتحميل ($availableCount)",
                        selected = activeFilter == OfflineFilter.AVAILABLE,
                        onClick = { activeFilter = OfflineFilter.AVAILABLE }
                    )
                }
            }

            // قائمة المحافظات والمدن
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredProvinces, key = { it.name }) { province ->
                    ProvinceItem(
                        province = province,
                        onDownloadClick = { provinceForDownloadDialog = province },
                        onDelete = { viewModel.deleteProvince(province.name) }
                    )
                }
            }
        }
    }
}

@Composable
fun ProvinceDownloadDialog(
    provinceNameAr: String,
    initialZoom: Int = 16,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedZoom by remember { mutableIntStateOf(initialZoom) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "تحديد دقة الخريطة أوفلاين",
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = 17.sp
                )
                Text(
                    text = provinceNameAr,
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "اختر مستوى التفاصيل والدقة الجغرافية التي ترغب بتحميلها أوفلاين:",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(Modifier.height(2.dp))

                ZoomOptionItem("🚀 دقة فائقة الميدان (Zoom 18)", "أقصى دقة تفصيلية لأرقام المباني والممرات والمقع التكتيكية (~350 MB)", selectedZoom == 18) { selectedZoom = 18 }
                ZoomOptionItem("🌟 دقة الشارع والبناء (Zoom 16 - موصى به)", "الشوارع الفرعية والأزقة والبنايات والمعالم المحلية (~120 MB)", selectedZoom == 16) { selectedZoom = 16 }
                ZoomOptionItem("⚡ دقة المحاور والمدن (Zoom 14)", "المدن والبلدات والمحاور والطرق الرئيسية والبديلة (~45 MB)", selectedZoom == 14) { selectedZoom = 14 }
                ZoomOptionItem("📦 دقة الطرق السريعة (Zoom 12)", "الطرق الدولية والبلدات والحدود الإدارية للمحافظة (~18 MB)", selectedZoom == 12) { selectedZoom = 12 }
                ZoomOptionItem("🗺️ دقة المدن الكبرى (Zoom 10)", "المدن الكبرى والمحاور الجغرافية الكبرى (~8 MB)", selectedZoom == 10) { selectedZoom = 10 }
                ZoomOptionItem("🌍 دقة الإقليم (Zoom 8)", "النطاق الإقليمي للمحافظة والحدود الخارجي (~3 MB)", selectedZoom == 8) { selectedZoom = 8 }
                ZoomOptionItem("🌐 دقة خاطفة (Zoom 5)", "الحدود والمسارات الإقليمية الخاطفة (~1 MB)", selectedZoom == 5) { selectedZoom = 5 }
                ZoomOptionItem("🏳️ دقة خريطة الدولة (Zoom 1)", "نظرة عامة شائعة على مستوى القطر والدولة (< 1 MB)", selectedZoom == 1) { selectedZoom = 1 }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedZoom) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF), contentColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("بدء التحميل (Zoom $selectedZoom)", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF161E2E),
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
fun ZoomOptionItem(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Color(0xFF0A84FF).copy(alpha = 0.20f) else Color.White.copy(alpha = 0.04f),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.12f)
        )
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Color(0xFF00E5FF),
                    unselectedColor = Color.Gray
                )
            )
            Spacer(Modifier.width(6.dp))
            Column {
                Text(text = title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.5.sp)
                Text(text = subtitle, color = Color.Gray, fontSize = 10.5.sp, lineHeight = 14.sp)
            }
        }
    }
}

@Composable
fun FilterChipItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Color(0xFF0A84FF) else Color(0xFF161E2E),
        border = BorderStroke(1.dp, if (selected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.15f))
    ) {
        Text(
            text = text,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else Color.LightGray,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun ProvinceItem(
    province: ProvinceOfflineState,
    onDownloadClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("تأكيد مسح الخرائط", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("سيتم مسح خرائط وأشكال وبيانات ${province.nameAr} المحملة أوفلاين.", color = Color.LightGray) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("مسح البيانات", color = Color(0xFFFF453A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("إلغاء", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1C1C1E),
            shape = RoundedCornerShape(16.dp)
        )
    }

    val zoomBadgeText = when (province.targetZoom) {
        18 -> "دقة فائقة (Zoom 18)"
        16 -> "دقة الشارع (Zoom 16)"
        14 -> "دقة المحاور (Zoom 14)"
        12 -> "دقة الطرق السريعة (Zoom 12)"
        10 -> "دقة المدن الكبرى (Zoom 10)"
        8 -> "دقة الإقليم (Zoom 8)"
        5 -> "دقة خاطفة (Zoom 5)"
        1 -> "دقة عامة (Zoom 1)"
        else -> "Zoom ${province.targetZoom}"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121826)),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = if (province.isDownloading) 1.5.dp else 1.dp,
            color = when {
                province.isDownloading -> Color(0xFF00E5FF)
                province.isDownloaded -> Color(0xFF30D158).copy(alpha = 0.5f)
                else -> Color.White.copy(alpha = 0.10f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            province.isDownloaded -> Color(0xFF30D158).copy(alpha = 0.15f)
                            province.isDownloading -> Color(0xFF0A84FF).copy(alpha = 0.15f)
                            else -> Color.White.copy(alpha = 0.08f)
                        },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when {
                                    province.isDownloaded -> Icons.Default.Verified
                                    province.isDownloading -> Icons.Default.CloudSync
                                    else -> Icons.Default.LocationCity
                                },
                                contentDescription = null,
                                tint = when {
                                    province.isDownloaded -> Color(0xFF30D158)
                                    province.isDownloading -> Color(0xFF00E5FF)
                                    else -> Color.LightGray
                                },
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column {
                        Text(
                            text = province.nameAr,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = if (province.isDownloading) province.status else zoomBadgeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (province.isDownloading) Color(0xFF00E5FF) else Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }

                Box(contentAlignment = Alignment.Center) {
                    if (province.isDownloaded) {
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.background(Color(0xFFFF453A).copy(alpha = 0.12f), CircleShape)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "حذف", tint = Color(0xFFFF453A), modifier = Modifier.size(20.dp))
                        }
                    } else if (!province.isDownloading) {
                        Button(
                            onClick = onDownloadClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0A84FF),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("تحميل أوفلاين", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (province.isDownloading) {
                Spacer(modifier = Modifier.height(14.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "${(province.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF00E5FF),
                            fontSize = 28.sp
                        )
                        Text(
                            text = province.speed,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF30D158),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (province.totalResources > 0) "${province.completedResources} / ${province.totalResources} مصادر" else "جاري تجهيز المصادر...",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.LightGray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Text(
                            text = province.size,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                
                LinearProgressIndicator(
                    progress = { province.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(CircleShape),
                    color = Color(0xFF00E5FF),
                    trackColor = Color(0xFF1E2838)
                )
            } else if (province.isDownloaded) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFF30D158).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF30D158), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("جاهزة للعمل أوفلاين 100%", style = MaterialTheme.typography.labelSmall, color = Color(0xFF30D158), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }

                    Surface(
                        color = Color(0xFF0A84FF).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = zoomBadgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF0A84FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
