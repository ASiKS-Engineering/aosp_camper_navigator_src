package com.example.campernavigator.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "traffic_events")
data class TrafficEventEntity(
    @PrimaryKey val id: String,
    val headline: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val severity: String,
    val type: String,
    val status: String,
    val cachedAt: Long = System.currentTimeMillis()
)
