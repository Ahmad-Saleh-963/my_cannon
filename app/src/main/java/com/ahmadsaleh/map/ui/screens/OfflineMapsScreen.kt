package com.ahmadsaleh.map.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapsScreen(
    onBack: () -> Unit,
    viewModel: MapOfflineViewModel = viewModel()
) {
    val provinces by viewModel.provinces.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val filteredProvinces = remember(provinces, searchQuery) {
        if (searchQuery.isBlank()) {
            provinces
        } else {
            provinces.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.nameAr.contains(searchQuery)
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.importMapFromFolder(it.toString()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("خرائط الميدان", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    TextButton(onClick = { importLauncher.launch(null) }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("استيراد", fontSize = 13.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1C1C1E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color(0xFF0A84FF)
                )
            )
        },
        containerColor = Color(0xFF000000)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .height(54.dp),
                placeholder = { Text("ابحث عن محافظة...", color = Color.Gray, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp)) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF0A84FF),
                    unfocusedBorderColor = Color(0xFF2C2C2E),
                    focusedContainerColor = Color(0xFF1C1C1E),
                    unfocusedContainerColor = Color(0xFF1C1C1E),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF0A84FF)
                ),
                singleLine = true
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredProvinces) { province ->
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
fun ProvinceItem(
    province: ProvinceOfflineState,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("تأكيد الحذف", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("سيتم مسح كافة البيانات المحملة لهذه المنطقة.", color = Color.LightGray) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("حذف", color = Color(0xFFFF453A), fontWeight = FontWeight.Bold)
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        shape = RoundedCornerShape(16.dp),
        border = if (province.isDownloading) BorderStroke(1.dp, Color(0xFF0A84FF)) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = province.nameAr,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    )
                    Text(
                        text = if (province.isDownloading) province.status else province.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (province.isDownloading) Color(0xFF0A84FF) else Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(contentAlignment = Alignment.Center) {
                    if (province.isDownloaded) {
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.background(Color(0xFFFF453A).copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFFF453A), modifier = Modifier.size(20.dp))
                        }
                    } else if (!province.isDownloading) {
                        IconButton(
                            onClick = onDownload,
                            modifier = Modifier.background(Color(0xFF0A84FF).copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFF0A84FF), modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }

            if (province.isDownloading) {
                Spacer(modifier = Modifier.height(20.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "${(province.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0A84FF),
                            fontSize = 36.sp
                        )
                        Text(
                            text = province.speed,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF30D158),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${province.completedResources} / ${province.totalResources}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.LightGray,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = province.size,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                
                LinearProgressIndicator(
                    progress = { province.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = Color(0xFF0A84FF),
                    trackColor = Color(0xFF2C2C2E)
                )
            } else if (province.isDownloaded) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = Color(0xFF30D158).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF30D158), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("جاهزة للعمل الميداني", style = MaterialTheme.typography.labelSmall, color = Color(0xFF30D158), fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}
