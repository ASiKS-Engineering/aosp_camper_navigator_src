package com.example.campernavigator.data

import com.example.campernavigator.service.SearchResult
import com.example.campernavigator.service.SearchService
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import com.example.campernavigator.service.CampingFeature

class SearchRepository(
    private val searchService: SearchService,
    private val cachedSearchDao: CachedSearchDao
) {
    suspend fun search(query: String, bias: LatLng? = null): List<SearchResult> {
        return try {
            val results = searchService.search(query, bias)
            if (results.isNotEmpty()) {
                cacheResults(query, results)
            }
            results
        } catch (e: Exception) {
            android.util.Log.e("SearchRepository", "Search failed, checking cache", e)
            getCache(query)
        }
    }

    suspend fun reverseGeocode(location: LatLng): String? {
        return searchService.reverseGeocode(location)
    }

    private suspend fun cacheResults(query: String, results: List<SearchResult>) {
        val jsonArray = JSONArray()
        results.forEach { result ->
            val jsonObject = JSONObject().apply {
                put("name", result.name)
                put("description", result.description)
                put("lat", result.location.latitude)
                put("lon", result.location.longitude)
                put("features", JSONArray(result.campingFeatures.map { it.name }))
            }
            jsonArray.put(jsonObject)
        }
        cachedSearchDao.insert(CachedSearchEntity(query, jsonArray.toString()))
    }

    private suspend fun getCache(query: String): List<SearchResult> {
        val cached = cachedSearchDao.getCachedResult(query) ?: return emptyList()
        val jsonArray = JSONArray(cached.resultsJson)
        val results = mutableListOf<SearchResult>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val features = mutableListOf<CampingFeature>()
            val featuresArray = obj.getJSONArray("features")
            for (j in 0 until featuresArray.length()) {
                features.add(CampingFeature.valueOf(featuresArray.getString(j)))
            }
            results.add(
                SearchResult(
                    name = obj.getString("name"),
                    description = obj.getString("description"),
                    location = LatLng(obj.getDouble("lat"), obj.getDouble("lon")),
                    campingFeatures = features
                )
            )
        }
        return results
    }
}
