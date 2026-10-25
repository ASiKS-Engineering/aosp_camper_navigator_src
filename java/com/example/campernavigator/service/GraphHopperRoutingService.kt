package com.example.campernavigator.service

import com.example.campernavigator.model.VehicleProfile
import com.graphhopper.GHRequest
import com.graphhopper.GraphHopper
import com.graphhopper.util.RoundaboutInstruction
import com.graphhopper.routing.util.EdgeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng

class GraphHopperRoutingService(
    private val hopper: GraphHopper,
) : RoutingService {

    private val translation = hopper.translationMap.getWithFallBack(java.util.Locale.GERMAN)

    override suspend fun calculateRoute(
        start: LatLng,
        end: LatLng,
        profile: VehicleProfile,
        hasTrailer: Boolean,
        waypoints: List<LatLng>
    ): Route? = withContext(Dispatchers.IO) {
        android.util.Log.d("RoutingService", "Berechne Camper-Route (GH 11.0): H=${profile.height}m, W=${profile.weight}t, Anhänger=$hasTrailer, Waypoints=${waypoints.size}")
        
        val loadedProfiles = hopper.profiles.map { it.name }
        
        val requestedProfile = if (hasTrailer) "camper_trailer" else "camper"
        val ghProfile = when {
            loadedProfiles.contains(requestedProfile) -> requestedProfile
            loadedProfiles.contains("camper") -> "camper"
            loadedProfiles.isNotEmpty() -> loadedProfiles.first()
            else -> requestedProfile 
        }
        
        android.util.Log.d("RoutingService", "Nutze geladenes GraphHopper-Profil: $ghProfile")
        
        val request = GHRequest().apply {
            addPoint(com.graphhopper.util.shapes.GHPoint(start.latitude, start.longitude))
            waypoints.forEach { addPoint(com.graphhopper.util.shapes.GHPoint(it.latitude, it.longitude)) }
            addPoint(com.graphhopper.util.shapes.GHPoint(end.latitude, end.longitude))
            setProfile(ghProfile)
            setPathDetails(listOf("max_speed", "average_speed"))
            putHint("index.max_region_search", 1000)
            putHint("index.high_resolution", true)
            putHint("curbside", "any")
        }
        
        request.putHint("ch.disable", true)
        request.algorithm = "astarbi"
        
        val response = try {
            hopper.route(request)
        } catch (e: Exception) {
            android.util.Log.e("RoutingService", "Fehler bei GH 11.0 Routenberechnung", e)
            throw Exception(e.message ?: "Interner Routing-Fehler")
        }

        if (response.hasErrors()) {
            val allErrors = response.errors.joinToString(", ") { it.message ?: "Unknown" }
            android.util.Log.e("RoutingService", "Routenfehler: $allErrors")
            throw Exception(allErrors)
        }
        
        val bestPath = response.best
        val points = mutableListOf<LatLng>()
        val pointList = bestPath.points
        for (i in 0 until pointList.size()) {
            points.add(LatLng(pointList.getLat(i), pointList.getLon(i)))
        }

        val instructions = bestPath.instructions.map {
            val roundaboutExit = if (it is RoundaboutInstruction) {
                it.exitNumber
            } else null
            
            RouteInstruction(
                text = it.getTurnDescription(translation),
                streetName = it.name,
                distance = it.distance,
                time = it.time,
                location = LatLng(it.points.getLat(0), it.points.getLon(0)),
                sign = it.sign,
                roundaboutExit = roundaboutExit
            )
        }
        
        val speedLimits = bestPath.pathDetails["max_speed"]?.mapNotNull { detail ->
            val speed = (detail.value as? Number)?.toDouble() ?: return@mapNotNull null
            if (speed > 0 && speed < 200) {
                SpeedLimitInfo(
                    speed = speed.toInt(),
                    startPointIndex = detail.first,
                    endPointIndex = detail.last
                )
            } else null
        } ?: emptyList()

        // Speed the routing model assumes per edge (road speed capped by the vehicle model); drives the demo mode.
        val segmentSpeedKmh = bestPath.pathDetails["average_speed"]?.let { details ->
            FloatArray((points.size - 1).coerceAtLeast(0)).also { out ->
                for (detail in details) {
                    val speed = (detail.value as? Number)?.toFloat() ?: continue
                    for (i in detail.first until minOf(detail.last, out.size)) out[i] = speed
                }
            }
        }

        Route(
            points = points,
            distance = bestPath.distance,
            time = bestPath.time,
            instructions = instructions,
            speedLimits = speedLimits,
            segmentSpeedKmh = segmentSpeedKmh
        )
    }

    override suspend fun getStreetName(location: LatLng): String? = withContext(Dispatchers.IO) {
        try {
            val snap = hopper.locationIndex.findClosest(location.latitude, location.longitude, EdgeFilter.ALL_EDGES)
            if (snap.isValid) {
                val edge = snap.closestEdge
                val name = edge.name
                var ref: String? = null
                
                val em = hopper.encodingManager
                try {
                    if (em.hasEncodedValue("road_ref")) {
                        ref = edge.get(em.getEncodedValue("road_ref", com.graphhopper.routing.ev.StringEncodedValue::class.java))
                    } else if (em.hasEncodedValue("ref")) {
                        ref = edge.get(em.getEncodedValue("ref", com.graphhopper.routing.ev.StringEncodedValue::class.java))
                    }
                } catch (e: Exception) { }
                
                if (ref.isNullOrBlank()) {
                    ref = try {
                        val method = edge.javaClass.getMethod("getValue", String::class.java)
                        method.invoke(edge, "ref") as? String
                    } catch (e: Exception) { null }
                }
                
                when {
                    !name.isNullOrBlank() && !ref.isNullOrBlank() -> if (name == ref) name else "$ref ($name)"
                    !name.isNullOrBlank() -> name
                    !ref.isNullOrBlank() -> ref
                    else -> null
                }
            } else null
        } catch (e: Exception) { null }
    }

    override suspend fun snapToRoad(location: LatLng): LatLng = withContext(Dispatchers.IO) {
        try {
            val snap = hopper.locationIndex.findClosest(location.latitude, location.longitude, EdgeFilter.ALL_EDGES)
            if (snap.isValid) LatLng(snap.snappedPoint.lat, snap.snappedPoint.lon) else location
        } catch (e: Exception) { location }
    }

    override suspend fun getSpeedLimit(location: LatLng): Int? = withContext(Dispatchers.IO) {
        try {
            val snap = hopper.locationIndex.findClosest(location.latitude, location.longitude, EdgeFilter.ALL_EDGES)
            if (snap.isValid) {
                val edge = snap.closestEdge
                val em = hopper.encodingManager
                val keys = listOf("max_speed", "max_speed_forward", "max_speed_backward")
                for (key in keys) {
                    if (em.hasEncodedValue(key)) {
                        val speed = edge.get(em.getDecimalEncodedValue(key))
                        if (speed > 0 && speed < 200) return@withContext speed.toInt()
                    }
                }
                null
            } else null
        } catch (e: Exception) { null }
    }

}
