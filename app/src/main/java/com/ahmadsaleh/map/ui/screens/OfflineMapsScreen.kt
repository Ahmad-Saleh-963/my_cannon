package com.ahmadsaleh.map.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val searchQuery by viewModel.searchQuery.collectAsState()

    var activeFilter by remember { mutableStateOf(OfflineFilter.ALL) }
    var showDownloadAllDialog by remember { mutableStateOf(false) }

    val filteredProvinces = remember(provinces, searchQuery, activeFilter) {
        provinces.filter { province ->
            val matchesSearch = searchQuery.isBlank() ||
                    province.name.contains(searchQuery, ignoreCase = true) ||
                    province.nameAr.contains(searchQuery)

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
                Text(
                    "سيتم البدء بتحميل خرائط كافة المحافظات والمدن السورية الـ 14 بجميع تفاصيلها الدقيقة (الشارع والبناء والبحث والملاحة الميدانية) للعمل أوفلاين 100%.",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.downloadAllProvinces()
                        showDownloadAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF), contentColor = Color.White)
                ) {
                    Text("بدء تحميل الكل", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadAllDialog = false }) {
                    Text("إلغاء", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1C1C1E),
            shape = RoundedCornerShape(20.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("خرائط الميدان والمحافظات", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        Text("تحميل أوفلاين كامل للمدن والبحث والتوجيه", fontSize = 11.sp, color = Color.Gray)
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
            // شريط المعلومات التوضيحي للأوفلاين
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF121929),
                border = BorderStroke(1.dp, Color(0xFF0A84FF).copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "جاهزية ميدانية أوفلاين 100%",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "تحميل المحافظة يوفر الخرائط والتفاصيل الدقيقة (Zoom 16) والبحث ورسم المسارات بدون إنترنت.",
                            color = Color.Gray,
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            // حقل البحث التفاعلي
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .height(52.dp),
                placeholder = { Text("ابحث عن مدينة أو محافظة سورية...", color = Color.Gray, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Cyan, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
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
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val downloadedCount = provinces.count { it.isDownloaded }
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
                        onDownload = { viewModel.downloadProvince(province.name) },
                        onDelete = { viewModel.deleteProvince(province.name) }
                    )
                }
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
    onDownload: () -> Unit,
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
                            text = if (province.isDownloading) province.status else "تغطية كاملة للمدينة والريف (Zoom 16)",
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
                            onClick = onDownload,
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
                            text = "${province.completedResources} / ${province.totalResources} مصادر",
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
                            text = "بحث + ملاحة أوفلاين",
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
