package com.ahmadsaleh.map.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ahmadsaleh.map.data.db.AppDatabase
import com.ahmadsaleh.map.data.db.entity.DriveSessionEntity
import com.ahmadsaleh.map.ui.components.rememberTextBitmap
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.IconImage
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrivingRecordsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val driveSessionDao = remember { AppDatabase.getInstance(context).driveSessionDao() }

    val sessions by driveSessionDao.getAllDriveSessionsFlow().collectAsState(initial = emptyList())
    var showClearAllDialog by remember { mutableStateOf(false) }
    var selectedSessionForMap by remember { mutableStateOf<DriveSessionEntity?>(null) }

    selectedSessionForMap?.let { session ->
        DriveSessionMapDialog(
            session = session,
            onDismiss = { selectedSessionForMap = null }
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFFF453A))
                    Spacer(Modifier.width(8.dp))
                    Text("مسح كافة سجلات القيادة", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "هل أنت محقق من رغبتك بمسح كافة سجلات وتأريخ رحلات القيادة والسرعة المسجلة أوفلاين؟ لا يمكن التراجع عن هذا الإجراء.",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            driveSessionDao.clearAll()
                        }
                        showClearAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF453A), contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("مسح الكل", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("إلغاء", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1C1C1E),
            shape = RoundedCornerShape(22.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("سجلات وتاريخ القيادة", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        Text("توثيق حركات ورحلات المركبة أوفلاين", fontSize = 11.sp, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    if (sessions.isNotEmpty()) {
                        IconButton(onClick = { showClearAllDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "مسح الكل", tint = Color(0xFFFF453A))
                        }
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
            if (sessions.isNotEmpty()) {
                // لوحة إحصائيات سجلات القيادة الشاملة
                val totalDistanceKm = sessions.sumOf { it.distanceKm }
                val maxSpeedOverall = sessions.maxOfOrNull { it.topSpeedKmh } ?: 0.0
                val totalDurationSec = sessions.sumOf { it.durationSeconds }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
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
                                Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(26.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "ملخص إحصائيات القيادة",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                            }

                            Surface(
                                color = Color(0xFF0A84FF).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "${sessions.size} رحلة مسجلة",
                                    color = Color(0xFF0A84FF),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatSummaryPill(
                                label = "المسافة الكلية",
                                value = String.format(Locale.US, "%.1f كم", totalDistanceKm),
                                color = Color(0xFF30D158),
                                modifier = Modifier.weight(1f)
                            )
                            StatSummaryPill(
                                label = "أقصى سرعة",
                                value = "${maxSpeedOverall.roundToInt()} كم/س",
                                color = Color(0xFFFFD600),
                                modifier = Modifier.weight(1f)
                            )
                            StatSummaryPill(
                                label = "وقت القيادة",
                                value = formatDurationArabic(totalDurationSec),
                                color = Color(0xFF00E5FF),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // قائمة كروت سجلات القيادة
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sessions, key = { it.id }) { session ->
                        DriveSessionCard(
                            session = session,
                            onViewMap = { selectedSessionForMap = session },
                            onDelete = {
                                scope.launch(Dispatchers.IO) {
                                    driveSessionDao.deleteDriveSession(session)
                                }
                            }
                        )
                    }
                }
            } else {
                // حالة فارغة بدون سجلات
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF0A84FF).copy(alpha = 0.12f),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = "لا توجد سجلات قيادة مسجلة بعد",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 17.sp
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "عند تفعيل وضع القيادة (زر الملاحة 🧭) على الخريطة أثناء التنقل، سيتم تلقائياً حفظ وتوثيق الرحلة والسرعة والوقت والمسار هنا.",
                            color = Color.Gray,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatSummaryPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, fontSize = 9.5.sp, color = Color.Gray)
            Text(text = value, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}

@Composable
fun DriveSessionCard(
    session: DriveSessionEntity,
    onViewMap: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف سجل الرحلة", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد من مسح سجل هذه الرحلة؟", color = Color.LightGray) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("حذف", color = Color(0xFFFF453A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("إلغاء", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1C1C1E),
            shape = RoundedCornerShape(16.dp)
        )
    }

    val dateFormatter = remember { SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar")) }
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale("ar")) }

    val startDateStr = remember(session.startTime) { dateFormatter.format(Calendar.getInstance().apply { timeInMillis = session.startTime }.time) }
    val startTimeStr = remember(session.startTime) { timeFormatter.format(Calendar.getInstance().apply { timeInMillis = session.startTime }.time) }
    val endTimeStr = remember(session.endTime) { timeFormatter.format(Calendar.getInstance().apply { timeInMillis = session.endTime }.time) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onViewMap),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121826)),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // الهيدر: التاريخ والأوقات وزر الحذف
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = startDateStr,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.5.sp
                    )
                    Text(
                        text = "التوقيت: $startTimeStr  ←  $endTimeStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onViewMap,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF).copy(alpha = 0.20f), contentColor = Color(0xFF00E5FF)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("عرض الخريطة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.width(6.dp))

                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.background(Color(0xFFFF453A).copy(alpha = 0.12f), CircleShape)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف السجل", tint = Color(0xFFFF453A), modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // كارت تحرك المسار من موقع إلى موقع
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White.copy(alpha = 0.04f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFF30D158), CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(text = "الانطلاق: ", fontSize = 11.sp, color = Color.Gray)
                        Text(text = session.startPlaceName, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFFFF3B30), CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(text = "الوصول: ", fontSize = 11.sp, color = Color.Gray)
                        Text(text = session.endPlaceName, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // شبكة تفاصيل وقراءات القيادة
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DriveDetailBadge("⏱️ الوقت", formatDurationArabic(session.durationSeconds), Color(0xFF00E5FF), Modifier.weight(1f))
                DriveDetailBadge("📏 المسافة", String.format(Locale.US, "%.1f كم", session.distanceKm), Color(0xFF30D158), Modifier.weight(1f))
                DriveDetailBadge("🚀 أقصى سرعة", "${session.topSpeedKmh.roundToInt()} كم/س", Color(0xFFFFD600), Modifier.weight(1f))
                DriveDetailBadge("🏎️ المتوسط", "${session.averageSpeedKmh.roundToInt()} كم/س", Color(0xFFFF9500), Modifier.weight(1f))
            }
        }
    }
}

@OptIn(MapboxExperimental::class)
@Composable
fun DriveSessionMapDialog(
    session: DriveSessionEntity,
    onDismiss: () -> Unit
) {
    val startPoint = Point.fromLngLat(session.startLon, session.startLat)
    val endPoint = Point.fromLngLat(session.endLon, session.endLat)

    val routeGeometry = remember(session.geoJsonGeometry) {
        if (session.geoJsonGeometry.isNotBlank()) {
            try {
                LineString.fromJson(session.geoJsonGeometry)
            } catch (_: Exception) {
                LineString.fromLngLats(listOf(startPoint, endPoint))
            }
        } else {
            LineString.fromLngLats(listOf(startPoint, endPoint))
        }
    }

    val midLat = (session.startLat + session.endLat) / 2.0
    val midLon = (session.startLon + session.endLon) / 2.0

    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(Point.fromLngLat(midLon, midLat))
            zoom(11.8)
        }
    }

    val startBitmap = rememberTextBitmap("🟢 انطلاق: ${session.startPlaceName}", Color(0xFF121826))
    val endBitmap = rememberTextBitmap("🔴 وصول: ${session.endPlaceName}", Color(0xFF121826))

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF090D16),
            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // الهيدر علوي
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "مسار الرحلة الميدانية",
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "${session.startPlaceName} ← ${session.endPlaceName}",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            maxLines = 1
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                    }
                }

                // الخريطة التفاعلية لعرض المسار
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    MapboxMap(
                        modifier = Modifier.fillMaxSize(),
                        mapViewportState = mapViewportState,
                        style = { MapStyle(style = "mapbox://styles/mapbox/satellite-streets-v12") }
                    ) {
                        // 1. رسم الحد الداكن السفلي للمسار
                        PolylineAnnotation(points = routeGeometry.coordinates()) {
                            lineColor = Color(0xFF0F172A)
                            lineWidth = 14.0
                            lineOpacity = 0.90
                            lineJoin = LineJoin.ROUND
                        }

                        // 2. رسم مسار الرحلة الأزرق الملكي
                        PolylineAnnotation(points = routeGeometry.coordinates()) {
                            lineColor = Color(0xFF0284C7)
                            lineWidth = 8.0
                            lineOpacity = 1.0
                            lineJoin = LineJoin.ROUND
                        }

                        // 3. علامة الانطلاق الخضراء
                        PointAnnotation(point = startPoint) {
                            iconImage = IconImage(startBitmap)
                            iconAnchor = IconAnchor.CENTER
                            iconSize = 1.0
                        }

                        // 4. علامة الوصول الحمراء
                        PointAnnotation(point = endPoint) {
                            iconImage = IconImage(endBitmap)
                            iconAnchor = IconAnchor.CENTER
                            iconSize = 1.0
                        }
                    }
                }

                // الـ Footer لعرض إحصائيات الرحلة المعروضة
                Surface(
                    color = Color(0xFF121826),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DriveDetailBadge("⏱️ الوقت", formatDurationArabic(session.durationSeconds), Color(0xFF00E5FF), Modifier.weight(1f))
                        DriveDetailBadge("📏 المسافة", String.format(Locale.US, "%.1f كم", session.distanceKm), Color(0xFF30D158), Modifier.weight(1f))
                        DriveDetailBadge("🚀 أقصى سرعة", "${session.topSpeedKmh.roundToInt()} كم/س", Color(0xFFFFD600), Modifier.weight(1f))
                        DriveDetailBadge("🏎️ المتوسط", "${session.averageSpeedKmh.roundToInt()} كم/س", Color(0xFFFF9500), Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun DriveDetailBadge(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.10f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, fontSize = 8.5.sp, color = Color.LightGray)
            Spacer(Modifier.height(2.dp))
            Text(text = value, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}

fun formatDurationArabic(totalSeconds: Long): String {
    val totalMinutes = (totalSeconds / 60L).toInt()
    if (totalMinutes <= 0) return "أقل من دقيقة"
    
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60

    return when {
        hours == 0 -> "$minutes دقيقة"
        hours == 1 -> if (minutes > 0) "1 س و $minutes د" else "ساعة واحدة"
        else -> if (minutes > 0) "$hours س و $minutes د" else "$hours ساعات"
    }
}
