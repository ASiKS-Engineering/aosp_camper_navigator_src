package com.example.campernavigator.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class CampingFeature {
    WATER, ELECTRICITY, WASTE, WIFI, SHOWER, PETS, GREY_WATER, BLACK_WATER
}

data class SearchResult(
    val name: String,
    val description: String,
    val location: LatLng,
    val campingFeatures: List<CampingFeature> = emptyList()
)

class SearchService(
    private val okHttpClient: OkHttpClient = OkHttpClient()
) {
    suspend fun search(query: String, bias: LatLng? = null): List<SearchResult> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val baseUrl = "https://photon.komoot.io/api/?q=${encodedQuery}&limit=10"
        val url = if (bias != null) {
            "$baseUrl&lat=${bias.latitude}&lon=${bias.longitude}"
        } else {
            baseUrl
        }
        val request = Request.Builder().url(url).build()
        
        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.e("SearchService", "Search failed: ${response.code}")
                return@withContext emptyList()
            }
            
            val jsonString = response.body?.string() ?: return@withContext emptyList()
            val root = JSONObject(jsonString)
            val features = root.getJSONArray("features")
            
            val results = mutableListOf<SearchResult>()
            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val geometry = feature.getJSONObject("geometry")
                val coords = geometry.getJSONArray("coordinates")
                val properties = feature.getJSONObject("properties")
                
                val name = properties.optString("name", "")
                val street = properties.optString("street", "")
                val housenumber = properties.optString("housenumber", "")
                val city = properties.optString("city", "")
                val country = properties.optString("country", "")

                // Erstelle einen aussagekräftigen Namen (z.B. "Hauptstraße 5" statt nur "5")
                val displayName = if (street.isNotEmpty() && housenumber.isNotEmpty()) {
                    "$street $housenumber"
                } else if (name.isNotEmpty()) {
                    name
                } else if (street.isNotEmpty()) {
                    street
                } else {
                    "Unbekannt"
                }

                // Erstelle eine Beschreibung (Ort, Land)
                val description = listOf(city, country).filter { it.isNotEmpty() }.joinToString(", ")
                
                val osmValue = properties.optString("osm_value", "")
                val features = mutableListOf<CampingFeature>()
                if (osmValue == "camp_site" || osmValue == "caravan_site" || query.contains("camping", ignoreCase = true)) {
                    // Da Photon keine detaillierten Tags liefert, simulieren wir hier 
                    // Features basierend auf dem Namen oder zufällig für die Demo.
                    // In einer echten App würde man hier eine zweite Anfrage an die Overpass API stellen.
                    if (displayName.length % 2 == 0) features.add(CampingFeature.WATER)
                    if (displayName.length % 3 == 0) features.add(CampingFeature.ELECTRICITY)
                    if (displayName.contains("Park") || displayName.length % 5 == 0) features.add(CampingFeature.WASTE)
                    if (displayName.length % 4 == 0) features.add(CampingFeature.WIFI)
                    if (osmValue == "camp_site") features.add(CampingFeature.SHOWER)
                    if (displayName.length % 7 == 0 || displayName.contains("Wiese", ignoreCase = true)) features.add(CampingFeature.PETS)
                    if (displayName.length % 3 == 0) features.add(CampingFeature.GREY_WATER)
                    if (displayName.length % 5 == 0 || osmValue == "caravan_site") features.add(CampingFeature.BLACK_WATER)
                }

                results.add(
                    SearchResult(
                        name = displayName,
                        description = description,
                        location = LatLng(coords.getDouble(1), coords.getDouble(0)),
                        campingFeatures = features
                    )
                )
            }
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun reverseGeocode(location: LatLng): String? = withContext(Dispatchers.IO) {
        val url = "https://photon.komoot.io/api/reverse?lon=${location.longitude}&lat=${location.latitude}"
        val request = Request.Builder().url(url).build()

        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val jsonString = response.body?.string() ?: return@withContext null
            val root = JSONObject(jsonString)
            val features = root.getJSONArray("features")
            if (features.length() == 0) return@withContext null

            val properties = features.getJSONObject(0).getJSONObject("properties")
            val street = properties.optString("street", "")
            val housenumber = properties.optString("housenumber", "")
            val ref = properties.optString("ref", "")

            when {
                street.isNotEmpty() -> if (housenumber.isNotEmpty()) "$street $housenumber" else street
                ref.isNotEmpty() -> ref
                else -> {
                    val name = properties.optString("name", "")
                    if (name.isNotEmpty()) name else null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
