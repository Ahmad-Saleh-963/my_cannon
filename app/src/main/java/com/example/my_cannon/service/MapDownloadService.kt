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
        
        // 1. تفعيل الـ WakeLock لمنع المعالج من الخمول (Full Power CPU)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyCannon:DownloadWakeLock")
        wakeLock?.acquire()

        // 2. تفعيل الـ WifiLock لضمان أقصى سرعة للـ Wifi وعدم الدخول في وضع توفير الطاقة
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MyCannon:WifiLock")
        wifiLock?.acquire()

        // 3. طلب أولوية عالية للشبكة من النظام
        requestHighPriorityNetwork()

        val path = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "mymap").absolutePath
        tileStore = TileStore.create(path)
        offlineManager = OfflineManager()
        createNotificationChannel()
    }

    private fun requestHighPriorityNetwork() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()
        
        connectivityManager.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
            // النظام سيعطي الأولوية لهذا الطلب لضمان استقرار وسرعة الاتصال
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val provinceName = intent?.getStringExtra("PROVINCE_NAME") ?: return START_NOT_STICKY
        val west = intent.getDoubleExtra("WEST", 0.0)
        val south = intent.getDoubleExtra("SOUTH", 0.0)
        val east = intent.getDoubleExtra("EAST", 0.0)
        val north = intent.getDoubleExtra("NORTH", 0.0)

        // تفعيل وضع "الأولوية المطلقة" (Foreground Service Priority)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification(provinceName, 0), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, createNotification(provinceName, 0))
        }
        
        downloadMap(provinceName, west, south, east, north)
        
        return START_STICKY
    }

    private fun downloadMap(name: String, w: Double, s: Double, e: Double, n: Double) {
        val polygon = Polygon.fromLngLats(listOf(listOf(
            Point.fromLngLat(w, s), Point.fromLngLat(e, s),
            Point.fromLngLat(e, n), Point.fromLngLat(w, n), Point.fromLngLat(w, s)
        )))

        val mapDescriptor = offlineManager.createTilesetDescriptor(
            TilesetDescriptorOptions.Builder()
                .styleURI("mapbox://styles/mapbox/satellite-streets-v12")
                .minZoom(0)
                .maxZoom(14)
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
                if (expected.isError) {
                    cleanupAndStop()
                } else {
                    updateNotification("$name - اكتمل التجميل", 100)
                    cleanupAndStop()
                }
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
            val channel = NotificationChannel(CHANNEL_ID, "تحميل الخرائط توربو", NotificationManager.IMPORTANCE_HIGH)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(name: String, progress: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("وضع الأولوية القصوى: $name")
            .setContentText("يتم الآن سحب كامل سرعة النت المتوفرة... $progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX) // أعلى أولوية معالجة
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(name: String, progress: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(name, progress))
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
