package com.example.my_cannon.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.my_cannon.R
import com.mapbox.common.TileStore
import com.mapbox.common.TileRegionLoadOptions
import com.mapbox.geojson.BoundingBox
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.OfflineManager
import com.mapbox.maps.TilesetDescriptorOptions

class MapDownloadService : Service() {

    private val CHANNEL_ID = "MapDownloadChannel"
    private val NOTIFICATION_ID = 101
    
    private lateinit var tileStore: TileStore
    private lateinit var offlineManager: OfflineManager

    override fun onCreate() {
        super.onCreate()
        tileStore = TileStore.create()
        offlineManager = OfflineManager()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val provinceName = intent?.getStringExtra("PROVINCE_NAME") ?: return START_NOT_STICKY
        val west = intent.getDoubleExtra("WEST", 0.0)
        val south = intent.getDoubleExtra("SOUTH", 0.0)
        val east = intent.getDoubleExtra("EAST", 0.0)
        val north = intent.getDoubleExtra("NORTH", 0.0)

        startForeground(NOTIFICATION_ID, createNotification(provinceName, 0))
        
        downloadMap(provinceName, west, south, east, north)
        
        return START_STICKY
    }

    private fun downloadMap(name: String, w: Double, s: Double, e: Double, n: Double) {
        val polygon = Polygon.fromLngLats(
            listOf(
                listOf(
                    Point.fromLngLat(w, s),
                    Point.fromLngLat(e, s),
                    Point.fromLngLat(e, n),
                    Point.fromLngLat(w, n),
                    Point.fromLngLat(w, s)
                )
            )
        )

        // زوم 14 لتصغير الحجم بشكل كبير مع الحفاظ على الجودة
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
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    updateNotification("$name - اكتمل", 100)
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                }
            }
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "تحميل الخرائط",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(name: String, progress: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("جاري تحميل خريطة: $name")
            .setContentText("يتم الآن جلب البيانات للاستخدام أوفلاين... $progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(name: String, progress: Int) {
        val notification = createNotification(name, progress)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
