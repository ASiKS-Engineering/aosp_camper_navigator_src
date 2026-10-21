package com.example.campernavigator.service

import com.graphhopper.GraphHopper
import com.graphhopper.GraphHopperConfig
import com.graphhopper.json.Statement
import com.graphhopper.config.LMProfile
import com.graphhopper.config.Profile
import com.graphhopper.routing.WeightingFactory
import com.graphhopper.util.CustomModel
import java.io.File

/**
 * Routing setup driven by the data of the imported map pack.
 *
 * The map pack ships the base custom model ([MODEL_FILE]) that was also used to build its landmarks (LM).
 * The vehicle model of the active vehicle pack and the routing mode are merged on top of it. The merged
 * model may only make edges more expensive than the base model, otherwise the landmarks would no longer
 * be a valid lower bound and routes could become wrong.
 */
object PackRouting {
    const val PROFILE_NAME = "camper"
    const val MODEL_FILE = "custom_model.json"

    // Large enough that distance dominates the travel time, and >= every base value so LM stays admissible.
    private const val SHORTEST_DISTANCE_INFLUENCE = 5000.0

    fun loadModel(file: File): CustomModel = PackCustomWeighting.parseModel(file.readText())

    fun effectiveModel(base: CustomModel, vehicle: CustomModel?, shortest: Boolean, log: (String) -> Unit = {}): CustomModel {
        val baseDistanceInfluence = base.distanceInfluence ?: 70.0
        var model = if (vehicle != null) CustomModel.merge(base, vehicle) else CustomModel(base)
        var distanceInfluence = model.distanceInfluence ?: baseDistanceInfluence
        if (shortest) distanceInfluence = Math.max(distanceInfluence, SHORTEST_DISTANCE_INFLUENCE)
        if (distanceInfluence < baseDistanceInfluence) {
            log("distance_influence $distanceInfluence is below the pack's $baseDistanceInfluence; using the pack value")
            distanceInfluence = baseDistanceInfluence
        }
        model.distanceInfluence = distanceInfluence
        return model
    }

    /** Vehicle restrictions for vehicle packs that do not ship their own model (legacy packs and "no vehicle"). */
    fun dimensionModel(height: Double, width: Double, length: Double, weight: Double): CustomModel = CustomModel().apply {
        addToPriority(Statement.If("max_height > 0 && max_height < $height", Statement.Op.MULTIPLY, "0"))
        addToPriority(Statement.If("max_width > 0 && max_width < $width", Statement.Op.MULTIPLY, "0"))
        addToPriority(Statement.If("max_length > 0 && max_length < $length", Statement.Op.MULTIPLY, "0"))
        addToPriority(Statement.If("max_weight > 0 && max_weight < $weight", Statement.Op.MULTIPLY, "0"))
    }

    fun createHopper(model: CustomModel): GraphHopper = object : GraphHopper() {
        override fun createWeightingFactory(): WeightingFactory = WeightingFactory { _, hints, _ ->
            PackCustomWeighting(
                encodingManager,
                model,
                hints.getDouble("heading_penalty", PackCustomWeighting.DEFAULT_HEADING_PENALTY)
            )
        }
    }

    /** Landmarks are only enabled if the pack contains them; GraphHopper would otherwise compute them on load. */
    fun hasLandmarks(graphDir: File, profile: String = PROFILE_NAME): Boolean =
        File(graphDir, "landmarks_$profile").let { it.isFile && it.length() > 0 }

    fun createConfig(graphDir: File, profileNames: Collection<String>, useLandmarks: Boolean, extra: GraphHopperConfig.() -> Unit = {}): GraphHopperConfig =
        GraphHopperConfig().apply {
            putObject("graph.profiles.check", false)
            putObject("graph.encoded_values.check", false)
            putObject("graph.location", graphDir.absolutePath)
            putObject("import.osm.ignored_highways", "")
            putObject("graph.lock_type", "simple")
            putObject("index.max_region_search", 1000)
            putObject("index.high_resolution", true)
            putObject("graph.dataaccess", "MMAP")
            putObject("graph.dataaccess.default_type", "MMAP")
            putObject("graph.dataaccess.segment_size", 33554432)
            setProfiles(profileNames.map { Profile(it).setWeighting("custom") })
            if (useLandmarks) setLMProfiles(profileNames.filter { hasLandmarks(graphDir, it) }.map { LMProfile(it) })
            extra()
        }

    /**
     * Loads the graph. A pack's stored profile hash never matches the profile created here (the model is not part
     * of the profile), so on "Profiles do not match" the stored hash is replaced and loading is retried once.
     */
    fun load(graphDir: File, model: CustomModel, useLandmarks: Boolean, log: (String) -> Unit = {}): GraphHopper {
        val names = profileNames(graphDir).ifEmpty { listOf(PROFILE_NAME) }
        fun open(): GraphHopper = createHopper(model).also { it.init(createConfig(graphDir, names, useLandmarks)) }
        var hopper = open()
        try {
            check(hopper.load()) { "Routing-Daten konnten nicht geladen werden" }
            return hopper
        } catch (e: Exception) {
            val msg = e.message ?: ""
            val expected = when {
                msg.contains("Profiles do not match") -> Regex("""config: ([\w|,-]+)""").find(msg)?.groupValues?.get(1)
                // profile hash already matches, only the stored LM/CH version differs
                msg.contains("already exists in storage and doesn't match configuration") ->
                    readProperties(graphDir)?.second?.lineSequence()?.firstOrNull { it.startsWith("profiles=") }?.removePrefix("profiles=")?.trim()
                else -> null
            }
            if (expected == null) throw e
            log("Profile hash sync: $expected (${msg.take(60)})")
            try { hopper.close() } catch (_: Exception) {}
            patchProfiles(graphDir, expected)
            hopper = open()
            check(hopper.load()) { "Routing-Daten konnten nach Hash-Sync nicht geladen werden" }
            return hopper
        }
    }

    // The graph's binary `properties` file: a 100 byte header (content length at offset 4), then text.
    private fun readProperties(dir: File): Pair<ByteArray, String>? {
        val file = File(dir, "properties")
        if (!file.exists()) return null
        val bytes = file.readBytes()
        if (bytes.size <= 100) return null
        val declared = java.nio.ByteBuffer.wrap(bytes).getLong(4)
        val end = if (declared in 1..(bytes.size - 100).toLong()) 100 + declared.toInt() else bytes.size
        return bytes.copyOfRange(0, 100) to String(bytes, 100, end - 100, Charsets.UTF_8)
    }

    fun profileNames(dir: File): List<String> = try {
        readProperties(dir)?.second?.lineSequence()?.firstOrNull { it.startsWith("profiles=") }
            ?.removePrefix("profiles=")?.split(",")?.map { it.substringBefore("|").trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    } catch (e: Exception) { emptyList() }

    private fun patchProfiles(dir: File, newProfiles: String) {
        val (header, text) = readProperties(dir) ?: return
        val hashes = newProfiles.split(",").associate { it.substringBefore("|") to it.substringAfter("|") }
        val versionLine = Regex("""graph\.profiles\.(lm|ch)\.([^.=]+)\.version=.*""")
        val patched = text.split("\n").joinToString("\n") { line ->
            val m = versionLine.matchEntire(line.trimEnd('\r'))
            when {
                line.startsWith("profiles=") -> "profiles=$newProfiles"
                m != null && hashes.containsKey(m.groupValues[2]) ->
                    "graph.profiles.${m.groupValues[1]}.${m.groupValues[2]}.version=${hashes[m.groupValues[2]]}"
                else -> line
            }
        }
        val body = patched.toByteArray(Charsets.UTF_8)
        java.nio.ByteBuffer.wrap(header).putLong(4, body.size.toLong())
        File(dir, "properties").writeBytes(header + body)
    }
}
