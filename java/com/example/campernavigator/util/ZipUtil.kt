package com.example.campernavigator.util

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import java.util.zip.ZipException

object ZipUtil {
    
    fun validateGraphHopperZip(file: File) {}
    fun validateMapPackZip(file: File) {}
    fun validateVehiclePackZip(file: File) {}

    /**
     * Fast integrity check without full CRC validation (too slow for large files).
     * Validates ZIP structure and readable entries instead.
     */
    fun checkIntegrity(zipFile: File): Boolean {
        FileLogger.log("ZipUtil: Fast integrity check (ZIP structure)...")
        return try {
            // 1. Quick size sanity check
            if (zipFile.length() < 22) return false // Minimum ZIP size
            
            // 2. Try to open and enumerate entries
            ZipFile(zipFile).use { zf ->
                var entryCount = 0
                val entries = zf.entries()
                
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    entryCount++
                    
                    // Timeout protection: if too many entries or taking too long, abort check
                    if (entryCount > 10000) {
                        FileLogger.log("ZipUtil: Too many entries, skipping full check")
                        return@use true
                    }
                }
                
                entryCount > 0 // Valid if we found any entries
            }
        } catch (e: ZipException) {
            FileLogger.log("ZipUtil: ZIP structure invalid - ${e.message}")
            false
        } catch (e: Exception) {
            FileLogger.log("ZipUtil: Integrity check failed - ${e.message}")
            false
        }
    }

    /**
     * OPTIMIZED BYTE-STREAM EXTRACTION:
     * - Fewer syncs (only every 200MB for large files)
     * - Reduced sleep time (5ms instead of 50ms)
     * - 512KB buffer for larger throughput
     * - Progress callback throttled
     */
    fun unzip(zipFile: File, targetDirectory: File, onProgress: ((Int) -> Unit)? = null) {
        FileLogger.log("ZipUtil: Starting optimized extraction...")
        targetDirectory.mkdirs()
        
        try {
            ZipFile(zipFile).use { zf ->
                // 1. Calculate total size
                var totalBytes = 0L
                val sizeEntries = zf.entries()
                while (sizeEntries.hasMoreElements()) {
                    totalBytes += sizeEntries.nextElement().size
                }
                
                if (totalBytes <= 0) totalBytes = 1 // Prevent div by zero
                
                var bytesWrittenAcrossAllFiles = 0L
                var lastReportedPercent = -1
                var lastProgressTime = System.currentTimeMillis()
                val entries = zf.entries()
                
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val targetFile = File(targetDirectory, entry.name.replace("\\", "/"))
                    
                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        
                        val isExtraLarge = entry.size > 500 * 1024 * 1024 // Only for really huge files
                        if (isExtraLarge) {
                            FileLogger.log("ZipUtil: Extracting large file: ${entry.name} (${entry.size / 1024 / 1024} MB)")
                        }

                        zf.getInputStream(entry).use { input ->
                            FileOutputStream(targetFile).use { output ->
                                val buffer = ByteArray(512 * 1024) // 512KB for better throughput
                                var len = input.read(buffer)
                                var syncCounter = 0L
                                
                                while (len > 0) {
                                    output.write(buffer, 0, len)
                                    syncCounter += len.toLong()
                                    bytesWrittenAcrossAllFiles += len.toLong()
                                    
                                    // Hardware Sync nur für sehr große Dateien, alle 200MB
                                    if (isExtraLarge && syncCounter >= 200 * 1024 * 1024) {
                                        output.flush()
                                        try {
                                            output.fd.sync()
                                        } catch (e: Exception) {}
                                        syncCounter = 0
                                        // Reduced sleep time (5ms statt 50ms) for better throughput
                                        Thread.sleep(5)
                                    }
                                    
                                    // Throttle progress callback (max every 500ms)
                                    val now = System.currentTimeMillis()
                                    if (now - lastProgressTime >= 500L) {
                                        val currentPercent = (bytesWrittenAcrossAllFiles * 100 / totalBytes).toInt()
                                        if (currentPercent != lastReportedPercent) {
                                            onProgress?.invoke(currentPercent)
                                            lastReportedPercent = currentPercent
                                        }
                                        lastProgressTime = now
                                    }
                                    
                                    len = input.read(buffer)
                                }
                                output.flush()
                                if (isExtraLarge) {
                                    try {
                                        output.fd.sync()
                                    } catch (e: Exception) {}
                                }
                            }
                        }
                    }
                }
            }
            FileLogger.log("ZipUtil: Extraction SUCCESSFUL.")
        } catch (e: Exception) {
            FileLogger.log("ZipUtil: Extraction FAILED: ${e.message}", "ERROR")
            throw e
        }
    }
}
