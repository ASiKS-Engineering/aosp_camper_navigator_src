package com.example.campernavigator.util

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileOutputStream

class TilesProvider : ContentProvider() {
    
    companion object {
        const val AUTHORITY = "com.example.campernavigator.tiles"
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        // Expected URI: content://com.example.campernavigator.tiles/{z}/{x}/{y}.pbf?path=/absolute/path/to/map.mbtiles
        val pathSegments = uri.pathSegments
        if (pathSegments.size < 3) {
            Log.w("TilesProvider", "Invalid URI format: $uri")
            return null
        }
        
        return try {
            val z = pathSegments[0].toInt()
            val x = pathSegments[1].toInt()
            val yWithExt = pathSegments[2]
            val y = yWithExt.substringBefore(".").toInt()
            
            val mbtilesPath = uri.getQueryParameter("path") ?: return null
            Log.d("TilesProvider", "Anfrage für Kachel: $z/$x/$y (Pfad: $mbtilesPath)")
            
            val tileData = MBTilesManager.getTile(mbtilesPath, z, x, y) ?: return null
            
            openPipeHelper(uri, "application/x-protobuf", null, tileData) { pipe, _, _, _, data ->
                try {
                    FileOutputStream(pipe.fileDescriptor).use { it.write(data as ByteArray) }
                } catch (e: Exception) {
                    Log.e("TilesProvider", "Error writing tile to pipe for $uri", e)
                }
            }
        } catch (e: Exception) {
            Log.e("TilesProvider", "Error processing tile request for $uri", e)
            null
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = "application/x-protobuf"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
