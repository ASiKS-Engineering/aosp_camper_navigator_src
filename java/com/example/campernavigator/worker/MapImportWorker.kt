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
        val notification = NotificationCompat.Builder(applicationContext, "MAP_IMPORT")
            .setContentTitle("Karten-Import")
            .setContentText("Turbo-Installation (Pi 5 Safe)...")
            .setSmallIcon(R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
        return ForegroundInfo(43, notification, 
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val regionId = inputData.getString(KEY_REGION_ID) ?: "Map"
        val uriString = inputData.getString(KEY_URI) ?: return@withContext Result.failure()
        
        val lockFile = File(applicationContext.cacheDir, "import_map.lock")
        val internalRoutingDir = File(applicationContext.filesDir, "routing")
        val tempExtractDir = File(internalRoutingDir, "tmp_safe_extract")
        // MOVE tempZip to filesDir as cacheDir might have size quotas
        val tempZip = File(applicationContext.filesDir, "import_turbo.zip")

        // 1. LOCK-MECHANISMUS
        if (lockFile.exists() && System.currentTimeMillis() - lockFile.lastModified() < 3600000) {
            FileLogger.log("MapImportWorker: Import already running. Killing ghost worker.")
            return@withContext Result.success() // NO output data -> ignored by UI
        }
        lockFile.createNewFile()

        return@withContext try {
            val finalDir = File(internalRoutingDir, regionId)
            
            // SOFORT-ERFOLG: Falls die Karte schon da ist
            if (finalDir.exists() && File(finalDir, "nodes").exists() && File(finalDir, "edges").exists()) {
                FileLogger.log("MapImportWorker: Map $regionId already complete. Success!")
                setProgressAsync(workDataOf(KEY_PROGRESS to 100))
                return@withContext Result.success(workDataOf(KEY_DISCOVERED_ID to regionId))
            }

            // 2. PRÜFE OB ZIP SCHON IM CACHE (RESUME)
            var isCopyNeeded = true
            if (tempZip.exists() && tempZip.length() > 4000000000L) {
                FileLogger.log("MapImportWorker: Found large ZIP in cache. Verifying...")
                if (ZipUtil.checkIntegrity(tempZip)) {
                    FileLogger.log("MapImportWorker: ZIP is valid. Skipping copy.")
                    isCopyNeeded = false
                }
            }

            // 3. TURBO-KOPIEREN (0-30%)
            if (isCopyNeeded) {
                FileLogger.log("MapImportWorker: Starting Turbo-Copy...")
                val pfd = applicationContext.contentResolver.openFileDescriptor(Uri.parse(uriString), "r")
                val totalSize = pfd?.statSize ?: -1L
                if (pfd != null) {
                    FileInputStream(pfd.fileDescriptor).use { input ->
                        FileOutputStream(tempZip).use { output ->
                            val buffer = ByteArray(16 * 1024 * 1024) // 16MB Turbo Buffer
                            var bytesRead: Int
                            var totalRead = 0L
                            var syncCounter = 0L
                            
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                syncCounter += bytesRead
                                
                                // Sync alle 500MB
                                if (syncCounter >= 500 * 1024 * 1024) {
                                    output.flush()
                                    output.getFD().sync()
                                    syncCounter = 0
                                    delay(10) // Minimale Pause
                                }
                                
                                if (totalSize > 0) {
                                    setProgressAsync(workDataOf(KEY_PROGRESS to (totalRead * 30 / totalSize).toInt()))
                                }
                                lockFile.setLastModified(System.currentTimeMillis()) 
                            }
                            output.flush()
                            output.getFD().sync()
                        }
                    }
                    pfd.close()
                }
            }
            
            setProgressAsync(workDataOf(KEY_PROGRESS to 30))
            FileLogger.log("MapImportWorker: Copy finished (30%). Verifying CRC...")

            if (!ZipUtil.checkIntegrity(tempZip)) throw Exception("ZIP Integrität fehlgeschlagen.")
            
            setProgressAsync(workDataOf(KEY_PROGRESS to 31))
            FileLogger.log("MapImportWorker: Integrity verified (31%). Starting extraction...")

            // 4. TURBO-EXTRAKTION (31-100%)
            tempExtractDir.deleteRecursively()
            tempExtractDir.mkdirs()

            ZipUtil.unzip(tempZip, tempExtractDir) { progress ->
                lockFile.setLastModified(System.currentTimeMillis())
                setProgressAsync(workDataOf(KEY_PROGRESS to (31 + (progress * 69 / 100))))
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
            Result.success(workDataOf(KEY_DISCOVERED_ID to regionId))
        } catch (e: Exception) {
            FileLogger.log("IMPORT ERROR: ${e.message}", "ERROR")
            Result.failure(workDataOf(KEY_ERROR_MESSAGE to (e.message ?: "Unbekannter Fehler")))
        } finally {
            lockFile.delete()
            tempExtractDir.deleteRecursively()
        }
    }
}
