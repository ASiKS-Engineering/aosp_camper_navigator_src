package com.example.campernavigator.model

enum class VehicleType {
    CAMPER, VAN, TRUCK
}

data class VehicleProfile(
    val name: String,
    val type: VehicleType,
    val height: Double, // in meters
    val width: Double,  // in meters
    val length: Double, // in meters
    val weight: Double  // in tons
)
