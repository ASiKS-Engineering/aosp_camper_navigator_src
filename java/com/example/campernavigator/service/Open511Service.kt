package com.example.campernavigator.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng

data class TrafficEvent(
    val id: String,
    val headline: String,
    val description: String,
    val location: LatLng,
    val severity: String,
    val type: String,
    val status: String
)

class Open511Service(
    private val okHttpClient: OkHttpClient = OkHttpClient()
) {
    // Standardmäßig DriveBC als Beispiel, kann aber übersteuert werden
    private var baseUrl: String = "https://api.open511.gov.bc.ca/events"

    suspend fun fetchEvents(bbox: String? = null): List<TrafficEvent> = withContext(Dispatchers.IO) {
        val url = if (bbox != null) "$baseUrl?in_bbox=$bbox" else baseUrl
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        
        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.e("Open511Service", "Traffic fetch failed: ${response.code}")
                return@withContext emptyList()
            }
            
            val jsonString = response.body?.string() ?: return@withContext emptyList()
            val root = JSONObject(jsonString)
            val events = root.getJSONArray("events")
            
            val results = mutableListOf<TrafficEvent>()
            for (i in 0 until events.length()) {
                val event = events.getJSONObject(i)
                val geography = event.optJSONObject("geography") ?: continue
                val type = geography.optString("type")
                
                // Wir unterstützen für den Anfang Punkt-Geometrien
                val location = if (type == "Point") {
                    val coords = geography.getJSONArray("coordinates")
                    LatLng(coords.getDouble(1), coords.getDouble(0))
                } else if (type == "LineString") {
                    // Fallback: Erster Punkt der Linie
                    val coords = geography.getJSONArray("coordinates").getJSONArray(0)
                    LatLng(coords.getDouble(1), coords.getDouble(0))
                } else continue

                results.add(
                    TrafficEvent(
                        id = event.optString("id"),
                        headline = event.optString("headline", "Verkehrsmeldung"),
                        description = event.optString("description", ""),
                        location = location,
                        severity = event.optString("severity", "UNKNOWN"),
                        type = event.optString("event_type", "UNKNOWN"),
                        status = event.optString("status", "ACTIVE")
                    )
                )
            }
            results
        } catch (e: Exception) {
            android.util.Log.e("Open511Service", "Error fetching traffic events", e)
            emptyList()
        }
    }

    fun setBaseUrl(url: String) {
        this.baseUrl = url
    }
}
