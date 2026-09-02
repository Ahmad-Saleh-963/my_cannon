package com.ahmadsaleh.map.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.mapbox.common.TileRegionLoadOptions
import com.mapbox.common.TileStore
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.OfflineManager
import com.mapbox.maps.TilesetDescriptorOptions
import java.util.Locale

data class ProvinceDownloadTask(
    val name: String,
    val nameAr: String,
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
    val maxZoom: Int = 16
)

class MapDownloadService : Service() {

    private val CHANNEL_ID = "MapDownloadChannel_v2"
    private val NOTIFICATION_ID = 888

    private val tileStore: TileStore by lazy {
        MapboxOfflineRegistry.getTileStore(this)
    }
    private val offlineManager: OfflineManager by lazy {
        MapboxOfflineRegistry.getOfflineManager()
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val downloadQueue = ArrayDeque<ProvinceDownloadTask>()
    private var isDownloading = false
    private var currentTask: ProvinceDownloadTask? = null

    private var lastUpdateTime = 0L
    private var lastResourceSize = 0L

    override fun onCreate() {
        super.onCreate()

        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyCannon:DownloadWakeLock")
            wakeLock?.acquire(30 * 60 * 1000L)

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MyCannon:WifiLock")
            wifiLock?.acquire()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        requestHighPriorityNetwork()
        createNotificationChannel()
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    private fun requestHighPriorityNetwork() {
        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {})
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: "ADD_TASK"

        if (action == "STOP_SERVICE") {
            cleanupAndStop()
            return START_NOT_STICKY
        }

        val name = intent?.getStringExtra("PROVINCE_NAME")
        val nameAr = intent?.getStringExtra("PROVINCE_NAME_AR") ?: name ?: ""
        val west = intent?.getDoubleExtra("WEST", 0.0) ?: 0.0
        val south = intent?.getDoubleExtra("SOUTH", 0.0) ?: 0.0
        val east = intent?.getDoubleExtra("EAST", 0.0) ?: 0.0
        val north = intent?.getDoubleExtra("NORTH", 0.0) ?: 0.0
        val maxZoom = intent?.getIntExtra("MAX_ZOOM", 16) ?: 16

        if (!name.isNullOrBlank()) {
            val task = ProvinceDownloadTask(name, nameAr, west, south, east, north, maxZoom)
            if (!downloadQueue.any { it.name == name } && currentTask?.name != name) {
                downloadQueue.addLast(task)
            }
        }

        startForegroundNotification("جاري تحضير تحميل الخرائط أوفلاين...", 0)
        processNextTask()

        return START_STICKY
    }

    private fun processNextTask() {
        if (isDownloading) return

        if (downloadQueue.isEmpty()) {
            showCompletionNotification("✅ تم اكتمال تحميل كافة الخرائط أوفلاين بنجاح")
            cleanupAndStop()
            return
        }

        val task = downloadQueue.removeFirst()
        currentTask = task

        if (!isNetworkAvailable()) {
            val prefs = getSharedPreferences("map_download_prefs", Context.MODE_PRIVATE)
            prefs.edit {
                remove("downloading_${task.name}")
                putString("status_${task.name}", "لا يوجد اتصال بالإنترنت")
            }
            Toast.makeText(this, "لا يوجد اتصال بالإنترنت للبدء بالتحميل", Toast.LENGTH_SHORT).show()
            currentTask = null
            processNextTask()
            return
        }

        isDownloading = true
        lastUpdateTime = System.currentTimeMillis()
        lastResourceSize = 0L

        startMapDownload(task)
    }

    private fun startMapDownload(task: ProvinceDownloadTask) {
        val prefs = getSharedPreferences("map_download_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean("downloading_${task.name}", true)
            putFloat("progress_${task.name}", 0f)
            remove("status_${task.name}")
        }

        val polygon = Polygon.fromLngLats(listOf(listOf(
            Point.fromLngLat(task.west, task.south),
            Point.fromLngLat(task.east, task.south),
            Point.fromLngLat(task.east, task.north),
            Point.fromLngLat(task.west, task.north),
            Point.fromLngLat(task.west, task.south)
        )))

        val mapDescriptor = offlineManager.createTilesetDescriptor(
            TilesetDescriptorOptions.Builder()
                .styleURI("mapbox://styles/mapbox/satellite-streets-v12")
                .minZoom(0)
                .maxZoom(task.maxZoom.toByte())
                .build()
        )

        tileStore.loadTileRegion(
            task.name,
            TileRegionLoadOptions.Builder()
                .geometry(polygon)
                .descriptors(listOf(mapDescriptor))
                .acceptExpired(true)
                .build(),
            { progress ->
                val currentTime = System.currentTimeMillis()
                val timeDiff = (currentTime - lastUpdateTime) / 1000.0
                val sizeDiff = progress.completedResourceSize - lastResourceSize

                val speedStr = if (timeDiff > 0.8) {
                    val speedKB = (sizeDiff / 1024.0) / timeDiff
                    lastUpdateTime = currentTime
                    lastResourceSize = progress.completedResourceSize
                    if (speedKB > 1024) String.format(Locale.US, "%.1f MB/s", speedKB / 1024.0)
                    else String.format(Locale.US, "%.1f KB/s", speedKB)
                } else {
                    prefs.getString("speed_${task.name}", "0 KB/s") ?: "0 KB/s"
                }

                val currentP = prefs.getFloat("progress_${task.name}", 0f)

                val rawP = when {
                    progress.requiredResourceCount > 0 -> {
                        (progress.completedResourceCount.toFloat() / progress.requiredResourceCount.toFloat()).coerceIn(0f, 1f)
                    }
                    else -> 0f
                }

                val p = if (rawP > 0f) maxOf(currentP, rawP) else currentP

                val percentInt = (p * 100).toInt().coerceIn(0, 100)
                val sizeMB = String.format(Locale.US, "%.1f MB", progress.completedResourceSize / (1024.0 * 1024.0))

                prefs.edit {
                    putFloat("progress_${task.name}", p)
                    putString("speed_${task.name}", speedStr)
                    putString("size_${task.name}", sizeMB)
                    putLong("completed_${task.name}", progress.completedResourceCount)
                    putLong("total_${task.name}", progress.requiredResourceCount)
                }

                val notifContent = "تحميل: ${task.nameAr} • $percentInt% • $speedStr ($sizeMB)"
                updateNotification(notifContent, percentInt)
            },
            { expected ->
                isDownloading = false
                prefs.edit {
                    remove("downloading_${task.name}")
                    if (expected.isValue) {
                        putFloat("progress_${task.name}", 1f)
                        remove("status_${task.name}")
                    } else {
                        putString("status_${task.name}", "فشل التحميل - حاول مجدداً")
                    }
                }
                currentTask = null
                processNextTask()
            }
        )
    }

    private fun startForegroundNotification(title: String, progress: Int) {
        val notification = createNotification(title, progress)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    private fun updateNotification(contentText: String, progress: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            manager.notify(NOTIFICATION_ID, createNotification(contentText, progress))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showCompletionNotification(message: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            val notif = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("خريطة الميدان أوفلاين")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            manager.notify(NOTIFICATION_ID + 1, notif)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "تنزيل الخرائط أوفلاين بالخلفية",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "عرض تقدم ونسبة تحميل الخرائط والمحافظات أوفلاين بالخلفية"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String, progress: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("تنزيل الخرائط الميدانية أوفلاين 📡")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress.coerceIn(0, 100), progress == 0)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun cleanupAndStop() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            stopForeground(STOP_FOREGROUND_DETACH)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
