package com.example.campernavigator.model

enum class RoutingMode {
    FASTEST, SHORTEST
}

data class CustomModelData(
    val distanceInfluence: Double = 70.0,
    val priorities: List<PriorityRule> = emptyList(),
    val speeds: List<SpeedRule> = emptyList()
)

data class PriorityRule(
    val condition: String,
    val multiplier: Double
)

data class SpeedRule(
    val condition: String,
    val limit: Double
)

data class VehicleConfigData(
    val name: String = "Camper",
    val height: Double = 3.2,
    val width: Double = 2.3,
    val length: Double = 7.0,
    val weight: Double = 3.5,
    val maxSpeed: Int = 80
)
