package com.example.campernavigator.util

import android.location.Location
import java.util.StringTokenizer

object NmeaParser {
    /**
     * Parsed einen $GPRMC oder $GNRMC Satz.
     * Format: $GPRMC,time,status,lat,N,lon,E,spd,cog,date,mag,dir,mode*checksum
     */
    fun parseRmc(nmea: String): Location? {
        try {
            if (!nmea.contains("RMC")) return null
            val tokens = nmea.split(",")
            if (tokens.size < 9) return null
            
            // Status A = Active, V = Void
            if (tokens[2] != "A") return null
            
            val loc = Location("nmea")
            
            // Latitude: DDMM.MMMM
            val rawLat = tokens[3]
            val latDir = tokens[4]
            if (rawLat.isNotEmpty()) {
                var lat = rawLat.substring(0, 2).toDouble() + rawLat.substring(2).toDouble() / 60.0
                if (latDir == "S") lat = -lat
                loc.latitude = lat
            }
            
            // Longitude: DDDMM.MMMM
            val rawLon = tokens[5]
            val lonDir = tokens[6]
            if (rawLon.isNotEmpty()) {
                var lon = rawLon.substring(0, 3).toDouble() + rawLon.substring(3).toDouble() / 60.0
                if (lonDir == "W") lon = -lon
                loc.longitude = lon
            }
            
            // Speed: knots to m/s (1 knot = 0.514444 m/s)
            val rawSpeed = tokens[7]
            if (rawSpeed.isNotEmpty()) {
                loc.speed = (rawSpeed.toFloat() * 0.514444f)
            }
            
            // Bearing (Course over ground)
            val rawBearing = tokens[8]
            if (rawBearing.isNotEmpty()) {
                loc.bearing = GeoUtil.normalizeBearing(rawBearing.toFloat())
            }
            
            loc.time = System.currentTimeMillis()
            loc.accuracy = 10f // Schätzung
            
            return loc
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Parsed einen $GPGGA oder $GNGGA Satz für Höheninformationen.
     * Format: $GPGGA,time,lat,N,lon,E,fix,sats,hdop,alt,M,...
     */
    fun parseGga(nmea: String): Location? {
        try {
            if (!nmea.contains("GGA")) return null
            val tokens = nmea.split(",")
            if (tokens.size < 10) return null
            
            // Fix Qualität 0 = ungültig
            if (tokens[6] == "0") return null
            
            val loc = Location("nmea_gga")
            
            // Latitude / Longitude (DDMM.MMMM)
            val rawLat = tokens[2]
            val latDir = tokens[3]
            if (rawLat.isNotEmpty()) {
                var lat = rawLat.substring(0, 2).toDouble() + rawLat.substring(2).toDouble() / 60.0
                if (latDir == "S") lat = -lat
                loc.latitude = lat
            }
            
            val rawLon = tokens[4]
            val lonDir = tokens[5]
            if (rawLon.isNotEmpty()) {
                var lon = rawLon.substring(0, 3).toDouble() + rawLon.substring(3).toDouble() / 60.0
                if (lonDir == "W") lon = -lon
                loc.longitude = lon
            }
            
            // ALITITUDE
            val rawAlt = tokens[9]
            if (rawAlt.isNotEmpty()) {
                loc.altitude = rawAlt.toDouble()
            }
            
            loc.time = System.currentTimeMillis()
            loc.accuracy = 10f
            
            return loc
        } catch (e: Exception) {
            return null
        }
    }
}
