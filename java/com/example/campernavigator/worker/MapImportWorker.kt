package com.example.campernavigator.worker

import android.R
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.campernavigator.util.ZipUtil
import com.example.campernavigator.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MapImportWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    companion object {
        const val KEY_REGION_ID = "REGION_ID"
        const val KEY_URI = "URI"
        const val KEY_PROGRESS = "PROGRESS"
        const val KEY_ERROR_MESSAGE = "ERROR_MESSAGE"
        const val KEY_DISCOVERED_ID = "DISCOVERED_ID"
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val channelId = "MAP_IMPORT"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Karten Import",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Karten-Import")
            .setContentText("Turbo-Installation (Pi 5 Safe)...")
            .setSmallIcon(R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
        return ForegroundInfo(43, notification, 
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
    }

    private fun getDirectFileFromUri(context: Context, uri: Uri): File? {
        if (uri.scheme == "file") {
            return uri.path?.let { File(it) }
        }
        if (uri.scheme == "content") {
            val docId = try {
                android.provider.DocumentsContract.getDocumentId(uri)
            } catch (e: Exception) {
                uri.path
            } ?: return null

            val split = docId.split(":")
            if (split.size >= 2) {
                val type = split[0]
                val relativePath = split[1]

                val possiblePaths = mutableListOf<String>()
                if ("primary".equals(type, ignoreCase = true)) {
                    possiblePaths.add("/storage/emulated/0/$relativePath")
                    possiblePaths.add("/sdcard/$relativePath")
                } else {
                    possiblePaths.add("/storage/$type/$relativePath")
                    possiblePaths.add("/mnt/media_rw/$type/$relativePath")
                    possiblePaths.add("/mnt/user/0/primary/$relativePath")
                }

                for (p in possiblePaths) {
                    val f = File(p)
                    if (f.exists() && f.canRead()) {
                        return f
                    }
                }
            }
        }
        return null
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            FileLogger.log("MapImportWorker: Could not start foreground service: ${e.message}")
        }

        val regionId = inputData.getString(KEY_REGION_ID) ?: "Map"
        val uriString = inputData.getString(KEY_URI) ?: return@withContext Result.failure()
        
        val lockFile = File(applicationContext.cacheDir, "import_map.lock")
        val internalRoutingDir = File(applicationContext.filesDir, "routing")
        val tempExtractDir = File(internalRoutingDir, "tmp_safe_extract")
        // MOVE tempZip to filesDir as cacheDir might have size quotas
        val tempZip = File(applicationContext.filesDir, "import_turbo.zip")

        // 1. LOCK-MECHANISMUS: Abbrechen, falls bereits eine Instanz läuft
        if (lockFile.exists() && System.currentTimeMillis() - lockFile.lastModified() < 3600000) {
            FileLogger.log("MapImportWorker: Import process already running. Killing duplicate worker.")
            return@withContext Result.failure() // Nicht Result.success() aufrufen, da WorkManager sonst den Task als beendet markiert!
        }
        lockFile.createNewFile()

        return@withContext try {
            val finalDir = File(internalRoutingDir, regionId)
            
            // Clean up leftover extraction dir; keep a valid cached ZIP for resume
            tempExtractDir.deleteRecursively()
            tempExtractDir.mkdirs()

            val hasResumableZip = tempZip.exists() && tempZip.length() > 4000000000L && ZipUtil.checkIntegrity(tempZip)
            if (tempZip.exists() && !hasResumableZip) tempZip.delete()

            val usableSpaceMb = internalRoutingDir.usableSpace / (1024 * 1024)
            FileLogger.log("MapImportWorker: Storage space available on /data: ${usableSpaceMb} MB")

            // SOFORT-ERFOLG: Falls die Karte schon da ist
            if (finalDir.exists() && File(finalDir, "nodes").exists() && File(finalDir, "edges").exists()) {
                FileLogger.log("MapImportWorker: Map $regionId already complete. Success!")
                setProgressAsync(workDataOf(KEY_PROGRESS to 100))
                return@withContext Result.success(workDataOf(KEY_DISCOVERED_ID to regionId))
            }

            // DIRECT ZIP EXTRACTION FROM NVME: Use ZipFile for large ZIP64 archives directly on NVMe
            val parsedUri = Uri.parse(uriString)
            val directFile = getDirectFileFromUri(applicationContext, parsedUri)

            if (directFile != null && directFile.exists() && directFile.canRead()) {
                FileLogger.log("MapImportWorker: Direct ZIP64 extraction from NVMe file: ${directFile.absolutePath}")
                if (tempZip.exists()) tempZip.delete()
                if (!ZipUtil.checkIntegrity(directFile)) throw Exception("ZIP integrity check failed.")
                ZipUtil.unzip(directFile, tempExtractDir) { progress ->
                    lockFile.setLastModified(System.currentTimeMillis())
                    setProgressAsync(workDataOf(KEY_PROGRESS to progress))
                }
            } else {
                if (hasResumableZip) {
                    FileLogger.log("MapImportWorker: Valid ZIP found in cache. Skipping copy.")
                } else {
                    FileLogger.log("MapImportWorker: ContentResolver fallback copy & extract...")
                    copyFromContentResolver(parsedUri, tempZip, lockFile)
                }
                setProgressAsync(workDataOf(KEY_PROGRESS to 30))
                if (!ZipUtil.checkIntegrity(tempZip)) throw Exception("ZIP integrity check failed.")

                ZipUtil.unzip(tempZip, tempExtractDir) { progress ->
                    lockFile.setLastModified(System.currentTimeMillis())
                    setProgressAsync(workDataOf(KEY_PROGRESS to (30 + (progress * 70 / 100))))
                }
            }

            // 5. FINALISIERUNG
            fun findDataRoot(dir: File): File? {
                if (File(dir, "nodes").exists()) return dir
                return dir.listFiles()?.filter { it.isDirectory }?.firstNotNullOfOrNull { findDataRoot(it) }
            }

            val dataRoot = findDataRoot(tempExtractDir) ?: throw Exception("Keine Routing-Daten gefunden.")
            
            // Suche nach mbtiles BEVOR wir irgendwas verschieben
            val allFiles = mutableListOf<File>()
            fun scan(d: File) { d.listFiles()?.forEach { if(it.isDirectory) scan(it) else allFiles.add(it) } }
            scan(tempExtractDir)
            val mbtilesFile = allFiles.find { it.name.endsWith(".mbtiles", ignoreCase = true) }

            finalDir.deleteRecursively()
            finalDir.mkdirs()
            
            // 1. MBTiles verschieben (Absolute Prio)
            if (mbtilesFile != null && mbtilesFile.exists()) {
                val targetMbtiles = File(finalDir, "map.mbtiles")
                FileLogger.log("MapImportWorker: Moving MBTiles ${mbtilesFile.name} to ${targetMbtiles.absolutePath}")
                if (!mbtilesFile.renameTo(targetMbtiles)) {
                    mbtilesFile.copyTo(targetMbtiles, true)
                }
            }

            // 2. Routing-Daten verschieben
            FileLogger.log("MapImportWorker: Moving routing data from ${dataRoot.name}...")
            dataRoot.listFiles()?.forEach { file ->
                if (!file.name.endsWith(".mbtiles", ignoreCase = true)) {
                    val target = File(finalDir, file.name)
                    if (!file.renameTo(target)) file.copyRecursively(target, true)
                }
            }
            
            FileLogger.log("IMPORT COMPLETED: $regionId")
            if (tempZip.exists()) tempZip.delete()
            Result.success(workDataOf(KEY_DISCOVERED_ID to regionId))
        } catch (e: Exception) {
            FileLogger.log("IMPORT ERROR: ${e.message}", "ERROR")
            Result.failure(workDataOf(KEY_ERROR_MESSAGE to (e.message ?: "Unbekannter Fehler")))
        } finally {
            lockFile.delete()
            tempExtractDir.deleteRecursively()
        }
    }

    private suspend fun copyFromContentResolver(uri: Uri, target: File, lockFile: File) {
        var pfd: android.os.ParcelFileDescriptor? = null
        try {
            pfd = applicationContext.contentResolver.openFileDescriptor(uri, "r")
                ?: throw Exception("Quelldatei konnte nicht geoeffnet werden.")
            val totalSize = pfd.statSize
            FileInputStream(pfd.fileDescriptor).use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L
                    var syncCounter = 0L
                    var lastProgressTime = System.currentTimeMillis()

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        syncCounter += bytesRead

                        if (syncCounter >= 100 * 1024 * 1024) {
                            output.flush()
                            output.fd.sync()
                            syncCounter = 0
                            delay(2)
                        }

                        val now = System.currentTimeMillis()
                        if (totalSize > 0 && now - lastProgressTime >= 400L) {
                            val currentProgress = (totalRead * 30 / totalSize).toInt().coerceIn(0, 30)
                            setProgressAsync(workDataOf(KEY_PROGRESS to currentProgress))
                            lastProgressTime = now
                        }

                        if (now - lockFile.lastModified() > 1000L) {
                            lockFile.setLastModified(now)
                        }
                    }
                    output.flush()
                    output.fd.sync()
                }
            }
        } finally {
            pfd?.close()
        }
    }
}
