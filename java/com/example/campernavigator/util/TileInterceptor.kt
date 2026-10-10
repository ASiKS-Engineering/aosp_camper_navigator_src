package com.example.campernavigator.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

private const val ONLINE_TILEJSON_URL = "https://tiles.openfreemap.org/planet"
private const val ONLINE_BACKOFF_MS = 30_000L

/**
 * Intercepts MapLibre requests to http://offline.map/... and serves them from local MBTiles.
 */
class TileInterceptor(private val context: Context) : Interceptor {
    companion object {
        /**
         * Start immer mit dem lokalen Pack. Erst wenn die App das Netz als nutzbar meldet, wird auf
         * "online nachladen" geschaltet (OpenFreeMap-Kachel zuerst, lokale Kachel als Rueckfall).
         */
        @Volatile
        var onlineEnabled = false
    }
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        if (url.host == "offline.map") {
            try {
                val pathSegments = url.pathSegments
                
                // --- Sprite & Glyphs: aus den gebuendelten Assets (funktioniert ohne Netz) ---
                if (pathSegments.contains("fonts")) {
                    return glyphResponse(request, pathSegments)
                }

                if (pathSegments.contains("sprite")) {
                    return spriteResponse(request, pathSegments.last())
                }

                // --- Tile-Handling ---
                if (pathSegments.size < 4 || pathSegments[0] != "tiles") {
                    return errorResponse(request, "Invalid segments: ${pathSegments.joinToString("/")}")
                }

                val z = pathSegments[1].toInt()
                val x = pathSegments[2].toInt()
                val y = pathSegments[3].substringBefore(".").toInt()
                val regionId = url.queryParameter("region") ?: return errorResponse(request, "No region")

                // Nach dem lokalen Start: Online-Kachel (Gebaeude, Landnutzung, Wasser) bevorzugen,
                // das lokale Pack ist dort teils nur duenn besetzt. Bei Fehlern lokal.
                if (onlineEnabled) {
                    fetchOnlineTile(z, x, y)?.let { onlineData ->
                        return Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .header("Cache-Control", "max-age=86400")
                            .body(onlineData.toResponseBody("application/x-protobuf".toMediaTypeOrNull()))
                            .build()
                    }
                }

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
                    // A null body causes OkHttp interceptor to crash.
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

    private val onlineClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    @Volatile private var onlineTileTemplate: String? = null
    @Volatile private var onlineBlockedUntil = 0L

    private fun hasValidatedNetwork(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) {
            false
        }
    }

    /** Aktuelle, versionierte Tile-URL aus dem TileJSON des OpenFreeMap-Planeten. */
    @Synchronized
    private fun resolveOnlineTemplate(): String? {
        onlineTileTemplate?.let { return it }
        val tileJson = onlineClient.newCall(Request.Builder().url(ONLINE_TILEJSON_URL).build()).execute().use { r ->
            if (!r.isSuccessful) return null
            JSONObject(r.body?.string() ?: return null)
        }
        val template = tileJson.optJSONArray("tiles")?.optString(0)?.takeIf { it.contains("{z}") }
        onlineTileTemplate = template
        return template
    }

    /** null = nicht verfuegbar (kein Netz, Fehler, leere Kachel) -> lokale Kachel verwenden. */
    private fun fetchOnlineTile(z: Int, x: Int, y: Int): ByteArray? {
        if (System.currentTimeMillis() < onlineBlockedUntil || !hasValidatedNetwork()) return null
        return try {
            val template = resolveOnlineTemplate() ?: throw java.io.IOException("no tile template")
            val tileUrl = template.replace("{z}", z.toString()).replace("{x}", x.toString()).replace("{y}", y.toString())
            onlineClient.newCall(Request.Builder().url(tileUrl).build()).execute().use { r ->
                if (r.code == 404 || r.code == 204) return null
                if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
                r.body?.bytes()?.takeIf { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            // Kurz pausieren, damit bei "Netz da, Server nicht erreichbar" nicht jede Kachel wartet.
            onlineBlockedUntil = System.currentTimeMillis() + ONLINE_BACKOFF_MS
            onlineTileTemplate = null
            Log.w("TileInterceptor", "Online tile failed ($z/$x/$y): ${e.message}; using local tiles for ${ONLINE_BACKOFF_MS / 1000}s")
            null
        }
    }

    private fun assetResponse(request: okhttp3.Request, assetPath: String, contentType: String): Response? {
        val data = try {
            context.assets.open(assetPath).use { it.readBytes() }
        } catch (_: java.io.IOException) {
            return null
        }
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(data.toResponseBody(contentType.toMediaTypeOrNull()))
            .build()
    }

    /** sprite.json / sprite@2x.png -> ofm.json / ofm@2x.png in assets. */
    private fun spriteResponse(request: okhttp3.Request, fileName: String): Response {
        val isPng = fileName.endsWith(".png")
        val assetName = "ofm" + fileName.removePrefix("sprite")
        assetResponse(request, "offline_map/sprite/$assetName", if (isPng) "image/png" else "application/json")
            ?.let { return it }
        Log.w("TileInterceptor", "Sprite asset missing: $assetName")
        return errorResponse(request, "Sprite not found")
    }

    /**
     * Glyphs come from the bundled Noto Sans ranges, so labels also work without network at boot.
     * Unknown ranges get an EMPTY body (a valid, glyph-less protobuf). Invalid bytes would make MapLibre
     * fail the glyph range, and then no tile containing symbol layers is rendered (blank map).
     */
    private fun glyphResponse(request: okhttp3.Request, pathSegments: List<String>): Response {
        val fontIndex = pathSegments.indexOf("fonts")
        val fontName = pathSegments.getOrNull(fontIndex + 1)?.replace(' ', '_')
        val range = pathSegments.getOrNull(fontIndex + 2)
        if (fontName != null && range != null) {
            assetResponse(request, "offline_map/fonts/$fontName/$range", "application/x-protobuf")
                ?.let { return it }
        }
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(ByteArray(0).toResponseBody("application/x-protobuf".toMediaTypeOrNull()))
            .build()
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
            android.util.Log.e("TileInterceptor", "Decompression failed for $z/$x/$y (Magic: $hex): ${e.message}")
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
