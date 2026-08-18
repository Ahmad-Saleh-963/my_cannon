package com.example.my_cannon.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.my_cannon.ui.viewmodel.MapOfflineViewModel
import com.example.my_cannon.ui.viewmodel.ProvinceOfflineState

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

    // متصفح الملفات للاستيراد
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.importMapFromFolder(it.toString()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("خرائط الميدان", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    // زر الاستيراد الاحترافي
                    TextButton(onClick = { importLauncher.launch(null) }) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, tint = Color.Cyan)
                        Spacer(Modifier.width(4.dp))
                        Text("استيراد", color = Color.Cyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.9f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("ابحث عن محافظة...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray)
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Cyan,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.Cyan
                ),
                singleLine = true
            )

            Text(
                "المحافظات السورية",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                color = Color.Cyan,
                fontWeight = FontWeight.Bold
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = province.nameAr,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (province.isDownloading) province.status else province.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (province.isDownloading) Color.Cyan else Color.Gray
                    )
                }

                Box(contentAlignment = Alignment.Center) {
                    if (province.isDownloaded) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red.copy(alpha = 0.7f))
                        }
                    } else if (province.isDownloading) {
                        CircularProgressIndicator(
                            progress = { province.progress },
                            modifier = Modifier.size(32.dp),
                            color = Color.Cyan,
                            strokeWidth = 3.dp,
                            trackColor = Color.DarkGray
                        )
                        Text(
                            text = "${(province.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Cyan,
                            fontSize = 8.sp
                        )
                    } else {
                        IconButton(onClick = onDownload) {
                            Icon(Icons.Default.Download, contentDescription = "تحميل", tint = Color.Cyan)
                        }
                    }
                }
            }

            if (province.isDownloading) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "تم تحميل: ${province.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    Text(
                        "${province.completedResources} / ${province.totalResources}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { province.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color.Cyan,
                    trackColor = Color.DarkGray
                )
            } else if (province.isDownloaded) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.Green,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "جاهز للاستخدام أوفلاين",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Green
                        )
                    }
                    if (province.size.isNotEmpty()) {
                        Text(
                            province.size,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}
