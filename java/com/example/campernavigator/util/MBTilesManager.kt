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

    fun getTile(path: String, z: Int, x: Int, y: Int): ByteArray? {
        return try {
            val db = getDatabase(path) ?: return null
            if (!db.isOpen) {
                dbCache.remove(path)
                return null
            }
            
            // MBTiles spec uses TMS (Tile Map Service) indexing for rows.
            // Converting XYZ to TMS:
            val tmsY = (1 shl z) - 1 - y
            
            // Dynamische Tabellenerkennung: 'tiles' (Standard) oder 'map' (Alternative)
            val tableName = if (isTableExisting(db, "tiles")) "tiles" else "map"
            
            val cursor = db.rawQuery(
                "SELECT tile_data FROM $tableName WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?",
                arrayOf(z.toString(), x.toString(), tmsY.toString())
            )
            
            cursor.use {
                if (it.moveToFirst()) {
                    it.getBlob(0)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading tile $z/$x/$y from $path: ${e.message}")
            null
        }
    }

    private fun getDatabase(path: String): SQLiteDatabase? {
        val file = File(path)
        if (!file.exists()) {
            dbCache.remove(path)?.close()
            return null
        }

        return dbCache.getOrPut(path) {
            try {
                SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open MBTiles DB: $path", e)
                throw e
            }
        }
    }

    fun closeAll() {
        dbCache.values.forEach { it.close() }
        dbCache.clear()
    }

    private fun isTableExisting(db: SQLiteDatabase, tableName: String): Boolean {
        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type IN ('table', 'view') AND name = ?",
            arrayOf(tableName)
        )
        return cursor.use { it.count > 0 }
    }
}
