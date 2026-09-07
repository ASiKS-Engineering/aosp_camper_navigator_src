package com.example.campernavigator.util

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

object ZipUtil {
    
    fun validateGraphHopperZip(file: File) {}
    fun validateMapPackZip(file: File) {}
    fun validateVehiclePackZip(file: File) {}

    fun checkIntegrity(zipFile: File): Boolean {
        FileLogger.log("ZipUtil: Running native integrity test (-t)...")
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "/system/bin/unzip -t \"${zipFile.absolutePath}\""))
            val result = process.waitFor()
            result == 0
        } catch (e: Exception) { false }
    }

    /**
     * PRECISION BYTE-STREAM EXTRACTION (Pi 5 optimized):
     * - Progress is calculated based on BYTES, not file count.
     * - Frequent hardware syncs for large files.
     * - 256KB buffer for speed.
     */
    fun unzip(zipFile: File, targetDirectory: File, onProgress: ((Int) -> Unit)? = null) {
        FileLogger.log("ZipUtil: Starting byte-accurate turbo extraction...")
        targetDirectory.mkdirs()
        
        try {
            ZipFile(zipFile).use { zf ->
                // 1. Gesamtgröße berechnen
                var totalBytes = 0L
                val sizeEntries = zf.entries()
                while (sizeEntries.hasMoreElements()) {
                    totalBytes += sizeEntries.nextElement().size
                }
                
                if (totalBytes <= 0) totalBytes = 1 // Prevent div by zero
                
                var bytesWrittenAcrossAllFiles = 0L
                var lastReportedPercent = -1
                val entries = zf.entries()
                
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val targetFile = File(targetDirectory, entry.name.replace("\\", "/"))
                    
                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        
                        val isExtraLarge = entry.size > 100 * 1024 * 1024
                        if (isExtraLarge) {
                            FileLogger.log("ZipUtil: Extracting large file: ${entry.name} (${entry.size / 1024 / 1024} MB)")
                        }

                        zf.getInputStream(entry).use { input ->
                            FileOutputStream(targetFile).use { output ->
                                val buffer = ByteArray(256 * 1024) 
                                var len = input.read(buffer)
                                var syncCounter = 0L
                                
                                while (len > 0) {
                                    output.write(buffer, 0, len)
                                    syncCounter += len.toLong()
                                    bytesWrittenAcrossAllFiles += len.toLong()
                                    
                                    // Hardware Sync alle 100MB
                                    if (isExtraLarge && syncCounter >= 100 * 1024 * 1024) {
                                        output.flush()
                                        try {
                                            output.fd.sync()
                                        } catch (e: Exception) {}
                                        syncCounter = 0
                                        Thread.sleep(50) // More breathing room for RPi5 SD controller
                                    }
                                    
                                    // Fortschritt alle 1% oder 10MB melden
                                    val currentPercent = (bytesWrittenAcrossAllFiles * 100 / totalBytes).toInt()
                                    if (currentPercent != lastReportedPercent) {
                                        onProgress?.invoke(currentPercent)
                                        lastReportedPercent = currentPercent
                                    }
                                    
                                    len = input.read(buffer)
                                }
                                output.flush()
                                if (isExtraLarge) output.fd.sync()
                            }
                        }
                    }
                }
            }
            FileLogger.log("ZipUtil: Precision extraction SUCCESSFUL.")
        } catch (e: Exception) {
            FileLogger.log("ZipUtil: TURBO FAILED: ${e.message}", "ERROR")
            throw e
        }
    }
}
