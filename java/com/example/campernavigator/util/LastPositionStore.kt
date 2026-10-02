package com.example.campernavigator.util

import android.content.Context
import com.example.campernavigator.NavigatorRuntime
import org.maplibre.android.geometry.LatLng

object LastPositionStore {
    private const val KEY_LAT = "last_position_lat_bits"
    private const val KEY_LON = "last_position_lon_bits"

    // Muenchen, Marienplatz
    val DEFAULT = LatLng(48.13743, 11.57549)

    fun load(context: Context): LatLng {
        val prefs = context.getSharedPreferences(NavigatorRuntime.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_LAT) || !prefs.contains(KEY_LON)) return DEFAULT
        val lat = Double.fromBits(prefs.getLong(KEY_LAT, 0L))
        val lon = Double.fromBits(prefs.getLong(KEY_LON, 0L))
        val valid = lat in -90.0..90.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0)
        return if (valid) LatLng(lat, lon) else DEFAULT
    }

    /** [sync] uses commit() so the value survives a process kill right after shutdown. */
    fun save(context: Context, position: LatLng, sync: Boolean) {
        val editor = context.getSharedPreferences(NavigatorRuntime.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAT, position.latitude.toRawBits())
            .putLong(KEY_LON, position.longitude.toRawBits())
        if (sync) editor.commit() else editor.apply()
    }
}
