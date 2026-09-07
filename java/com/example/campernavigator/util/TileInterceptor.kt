package com.example.campernavigator.util

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

/**
 * Intercepts MapLibre requests to http://offline.map/... and serves them from local MBTiles.
 */
class TileInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        if (url.host == "offline.map") {
            try {
                val pathSegments = url.pathSegments
                
                // --- Metadata-Handling: Dummy-Antworten für lokale Sprites ---
                if (pathSegments.contains("sprite")) {
                    val isPng = url.encodedPath.endsWith(".png")
                    val contentType = if (isPng) "image/png" else "application/json"
                    
                    val body = if (isPng) {
                        // Standard 1x1 Transparent PNG (67 bytes)
                        byteArrayOf(
                            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
                            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0D.toByte(), 0x49.toByte(), 0x48.toByte(), 0x44.toByte(), 0x52.toByte(), // IHDR
                            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
                            0x08.toByte(), 0x06.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x1F.toByte(), 0x15.toByte(), 0xC4.toByte(), 0x89.toByte(),
                            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0A.toByte(), 0x49.toByte(), 0x44.toByte(), 0x41.toByte(), 0x54.toByte(), // IDAT
                            0x78.toByte(), 0x9C.toByte(), 0x63.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x05.toByte(),
                            0x00.toByte(), 0x01.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x2D.toByte(), 0xB4.toByte(),
                            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x49.toByte(), 0x45.toByte(), 0x4E.toByte(), 0x44.toByte(), // IEND
                            0xAE.toByte(), 0x42.toByte(), 0x60.toByte(), 0x82.toByte()
                        )
                    } else {
                        "{}".toByteArray()
                    }

                    android.util.Log.v("TileInterceptor", "Dummy-Antwort ($contentType) für Metadata: ${url.encodedPath}")
                    return Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body.toResponseBody(contentType.toMediaTypeOrNull()))
                        .build()
                }

                // --- Tile-Handling ---
                if (pathSegments.size < 4 || pathSegments[0] != "tiles") {
                    return errorResponse(request, "Invalid segments: ${pathSegments.joinToString("/")}")
                }

                val z = pathSegments[1].toInt()
                val x = pathSegments[2].toInt()
                val y = pathSegments[3].substringBefore(".").toInt()
                val regionId = url.queryParameter("region") ?: return errorResponse(request, "No region")

                val mbtilesPath = LocalTileRegistry.getPath(regionId)
                if (mbtilesPath == null) {
                    android.util.Log.w("TileInterceptor", "Region not registered: $regionId")
                    return errorResponse(request, "Region not registered")
                }

                val tileData = MBTilesManager.getTile(mbtilesPath, z, x, y)
                if (tileData != null) {
                    val finalData = decompressIfNeeded(tileData, z, x, y)
                    
                    return Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Access-Control-Allow-Origin", "*")
                        .header("Cache-Control", "no-cache")
                        .body(finalData.toResponseBody("application/x-protobuf".toMediaTypeOrNull()))
                        .build()
                } else {
                    // WICHTIG: 404 statt 204 nutzen und IMMER einen Body mitgeben (auch wenn leer).
                    // Ein null-Body führt bei OkHttp zum Absturz des Interceptors.
                    return Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("".toResponseBody("text/plain".toMediaTypeOrNull()))
                        .build()
                }
            } catch (e: Exception) {
                android.util.Log.e("TileInterceptor", "Error serving tile: ${e.message}")
                return errorResponse(request, "Internal Error")
            }
        }

        return chain.proceed(request)
    }

    private fun decompressIfNeeded(data: ByteArray, z: Int, x: Int, y: Int): ByteArray {
        if (data.size < 2) return data
        
        val b0 = data[0].toUByte().toInt()
        val b1 = data[1].toUByte().toInt()

        return try {
            when {
                // GZIP: 1F 8B
                b0 == 0x1F && b1 == 0x8B -> {
                    GZIPInputStream(data.inputStream()).use { it.readBytes() }
                }
                // ZLIB: 78 9C, 78 01, 78 DA, 78 5E
                b0 == 0x78 && (b1 == 0x9C || b1 == 0x01 || b1 == 0xDA || b1 == 0x5E) -> {
                    InflaterInputStream(data.inputStream()).use { it.readBytes() }
                }
                else -> data
            }
        } catch (e: Exception) {
            val hex = data.take(8).joinToString(" ") { String.format("%02X", it) }
            android.util.Log.e("TileInterceptor", "Dekomprimierung fehlgeschlagen für $z/$x/$y (Magic: $hex): ${e.message}")
            data
        }
    }

    private fun errorResponse(request: okhttp3.Request, message: String): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(404)
            .message("Error") // Statische Nachricht, um HTTP-Header-Probleme zu vermeiden
            .header("X-Error-Message", message.filter { it.code in 32..126 }) // Nur sichere Zeichen
            .body(message.toByteArray().toResponseBody("text/plain".toMediaTypeOrNull()))
            .build()
    }
}
