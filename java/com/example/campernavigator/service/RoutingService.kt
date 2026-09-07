package com.example.campernavigator.service

import com.example.campernavigator.model.VehicleProfile
import org.maplibre.android.geometry.LatLng

data class RouteInstruction(
    val text: String,
    val streetName: String?,
    val distance: Double,
    val time: Long,
    val location: LatLng,
    val sign: Int = 0, // Entspricht Instruction.getSign() von GraphHopper
    val roundaboutExit: Int? = null
)

data class SpeedLimitInfo(
    val speed: Int,
    val startPointIndex: Int,
    val endPointIndex: Int
)

data class Route(
    val points: List<LatLng>,
    val distance: Double,
    val time: Long,
    val instructions: List<RouteInstruction> = emptyList(),
    val speedLimits: List<SpeedLimitInfo> = emptyList()
)

interface RoutingService {
    suspend fun calculateRoute(
        start: LatLng,
        end: LatLng,
        profile: VehicleProfile,
        hasTrailer: Boolean = false,
        waypoints: List<LatLng> = emptyList()
    ): Route?

    suspend fun getStreetName(location: LatLng): String?

    suspend fun snapToRoad(location: LatLng): LatLng

    suspend fun getSpeedLimit(location: LatLng): Int?
}
