package com.ahmadsaleh.map.service

import android.content.Context
import com.mapbox.common.TileStore
import com.mapbox.maps.OfflineManager
import java.io.File

object MapboxOfflineRegistry {
    @Volatile
    private var tileStoreInstance: TileStore? = null

    @Volatile
    private var offlineManagerInstance: OfflineManager? = null

    fun getMapPath(context: Context): String {
        val folder = File(context.filesDir, "mymap")
        if (!folder.exists()) folder.mkdirs()
        return folder.absolutePath
    }

    fun getTileStore(context: Context): TileStore {
        return tileStoreInstance ?: synchronized(this) {
            tileStoreInstance ?: TileStore.create(getMapPath(context)).also {
                tileStoreInstance = it
            }
        }
    }

    fun getOfflineManager(): OfflineManager {
        return offlineManagerInstance ?: synchronized(this) {
            offlineManagerInstance ?: OfflineManager().also {
                offlineManagerInstance = it
            }
        }
    }
}
