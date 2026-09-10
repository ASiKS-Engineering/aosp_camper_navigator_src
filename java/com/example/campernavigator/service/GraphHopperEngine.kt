package com.example.campernavigator.service

import android.content.Context
import com.graphhopper.GraphHopper
import com.graphhopper.GraphHopperConfig
import com.graphhopper.config.Profile
import com.graphhopper.routing.WeightingFactory
import com.graphhopper.routing.DefaultWeightingFactory
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.util.PMap
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.routing.ev.BooleanEncodedValue
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.RoadClass
import com.graphhopper.routing.ev.RoadAccess
import com.graphhopper.routing.ev.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONObject
import com.example.campernavigator.model.CustomModelData
import com.example.campernavigator.model.VehicleConfigData
import com.example.campernavigator.model.RoutingMode
import com.example.campernavigator.util.FileLogger
import com.example.campernavigator.util.JsonUtil
import kotlin.text.Charsets

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

    private class AndroidFastestWeighting(
        private val accessEnc: BooleanEncodedValue?,
        private val isSubnetworkFlag: Boolean,
        private val speedEnc: DecimalEncodedValue?,
        private val profile: com.example.campernavigator.model.VehicleProfile,
        private val physicalEnc: Map<String, DecimalEncodedValue>,
        private val roadAccessEnc: EnumEncodedValue<RoadAccess>?,
        private val surfaceEnc: EnumEncodedValue<Surface>?,
        private val roadClassEnc: EnumEncodedValue<RoadClass>?,
        private val customModel: CustomModelData? = null,
        private val routingMode: RoutingMode = RoutingMode.FASTEST,
        private val name: String = "fastest"
    ) : Weighting {
        override fun calcMinWeightPerDistance(): Double = 1.0 / 130.0
        override fun calcEdgeWeight(edgeState: EdgeIteratorState, reverse: Boolean): Double {
            val h = physicalEnc["max_height"]?.let { if (reverse) edgeState.getReverse(it) else edgeState.get(it) } ?: 0.0
            if (h > 0.0 && h < profile.height) return Double.POSITIVE_INFINITY
            val w = physicalEnc["max_weight"]?.let { if (reverse) edgeState.getReverse(it) else edgeState.get(it) } ?: 0.0
            if (w > 0.0 && w < profile.weight) return Double.POSITIVE_INFINITY
            val width = physicalEnc["max_width"]?.let { if (reverse) edgeState.getReverse(it) else edgeState.get(it) } ?: 0.0
            if (width > 0.0 && width < profile.width) return Double.POSITIVE_INFINITY
            val length = physicalEnc["max_length"]?.let { if (reverse) edgeState.getReverse(it) else edgeState.get(it) } ?: 0.0
            if (length > 0.0 && length < profile.length) return Double.POSITIVE_INFINITY
            
            if (roadAccessEnc != null) {
                val ra = edgeState.get(roadAccessEnc)
                if (ra == RoadAccess.PRIVATE || ra == RoadAccess.NO) return Double.POSITIVE_INFINITY
            }
            if (accessEnc != null) {
                val flag = if (reverse) edgeState.getReverse(accessEnc) else edgeState.get(accessEnc)
                if (isSubnetworkFlag) { if (flag) return (edgeState.distance * 100.0) } 
                else { if (!flag) return Double.POSITIVE_INFINITY }
            }
            val rawSpeed = if (speedEnc != null) (if (reverse) edgeState.getReverse(speedEnc) else edgeState.get(speedEnc)) else 50.0
            val speed = Math.min(rawSpeed, 130.0)
            if (speed <= 0.0) return Double.POSITIVE_INFINITY
            var priority = 1.0
            if (roadClassEnc != null) {
                priority *= when(edgeState.get(roadClassEnc)) {
                    RoadClass.MOTORWAY, RoadClass.TRUNK -> 1.0
                    RoadClass.PRIMARY -> 0.95
                    RoadClass.SECONDARY -> 0.85
                    RoadClass.TERTIARY -> 0.75
                    RoadClass.RESIDENTIAL, RoadClass.LIVING_STREET -> 0.4
                    RoadClass.SERVICE -> 0.1
                    RoadClass.TRACK -> 0.01
                    else -> 0.6
                }
            }
            if (surfaceEnc != null) {
                val surf = edgeState.get(surfaceEnc)
                if (surf == Surface.GRAVEL || surf == Surface.DIRT || surf == Surface.SAND) priority *= 0.1
            }
            return if (routingMode == RoutingMode.SHORTEST) edgeState.distance / priority
            else {
                val distInfluence = customModel?.distanceInfluence ?: 70.0
                val time = edgeState.distance * 3.6 / speed
                (time + (distInfluence / 1000.0 * edgeState.distance)) / priority
            }
        }
        override fun calcEdgeMillis(edgeState: EdgeIteratorState, reverse: Boolean): Long {
            val speed = Math.min(if (speedEnc != null) (if (reverse) edgeState.getReverse(speedEnc) else edgeState.get(speedEnc)) else 50.0, 130.0)
            if (speed <= 0.0) return 0
            return (1000 * (edgeState.distance * 3.6 / speed)).toLong()
        }
        override fun calcTurnWeight(inEdge: Int, viaNode: Int, outEdge: Int): Double = 0.0
        override fun calcTurnMillis(inEdge: Int, viaNode: Int, outEdge: Int): Long = 0
        override fun hasTurnCosts(): Boolean = false
        override fun getName(): String = name
    }

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

            var vehicleConfig: VehicleConfigData? = null
            var customModel: CustomModelData? = null

            if (requestedConfig.vehicleId != null) {
                try {
                    val vFile = File(
                        File(context.getExternalFilesDir(null), "vehicles/${requestedConfig.vehicleId}"),
                        "vehicles"
                    ).listFiles { f -> f.extension == "json" }?.firstOrNull()
                    if (vFile != null) {
                        val json = JSONObject(JsonUtil.sanitizeJson(vFile.readText())).optJSONObject("vehicle")
                        if (json != null) vehicleConfig = VehicleConfigData(height = json.optDouble("height", 3.22), width = json.optDouble("width", 2.35), weight = json.optDouble("weight", 4.4), length = json.optDouble("length", 11.7))
                    }
                    val sFile = File(
                        File(context.getExternalFilesDir(null), "vehicles/${requestedConfig.vehicleId}"),
                        "settings/routing.json"
                    )
                    if (sFile.exists()) {
                        val json = JSONObject(JsonUtil.sanitizeJson(sFile.readText()))
                        customModel = CustomModelData(distanceInfluence = json.optDouble("distance_influence", 70.0))
                    }
                } catch (e: Exception) {}
            }

            if (currentConfigKey == requestedConfig && routingService != null) return@withContext routingService!!
            closeCurrentHopper()
            val ghLocationFile = findGraphCacheDir(regionDir) ?: throw Exception("Routing-Daten nicht gefunden.")
            File(ghLocationFile, "gh.lock").delete()

            val propsInfo = extractInfoFromProperties(ghLocationFile)
            val profilesToLoad = propsInfo.profiles.map { name: String ->
                // GH 11.0: Wir nutzen nur Profile(name).setWeighting("custom")
                Profile(name).setWeighting("custom")
            }

            val config = GraphHopperConfig().apply {
                putObject("graph.profiles.check", false)
                putObject("graph.encoded_values.check", false)
                putObject("graph.dataaccess", "MMAP")
                putObject("graph.location", ghLocationFile.absolutePath)
                putObject("import.osm.ignored_highways", "")
                putObject("graph.lock_type", "simple")
                putObject("index.max_region_search", 1000)
                putObject("index.high_resolution", true)
                putObject("graph.dataaccess.segment_size", 33554432)
                setProfiles(profilesToLoad)
            }

            fun createHopper() = object : GraphHopper() {
                override fun createWeightingFactory(): WeightingFactory {
                    return object : DefaultWeightingFactory(baseGraph, encodingManager) {
                        override fun createWeighting(profile: Profile, requestHints: PMap, disableTurnCosts: Boolean): Weighting {
                            // In GH 11.0 nutzen wir 'car' als Standard-Basis
                            val v = "car"
                            val access = try { when {
                                encodingManager.hasEncodedValue(v + "_access") -> encodingManager.getBooleanEncodedValue(v + "_access")
                                else -> null
                            } } catch (e: Exception) { null }
                            val speed = try { when {
                                encodingManager.hasEncodedValue(v + "_average_speed") -> encodingManager.getDecimalEncodedValue(v + "_average_speed")
                                else -> null
                            } } catch (e: Exception) { null }
                            val phys = mutableMapOf<String, DecimalEncodedValue>()
                            listOf("max_height", "max_weight", "max_width", "max_length").forEach { k -> try { if (encodingManager.hasEncodedValue(k)) phys[k] = encodingManager.getDecimalEncodedValue(k) } catch (e: Exception) {} }
                            val roadAccess = try { if (encodingManager.hasEncodedValue("road_access")) encodingManager.getEnumEncodedValue("road_access", RoadAccess::class.java) else null } catch (e: Exception) { null }
                            val surface = try { if (encodingManager.hasEncodedValue("surface")) encodingManager.getEnumEncodedValue("surface", Surface::class.java) else null } catch (e: Exception) { null }
                            val roadClass = try { if (encodingManager.hasEncodedValue("road_class")) encodingManager.getEnumEncodedValue("road_class", RoadClass::class.java) else null } catch (e: Exception) { null }
                            val routingProfile = if (vehicleConfig != null) com.example.campernavigator.model.VehicleProfile("Mappack", com.example.campernavigator.model.VehicleType.CAMPER, vehicleConfig.height, vehicleConfig.width, vehicleConfig.length, vehicleConfig.weight)
                            else com.example.campernavigator.model.VehicleProfile("Internal", com.example.campernavigator.model.VehicleType.CAMPER, 3.2, 2.3, 6.0, 3.5)
                            return AndroidFastestWeighting(access, access?.name?.contains("subnetwork") == true, speed, routingProfile, phys, roadAccess, surface, roadClass, customModel, routingMode)
                        }
                    }
                }
            }

            var activeHopper = createHopper()
            try {
                FileLogger.log("Calling activeHopper.init(config)")
                activeHopper.init(config)
                FileLogger.log("activeHopper.init(config) success, calling load()")
                if (!activeHopper.load()) throw Exception("Profiles do not match")
                FileLogger.log("activeHopper.load() success")
            } catch (e: Throwable) {
                FileLogger.log("GraphHopper load error: ${e.message}", "ERROR")
                if (e.message?.contains("Profiles do not match") == true) {
                    val expected = Regex("""config: ([\w|,-]+)""").find(e.message!!)?.groupValues?.get(1)
                    if (expected != null) {
                        FileLogger.log("Hash-Sync (OOM safe): $expected")
                        patchProfilesOnlySafe(ghLocationFile, expected)
                        try { activeHopper.close() } catch (ex: Exception) {}
                        activeHopper = createHopper()
                        activeHopper.init(config)
                        if (!activeHopper.load()) throw Exception("Retry load failed")
                    } else throw e
                } else throw e
            }

            hopper = activeHopper
            currentRegionId = regionId
            currentConfigKey = requestedConfig
            routingService = GraphHopperRoutingService(activeHopper)
            routingService!!
        }
    }

    private fun patchProfilesOnlySafe(dir: File, newProfiles: String) {
        val propsFile = File(dir, "properties")
        if (!propsFile.exists()) return
        try {
            val buffer = ByteArray(256 * 1024)
            val bytesRead: Int
            propsFile.inputStream().use { it.skip(100); bytesRead = it.read(buffer) }
            if (bytesRead <= 0) return
            val text = String(buffer, 0, bytesRead, Charsets.UTF_8)
            val lines = text.split("\n").toMutableList()
            val newLines = lines.map { if (it.startsWith("profiles=")) "profiles=$newProfiles" else it }
            val newText = newLines.joinToString("\n").toByteArray(Charsets.UTF_8)
            RandomAccessFile(propsFile, "rw").use { raf ->
                val out = ByteBuffer.allocate(100 + newText.size).apply {
                    order(ByteOrder.BIG_ENDIAN); putShort(2); put('G'.code.toByte()); put('H'.code.toByte())
                    putLong(newText.size.toLong()); position(20); putInt(33554432); position(100); put(newText)
                }
                raf.seek(0); raf.write(out.array()); raf.setLength(100L + newText.size)
            }
        } catch (e: Exception) { android.util.Log.e("GraphHopperEngine", "Patch failed: ${e.message}") }
    }

    private data class GraphPropsInfo(val profiles: Set<String>, val encodedValues: Set<String>)
    private fun extractInfoFromProperties(dir: File): GraphPropsInfo {
        val propsFile = File(dir, "properties")
        if (!propsFile.exists()) return GraphPropsInfo(emptySet(), emptySet())
        try {
            val profiles = mutableSetOf<String>()
            val evs = mutableSetOf<String>()
            val buffer = ByteArray(256 * 1024)
            propsFile.inputStream().use { it.skip(100); val read = it.read(buffer); if (read > 0) {
                val text = String(buffer, 0, read, Charsets.UTF_8)
                Regex("""profiles=(.*?)(\n|\r|$)""").find(text)?.groupValues?.get(1)?.split(",")?.forEach {
                    val p = it.substringBefore("|").trim(); if (p.isNotEmpty()) profiles.add(p)
                }
                Regex("""graph.encoded_values=(.*?)(\n|\r|$)""").find(text)?.groupValues?.get(1)?.let { line ->
                    Regex("""\\"name\\":\\"(.*?)\\"""").findAll(line).forEach { evs.add(it.groupValues[1]) }
                }
            }}
            return GraphPropsInfo(profiles, evs)
        } catch (e: Exception) { return GraphPropsInfo(emptySet(), emptySet()) }
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
