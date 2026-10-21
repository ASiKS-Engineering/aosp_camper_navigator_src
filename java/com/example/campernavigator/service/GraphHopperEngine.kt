package com.example.campernavigator.service

import android.content.Context
import com.example.campernavigator.model.RoutingMode
import com.example.campernavigator.model.VehicleConfigData
import com.example.campernavigator.util.FileLogger
import com.example.campernavigator.util.JsonUtil
import com.graphhopper.GraphHopper
import com.graphhopper.util.CustomModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class GraphHopperEngine(val context: Context) {
    private var hopper: GraphHopper? = null
    private var routingService: RoutingService? = null
    private var currentRegionId: String? = null
    private var currentConfigKey: EngineConfigKey? = null
    private val initMutex = Mutex()

    private data class EngineConfigKey(
        val regionId: String,
        val routingMode: RoutingMode,
        val vehicleId: String?
    )

    suspend fun init(regionId: String, routingMode: RoutingMode = RoutingMode.FASTEST, vehicleId: String? = null): RoutingService = initMutex.withLock {
        val requestedConfig = EngineConfigKey(
            regionId = regionId,
            routingMode = routingMode,
            vehicleId = vehicleId?.takeIf { it.isNotBlank() }
        )
        FileLogger.log(
            "GraphHopperEngine.init started for region $regionId mode=$routingMode vehicle=${requestedConfig.vehicleId}"
        )
        withContext(Dispatchers.IO) {
            // Wir suchen an beiden Orten: Intern (Pi 5 Favorit) und Extern
            val internalRoot = File(context.filesDir, "routing/$regionId")
            val externalRoot = File(context.getExternalFilesDir(null), "routing/$regionId")

            val regionDir = if (internalRoot.exists()) {
                FileLogger.log("GraphHopperEngine: Using INTERNAL storage for $regionId")
                internalRoot
            } else {
                FileLogger.log("GraphHopperEngine: Using EXTERNAL storage for $regionId")
                externalRoot
            }

            if (currentConfigKey == requestedConfig && routingService != null) return@withContext routingService!!
            closeCurrentHopper()
            val graphDir = findGraphCacheDir(regionDir) ?: throw Exception("Routing-Daten nicht gefunden.")
            File(graphDir, "gh.lock").delete()

            val model = buildEffectiveModel(graphDir, regionDir, requestedConfig)
            val activeHopper = PackRouting.load(graphDir, model, useLandmarks = true) { FileLogger.log("GraphHopperEngine: $it") }
            try {
                // Fail early (and readably) if the pack's model uses something this app cannot evaluate.
                PackCustomWeighting(activeHopper.encodingManager, model)
            } catch (e: Exception) {
                try { activeHopper.close() } catch (_: Exception) {}
                throw Exception("Routing-Modell des Kartenpakets nicht nutzbar: ${e.message}")
            }
            FileLogger.log("GraphHopperEngine: loaded, landmarks=${PackRouting.hasLandmarks(graphDir)}, profiles=${activeHopper.profiles.map { it.name }}")

            hopper = activeHopper
            currentRegionId = regionId
            currentConfigKey = requestedConfig
            routingService = GraphHopperRoutingService(activeHopper)
            routingService!!
        }
    }

    // Pack model (as used for the landmarks) + vehicle restrictions + routing mode.
    private fun buildEffectiveModel(graphDir: File, regionDir: File, config: EngineConfigKey): CustomModel {
        val baseFile = listOf(File(graphDir, PackRouting.MODEL_FILE), File(regionDir, PackRouting.MODEL_FILE)).firstOrNull { it.isFile }
            ?: throw Exception("Das Kartenpaket enthält kein Routing-Modell (${PackRouting.MODEL_FILE}). Bitte mit dem aktuellen MapBuilder neu erstellen und importieren.")
        val base = try { PackRouting.loadModel(baseFile) } catch (e: Exception) {
            throw Exception("Routing-Modell des Kartenpakets ist ungültig: ${e.message}")
        }
        val vehicle = loadVehicleModel(config.vehicleId)
        return PackRouting.effectiveModel(base, vehicle, config.routingMode == RoutingMode.SHORTEST) { FileLogger.log("GraphHopperEngine: $it", "WARN") }
    }

    private fun loadVehicleModel(vehicleId: String?): CustomModel {
        val vehicleDir = vehicleId?.let { File(context.getExternalFilesDir(null), "vehicles/$it") }
        val modelFile = vehicleDir?.let { File(it, "models/custom_model.json") }
        if (modelFile != null && modelFile.isFile) {
            try {
                return PackRouting.loadModel(modelFile)
            } catch (e: Exception) {
                FileLogger.log("GraphHopperEngine: vehicle model $modelFile invalid (${e.message}); falling back to vehicle dimensions", "WARN")
            }
        }
        // Vehicle pack without model, or no vehicle at all: restrict by dimensions only.
        var dims = VehicleConfigData(height = 3.2, width = 2.3, weight = 3.5, length = 6.0)
        try {
            val vFile = vehicleDir?.let { File(it, "vehicles").listFiles { f -> f.extension == "json" }?.firstOrNull() }
            val json = vFile?.let { JSONObject(JsonUtil.sanitizeJson(it.readText())).optJSONObject("vehicle") }
            if (json != null) dims = VehicleConfigData(
                height = json.optDouble("height", 3.22), width = json.optDouble("width", 2.35),
                weight = json.optDouble("weight", 4.4), length = json.optDouble("length", 11.7)
            )
        } catch (e: Exception) {
            FileLogger.log("GraphHopperEngine: vehicle dimensions unreadable: ${e.message}", "WARN")
        }
        return PackRouting.dimensionModel(dims.height, dims.width, dims.length, dims.weight)
    }

    suspend fun close() = initMutex.withLock { withContext(Dispatchers.IO) { closeCurrentHopper() } }
    private fun closeCurrentHopper() { try { hopper?.close() } catch (e: Exception) { }; hopper = null; routingService = null; currentRegionId = null; currentConfigKey = null }
    private fun findGraphCacheDir(dir: File): File? {
        if (!dir.exists()) return null
        if (File(dir, "nodes").exists() && File(dir, "edges").exists()) return dir
        dir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.forEach { sub -> findGraphCacheDir(sub)?.let { return it } }
        return null
    }
    fun getSupportedEncodedValues(): List<String> = try { hopper?.encodingManager?.encodedValues?.map { it.name } ?: emptyList() } catch (e: Exception) { emptyList() }
    fun getRoutingService(): RoutingService? = routingService
    fun getActiveRegionBounds(): org.maplibre.android.geometry.LatLngBounds? {
        val h = hopper ?: return null
        return try {
            val b = h.baseGraph.bounds
            org.maplibre.android.geometry.LatLngBounds.Builder().include(org.maplibre.android.geometry.LatLng(b.minLat, b.minLon)).include(org.maplibre.android.geometry.LatLng(b.maxLat, b.maxLon)).build()
        } catch (e: Exception) { null }
    }
    fun getRegionBounds(regionId: String): org.maplibre.android.geometry.LatLngBounds? = if (currentRegionId == regionId) getActiveRegionBounds() else null
}
