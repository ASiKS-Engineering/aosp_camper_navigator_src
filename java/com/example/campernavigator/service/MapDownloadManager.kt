package com.example.campernavigator.service

import android.content.Context
import android.net.Uri
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.example.campernavigator.worker.MapImportWorker
import com.example.campernavigator.util.FileLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

sealed class DownloadState {
    object Idle : DownloadState()
    data class Processing(val progress: Int = 0) : DownloadState()
    object Completed : DownloadState()
    object Cancelling : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class MapDownloadManager(
    private val context: Context
) {
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState

    private val workManager = WorkManager.getInstance(context)

    fun getInstalledRegions(): List<String> {
        val internalRoutingDir = File(context.filesDir, "routing")
        val externalRoutingDir = File(context.getExternalFilesDir(null), "routing")
        val regions = mutableSetOf<String>()
        
        listOf(internalRoutingDir, externalRoutingDir).forEach { root ->
            if (root.exists()) {
                root.listFiles { file -> 
                    file.isDirectory && !file.name.startsWith(".") && 
                    !file.name.contains("tmp") && !file.name.contains("diag")
                }?.forEach { regions.add(it.name) }
            }
        }
        return regions.toList()
    }

    /**
     * RADIKALER RESET: Stoppt alle Importe und vernichtet alle Dateifragmente.
     */
    fun cancelImport() {
        FileLogger.log("MapDownloadManager: KILLING ALL MAP IMPORTS")
        _downloadState.value = DownloadState.Cancelling
        // 1. WorkManager stoppen
        workManager.cancelAllWorkByTag("map_import")
        workManager.cancelUniqueWork("map_import_task")
        
        _downloadState.value = DownloadState.Idle
        
        // 2. Physische Reinigung
        cleanupArtifacts()
        
        // 3. Kernel Sync erzwingen
        try {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", "sync"))
            FileLogger.log("MapDownloadManager: Kernel sync triggered")
        } catch (e: Exception) {}
    }

    fun cleanupArtifacts() {
        FileLogger.log("MapDownloadManager: Starting radical cleanup...")
        val internalRoot = File(context.filesDir, "routing")
        val externalRoot = File(context.getExternalFilesDir(null), "routing")
        val cacheDir = context.cacheDir

        // Alles löschen, was nach Fragment aussieht
        listOf(internalRoot, externalRoot, cacheDir).forEach { dir ->
            if (dir.exists()) {
                dir.listFiles { f -> 
                    f.name.contains("tmp") || f.name.contains("diag") || 
                    f.name.contains("transfer") || f.name.contains("import") ||
                    f.name.endsWith(".zip") || f.name.startsWith("source_")
                }?.forEach { 
                    FileLogger.log("Cleanup: Deleting ${it.name}")
                    it.deleteRecursively() 
                }
            }
        }
    }

    fun deleteRegion(regionId: String): Boolean {
        cancelImport() 
        FileLogger.log("MapDownloadManager: Deleting region $regionId")
        
        val internalRegionDir = File(context.filesDir, "routing/$regionId")
        val externalRegionDir = File(context.getExternalFilesDir(null), "routing/$regionId")
        
        var deleted = false
        if (internalRegionDir.exists()) deleted = internalRegionDir.deleteRecursively() || deleted
        if (externalRegionDir.exists()) deleted = externalRegionDir.deleteRecursively() || deleted
        
        cleanupArtifacts()
        return deleted
    }

    fun clearAllRoutingData(): Boolean {
        cancelImport()
        FileLogger.log("MapDownloadManager: CLEARING ALL ROUTING DATA")
        
        val internalRoot = File(context.filesDir, "routing")
        val externalRoot = File(context.getExternalFilesDir(null), "routing")
        
        var success = true
        if (internalRoot.exists()) success = internalRoot.deleteRecursively() && success
        if (externalRoot.exists()) success = externalRoot.deleteRecursively() && success
        
        cleanupArtifacts()
        return success
    }

    fun importRegion(regionId: String, uri: Uri) {
        val data = Data.Builder()
            .putString(MapImportWorker.KEY_REGION_ID, regionId)
            .putString(MapImportWorker.KEY_URI, uri.toString())
            .build()

        val importRequest = OneTimeWorkRequestBuilder<MapImportWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(data)
            .addTag("map_import")
            .build()

        workManager.enqueueUniqueWork(
            "map_import_task",
            ExistingWorkPolicy.KEEP,
            importRequest
        )
        
        _downloadState.value = DownloadState.Processing()
    }

    fun resetState() {
        _downloadState.value = DownloadState.Idle
    }
}
