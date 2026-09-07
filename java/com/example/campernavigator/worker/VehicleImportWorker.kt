package com.example.campernavigator.worker

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.campernavigator.util.ZipUtil
import com.example.campernavigator.util.JsonUtil
import com.example.campernavigator.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class VehicleImportWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Fahrzeug-Import")
            .setContentText("Verarbeite Fahrzeugdaten...")
            .setSmallIcon(R.drawable.stat_sys_download)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else { 0 }

        return ForegroundInfo(NOTIFICATION_ID, notification, type)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uriString = inputData.getString(KEY_URI) ?: return@withContext Result.failure()
        val uri = Uri.parse(uriString)
        
        val lockFile = File(applicationContext.cacheDir, "import_vehicle.lock")
        val vehiclesBaseDir = File(applicationContext.getExternalFilesDir(null), "vehicles")
        val tempZip = File(applicationContext.cacheDir, "transfer_vehicle.vpk")
        val tempDir = File(vehiclesBaseDir, "temp_v_${System.currentTimeMillis()}")

        // 1. LOCK-MECHANISMUS
        if (lockFile.exists() && System.currentTimeMillis() - lockFile.lastModified() < 1800000) {
            FileLogger.log("VehicleImportWorker: Import already running. Killing ghost worker.")
            return@withContext Result.success()
        }
        lockFile.createNewFile()

        FileLogger.log("VehicleImportWorker: Starting robust import")
        try { setForeground(getForegroundInfo()) } catch (e: Exception) {}

        return@withContext try {
            vehiclesBaseDir.mkdirs()
            
            // 2. PRÜFE OB ZIP SCHON IM CACHE (RESUME)
            var isCopyNeeded = true
            if (tempZip.exists() && tempZip.length() > 1000) {
                if (ZipUtil.checkIntegrity(tempZip)) {
                    FileLogger.log("VehicleImportWorker: Using existing valid VPK.")
                    isCopyNeeded = false
                } else {
                    tempZip.delete()
                }
            }

            // 3. GEDROSSELTES KOPIEREN (0-30%)
            if (isCopyNeeded) {
                val pfd: ParcelFileDescriptor? = applicationContext.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    val totalSize = pfd.statSize
                    FileInputStream(pfd.fileDescriptor).use { fis ->
                        val sourceChannel = fis.channel
                        FileOutputStream(tempZip).use { fos ->
                            val destChannel = fos.channel
                            var position = 0L
                            while (position < totalSize) {
                                val transferred = destChannel.transferFrom(sourceChannel, position, 8 * 1024 * 1024L)
                                if (transferred <= 0) break
                                position += transferred
                                
                                if (totalSize > 0) {
                                    setProgressAsync(workDataOf(KEY_PROGRESS to (position * 30 / totalSize).toInt()))
                                }
                                lockFile.setLastModified(System.currentTimeMillis())
                            }
                            fos.flush(); fos.getFD().sync()
                        }
                    }
                    pfd.close()
                }
            }
            
            setProgressAsync(workDataOf(KEY_PROGRESS to 30))
            FileLogger.log("VehicleImportWorker: VPK Copy finished (30%). Verifying CRC...")

            if (!ZipUtil.checkIntegrity(tempZip)) throw Exception("VPK Korruption erkannt.")
            
            setProgressAsync(workDataOf(KEY_PROGRESS to 31))
            FileLogger.log("VehicleImportWorker: Integrity verified (31%). Starting extraction...")

            // 4. EXTRAKTION (31-100%)
            tempDir.mkdirs()
            ZipUtil.unzip(tempZip, tempDir) { progress ->
                lockFile.setLastModified(System.currentTimeMillis())
                setProgressAsync(workDataOf(KEY_PROGRESS to (31 + (progress * 69 / 100))))
            }
            
            // 5. FINALISIERUNG
            fun findDataRoot(dir: File): File? {
                if (File(dir, "manifest.json").exists()) return dir
                return dir.listFiles()?.filter { it.isDirectory }?.firstNotNullOfOrNull { findDataRoot(it) }
            }

            val dataRoot = findDataRoot(tempDir) ?: throw Exception("Struktur im VPK ungültig.")
            val manifestFile = File(dataRoot, "manifest.json")
            val json = JSONObject(JsonUtil.sanitizeJson(manifestFile.readText()))
            
            val finalId = json.optJSONObject("vehicle")?.optString("id") 
                ?: json.optJSONObject("package")?.optString("id")
                ?: ("vehicle_" + (System.currentTimeMillis() / 1000))
            
            FileLogger.log("VehicleImportWorker: SUCCESS: $finalId installed")
            
            val finalDir = File(vehiclesBaseDir, finalId)
            finalDir.deleteRecursively()
            dataRoot.renameTo(finalDir)
            
            Result.success(workDataOf(KEY_DISCOVERED_ID to finalId))
        } catch (e: Exception) {
            FileLogger.log("VehicleImportWorker: ERROR: ${e.message}", "ERROR")
            Result.failure(workDataOf(KEY_ERROR_MESSAGE to (e.message ?: "Fehler")))
        } finally {
            lockFile.delete()
            if (tempZip.exists()) tempZip.delete()
            tempDir.deleteRecursively()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Fahrzeug Import", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val KEY_VEHICLE_ID = "VEHICLE_ID"
        const val KEY_URI = "URI"
        const val KEY_PROGRESS = "PROGRESS"
        const val KEY_ERROR_MESSAGE = "ERROR_MESSAGE"
        const val KEY_DISCOVERED_ID = "DISCOVERED_ID"
        private const val CHANNEL_ID = "VEHICLE_IMPORT_CHANNEL"
        private const val NOTIFICATION_ID = 44
    }
}
