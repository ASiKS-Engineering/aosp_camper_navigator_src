package com.example.campernavigator.data

import androidx.room.TypeConverter
import org.maplibre.android.geometry.LatLng
import org.json.JSONArray
import com.example.campernavigator.service.CampingFeature

class Converters {
    @TypeConverter
    fun fromLatLng(latLng: LatLng): String {
        return "${latLng.latitude},${latLng.longitude}"
    }

    @TypeConverter
    fun toLatLng(value: String): LatLng {
        val split = value.split(",")
        return LatLng(split[0].toDouble(), split[1].toDouble())
    }

    @TypeConverter
    fun fromCampingFeatureList(value: List<CampingFeature>): String {
        return JSONArray(value.map { it.name }).toString()
    }

    @TypeConverter
    fun toCampingFeatureList(value: String): List<CampingFeature> {
        val array = JSONArray(value)
        val list = mutableListOf<CampingFeature>()
        for (i in 0 until array.length()) {
            list.add(CampingFeature.valueOf(array.getString(i)))
        }
        return list
    }
}
