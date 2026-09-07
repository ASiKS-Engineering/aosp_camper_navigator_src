package com.example.campernavigator.service

import com.example.campernavigator.model.VehicleProfile
import org.maplibre.android.geometry.LatLng

class FakeRoutingService : RoutingService {
    override suspend fun calculateRoute(
        start: LatLng,
        end: LatLng,
        profile: VehicleProfile,
        hasTrailer: Boolean,
        waypoints: List<LatLng>
    ): Route {
        // Just return a straight line through all waypoints
        val allPoints = mutableListOf<LatLng>()
        allPoints.add(start)
        allPoints.addAll(waypoints)
        allPoints.add(end)
        
        return Route(
            points = allPoints,
            distance = 1000.0,
            time = 600000 // 10 Minuten in Millisekunden
        )
    }

    override suspend fun getStreetName(location: LatLng): String? = "Simulierte Straße"

    override suspend fun snapToRoad(location: LatLng): LatLng = location

    override suspend fun getSpeedLimit(location: LatLng): Int? {
        // Simuliere unterschiedliche Geschwindigkeiten basierend auf der Position
        // Wir nehmen die Longitude als "Streckenfortschritt"
        val seed = (location.longitude * 100).toInt()
        return when {
            seed % 3 == 0 -> 30
            seed % 5 == 0 -> 100
            else -> 50
        }
    }
}
