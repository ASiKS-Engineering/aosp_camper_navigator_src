package com.example.campernavigator.util

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages MBTiles SQLite connections with caching.
 */
object MBTilesManager {
    private const val TAG = "MBTilesManager"
    private val dbCache = ConcurrentHashMap<String, SQLiteDatabase>()
    private val maxZoomCache = ConcurrentHashMap<String, Int>()
    private val minZoomCache = ConcurrentHashMap<String, Int>()

    fun getTile(path: String, z: Int, x: Int, y: Int): ByteArray? {
        return try {
            val db = getDatabase(path) ?: return null
            if (!db.isOpen) {
                dbCache.remove(path)
                maxZoomCache.remove(path)
                minZoomCache.remove(path)
                return null
            }

            val minDbZoom = getMinZoom(db, path)
            val maxDbZoom = getMaxZoom(db, path)
            val effectiveZ: Int
            val effectiveX: Int
            val effectiveY: Int

            if (z < minDbZoom) {
                val diff = minDbZoom - z
                effectiveZ = minDbZoom
                effectiveX = x shl diff
                effectiveY = y shl diff
            } else if (z > maxDbZoom) {
                val diff = z - maxDbZoom
                effectiveZ = maxDbZoom
                effectiveX = x shr diff
                effectiveY = y shr diff
            } else {
                effectiveZ = z
                effectiveX = x
                effectiveY = y
            }

            val tableName = getTilesTableName(db) ?: "tiles"
            
            // MBTiles spec uses TMS (Tile Map Service) indexing for rows, but some tools use XYZ.
            val tmsY = (1 shl effectiveZ) - 1 - effectiveY
            val rowsToTry = listOf(tmsY, effectiveY)

            for (rowVal in rowsToTry) {
                val cursor = db.rawQuery(
                    "SELECT tile_data FROM $tableName WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?",
                    arrayOf(effectiveZ.toString(), effectiveX.toString(), rowVal.toString())
                )
                cursor.use {
                    if (it.moveToFirst()) {
                        val data = it.getBlob(0)
                        if (data != null && data.isNotEmpty()) {
                            return data
                        }
                    }
                }
            }

            Log.w(TAG, "Tile not found: requested $z/$x/$y (effective $effectiveZ/$effectiveX/$effectiveY) in $path")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error reading tile $z/$x/$y from $path: ${e.message}")
            null
        }
    }

    private fun getMaxZoom(db: SQLiteDatabase, path: String): Int {
        return maxZoomCache.getOrPut(path) {
            try {
                val cursor = db.rawQuery("SELECT value FROM metadata WHERE name = 'maxzoom'", null)
                val maxZoomFromMeta = cursor.use {
                    if (it.moveToFirst()) it.getString(0)?.toIntOrNull() else null
                }
                if (maxZoomFromMeta != null) return@getOrPut maxZoomFromMeta

                val cursor2 = db.rawQuery("SELECT MAX(zoom_level) FROM tiles", null)
                val maxZoomFromTable = cursor2.use {
                    if (it.moveToFirst()) it.getInt(0) else 14
                }
                maxZoomFromTable.takeIf { it > 0 } ?: 14
            } catch (_: Exception) {
                14
            }
        }
    }

    private fun getMinZoom(db: SQLiteDatabase, path: String): Int {
        return minZoomCache.getOrPut(path) {
            try {
                val cursor = db.rawQuery("SELECT value FROM metadata WHERE name = 'minzoom'", null)
                val minZoomFromMeta = cursor.use {
                    if (it.moveToFirst()) it.getString(0)?.toIntOrNull() else null
                }
                if (minZoomFromMeta != null) return@getOrPut minZoomFromMeta

                val cursor2 = db.rawQuery("SELECT MIN(zoom_level) FROM tiles", null)
                val minZoomFromTable = cursor2.use {
                    if (it.moveToFirst()) it.getInt(0) else 0
                }
                minZoomFromTable
            } catch (_: Exception) {
                0
            }
        }
    }

    fun getMinZoom(path: String): Int {
        return try {
            val db = getDatabase(path) ?: return 0
            getMinZoom(db, path)
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine min zoom for $path: ${e.message}")
            0
        }
    }

    private fun getTilesTableName(db: SQLiteDatabase): String? {
        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type IN ('table', 'view') AND name IN ('tiles', 'map')",
            null
        )
        val names = cursor.use {
            val list = mutableListOf<String>()
            while (it.moveToNext()) list.add(it.getString(0))
            list
        }
        // 'tiles' (Tabelle oder View) enthaelt immer tile_data. 'map' hat nur Verweise auf 'images'.
        return if ("tiles" in names) "tiles" else names.firstOrNull()
    }

    /** Hoechster Zoom der MBTiles-Datei (Metadaten bzw. Tabelle), null falls nicht lesbar. */
    fun getMaxZoom(path: String): Int? {
        return try {
            val db = getDatabase(path) ?: return null
            getMaxZoom(db, path)
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine max zoom for $path: ${e.message}")
            null
        }
    }

    private fun getDatabase(path: String): SQLiteDatabase? {
        val file = File(path)
        if (!file.exists()) {
            Log.w(TAG, "MBTiles file does not exist: $path")
            dbCache.remove(path)?.close()
            maxZoomCache.remove(path)
            return null
        }

        return dbCache.getOrPut(path) {
            try {
                val db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)
                logMetadata(db, path)
                db
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open MBTiles DB: $path", e)
                throw e
            }
        }
    }

    private fun logMetadata(db: SQLiteDatabase, path: String) {
        try {
            val cursor = db.rawQuery("SELECT name, value FROM metadata", null)
            cursor.use {
                val metadata = mutableMapOf<String, String>()
                while (it.moveToNext()) {
                    metadata[it.getString(0)] = it.getString(1)
                }
                Log.i(TAG, "MBTiles Metadata for $path: $metadata")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read metadata from $path: ${e.message}")
        }
    }

    fun closeAll() {
        dbCache.values.forEach { it.close() }
        dbCache.clear()
        maxZoomCache.clear()
    }
}
