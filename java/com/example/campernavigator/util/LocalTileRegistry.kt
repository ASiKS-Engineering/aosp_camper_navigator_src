package com.example.campernavigator.util

import java.util.concurrent.ConcurrentHashMap

/**
 * Registry to map Region IDs to absolute MBTiles paths.
 * Used by TileInterceptor to resolve requests without passing long paths in URLs.
 */
object LocalTileRegistry {
    private val paths = ConcurrentHashMap<String, String>()

    fun register(regionId: String, path: String) {
        paths[regionId] = path
    }

    fun getPath(regionId: String): String? {
        return paths[regionId]
    }

    fun clear() {
        paths.clear()
    }
}
