package com.example.my_cannon.service

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
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.mapbox.common.TileStore
import com.mapbox.common.TileRegionLoadOptions
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.OfflineManager
import com.mapbox.maps.TilesetDescriptorOptions
import java.io.File

class MapDownloadService : Service() {

    private val CHANNEL_ID = "MapDownloadChannel"
    private val NOTIFICATION_ID = 101
    
    private lateinit var tileStore: TileStore
    private lateinit var offlineManager: OfflineManager
    
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyCannon:DownloadWakeLock")
        wakeLock?.acquire(10 * 60 * 1000L)

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MyCannon:WifiLock")
        wifiLock?.acquire()

        requestHighPriorityNetwork()

        // إنشاء المسار باحترافية مع معالجة احتمالية عدم وجود المجلد الرئيسي
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloads.exists()) downloads.mkdirs()
        val folder = File(downloads, "mymap")
        if (!folder.exists()) folder.mkdirs()
        
        val path = if (folder.exists()) folder.absolutePath else getExternalFilesDir(null)?.absolutePath ?: filesDir.absolutePath
        
        tileStore = TileStore.create(path)
        offlineManager = OfflineManager()
        createNotificationChannel()
    }

    private fun requestHighPriorityNetwork() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {})
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra("MODE") ?: "SINGLE"
        
        if (mode == "SMART_AUTO") {
            val lat = intent?.getDoubleExtra("LAT", 0.0) ?: 0.0
            val lon = intent?.getDoubleExtra("LON", 0.0) ?: 0.0
            if (lat != 0.0 && lon != 0.0) {
                startForegroundNotification("الجاهزية التلقائية")
                startSmartAutoDownload(lat, lon)
            } else {
                stopSelf()
            }
        } else {
            val provinceName = intent?.getStringExtra("PROVINCE_NAME") ?: return START_NOT_STICKY
            val w = intent.getDoubleExtra("WEST", 0.0)
            val s = intent.getDoubleExtra("SOUTH", 0.0)
            val e = intent.getDoubleExtra("EAST", 0.0)
            val n = intent.getDoubleExtra("NORTH", 0.0)
            
            startForegroundNotification("تحميل: $provinceName")
            downloadMap(provinceName, w, s, e, n, 14)
        }
        
        return START_STICKY
    }

    private fun startSmartAutoDownload(lat: Double, lon: Double) {
        // 1. تحميل النطاق الحالي (10 كم)
        val offset = 0.1
        downloadMap("MyArea", lon - offset, lat - offset, lon + offset, lat + offset, 14)
        
        // 2. تحميل سوريا كاملة (زوم منخفض)
        downloadMap("SyriaLow", 35.0, 32.0, 42.5, 37.5, 10)
    }

    private fun startForegroundNotification(title: String) {
        val notification = createNotification(title, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun downloadMap(name: String, w: Double, s: Double, e: Double, n: Double, maxZoom: Int) {
        val polygon = Polygon.fromLngLats(listOf(listOf(
            Point.fromLngLat(w, s), Point.fromLngLat(e, s),
            Point.fromLngLat(e, n), Point.fromLngLat(w, n), Point.fromLngLat(w, s)
        )))

        val mapDescriptor = offlineManager.createTilesetDescriptor(
            TilesetDescriptorOptions.Builder()
                .styleURI("mapbox://styles/mapbox/satellite-streets-v12")
                .minZoom(0)
                .maxZoom(maxZoom.toByte())
                .build()
        )

        tileStore.loadTileRegion(
            name,
            TileRegionLoadOptions.Builder()
                .geometry(polygon)
                .descriptors(listOf(mapDescriptor))
                .acceptExpired(true)
                .build(),
            { progress ->
                val p = (progress.completedResourceCount.toFloat() / progress.requiredResourceCount.toFloat() * 100).toInt()
                updateNotification(name, p)
            },
            { expected ->
                if (expected.isError && name != "MyArea") cleanupAndStop()
                // if it's smart auto, we keep going for other regions
            }
        )
    }

    private fun cleanupAndStop() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (wifiLock?.isHeld == true) wifiLock?.release()
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "مزامنة الخرائط", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, progress: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("جاري تحديث الخرائط الميدانية... $progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()
    }

    private fun updateNotification(name: String, progress: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification("تحديث: $name", progress))
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
