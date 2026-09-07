package com.example.campernavigator.model

import org.maplibre.android.geometry.LatLngBounds

data class MapRegion(
    val id: String,
    val name: String,
    val bounds: LatLngBounds,
    val graphHopperUrl: String, // URL to zipped .gh files
    val styleUrl: String
)
