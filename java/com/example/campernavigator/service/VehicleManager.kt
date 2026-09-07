package com.example.campernavigator.service

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.campernavigator.worker.VehicleImportWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class VehicleManager(
    private val context: Context
) {
    private val _importState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val importState: StateFlow<DownloadState> = _importState

    private val workManager = WorkManager.getInstance(context)

    fun getInstalledVehicles(): List<String> {
        val vehiclesDir = File(context.getExternalFilesDir(null), "vehicles")
        if (!vehiclesDir.exists()) return emptyList()
        
        return vehiclesDir.listFiles { file -> 
            file.isDirectory && !file.name.startsWith(".") && !file.name.startsWith("temp_") && file.name != "Importing..." && file.name != "Vehicle"
        }?.map { it.name } ?: emptyList()
    }

    /**
     * Bereinigt verwaiste temporäre Ordner oder Ordner mit ungültigen IDs
     */
    fun cleanUpOrphanedFolders() {
        val vehiclesDir = File(context.getExternalFilesDir(null), "vehicles")
        if (!vehiclesDir.exists()) return
        
        vehiclesDir.listFiles { file ->
            file.isDirectory && (file.name.startsWith("temp_") || file.name == "Importing..." || file.name == "Vehicle")
        }?.forEach { 
            Log.d("VehicleManager", "Bereinige verwaisten Ordner: ${it.name}")
            it.deleteRecursively() 
        }
    }

    fun deleteVehicle(vehicleId: String): Boolean {
        val vehicleDir = File(context.getExternalFilesDir(null), "vehicles/$vehicleId")
        return if (vehicleDir.exists()) {
            vehicleDir.deleteRecursively()
        } else {
            false
        }
    }

    fun importVehicle(vehicleId: String, uri: Uri) {
        // Explizite Typisierung um Inferenz-Fehler zu vermeiden
        val idPair = Pair<String, String>(VehicleImportWorker.KEY_VEHICLE_ID, vehicleId)
        val uriPair = Pair<String, String>(VehicleImportWorker.KEY_URI, uri.toString())
        
        val data = workDataOf(idPair, uriPair)

        val importRequest = OneTimeWorkRequestBuilder<VehicleImportWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(data)
            .addTag("vehicle_import")
            .build()

        workManager.enqueueUniqueWork(
            "vehicle_import_task", 
            ExistingWorkPolicy.REPLACE,
            importRequest
        )
        
        _importState.value = DownloadState.Processing()
    }

    fun resetState() {
        _importState.value = DownloadState.Idle
    }
}
