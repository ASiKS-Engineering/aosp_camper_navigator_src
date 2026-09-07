package com.example.campernavigator.data

import com.example.campernavigator.service.Open511Service
import com.example.campernavigator.service.TrafficEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.maplibre.android.geometry.LatLng

class TrafficRepository(
    private val apiService: Open511Service,
    private val dao: TrafficEventDao
) {
    val allTrafficEvents: Flow<List<TrafficEvent>> = dao.getAllTrafficEvents().map { entities ->
        entities.map { entity ->
            TrafficEvent(
                id = entity.id,
                headline = entity.headline,
                description = entity.description,
                location = LatLng(entity.latitude, entity.longitude),
                severity = entity.severity,
                type = entity.type,
                status = entity.status
            )
        }
    }

    suspend fun refreshTraffic(bbox: String? = null) {
        try {
            val events = apiService.fetchEvents(bbox)
            if (events.isNotEmpty()) {
                val entities = events.map { event ->
                    TrafficEventEntity(
                        id = event.id,
                        headline = event.headline,
                        description = event.description,
                        latitude = event.location.latitude,
                        longitude = event.location.longitude,
                        severity = event.severity,
                        type = event.type,
                        status = event.status
                    )
                }
                dao.refreshTrafficEvents(entities)
            }
        } catch (e: Exception) {
            // In case of network error, we just don't refresh and keep cached data
            android.util.Log.e("TrafficRepository", "Failed to refresh traffic", e)
        }
    }
}
