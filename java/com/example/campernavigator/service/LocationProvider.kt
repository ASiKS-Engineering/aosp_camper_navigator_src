package com.example.campernavigator.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.example.campernavigator.util.FileLogger
import com.example.campernavigator.util.GeoUtil
import com.example.campernavigator.util.NmeaParser

interface LocationProvider {
    fun startLocationUpdates(callback: (Location) -> Unit)
    fun stopLocationUpdates()
    fun getSatelliteCount(): Int
}

class LocationProviderFactory {
    fun create(context: Context): LocationProvider {
        return StandardLocationProvider(context)
    }
}

class StandardLocationProvider(context: Context) : LocationProvider, LocationListener {
    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var onLocationReceived: ((Location) -> Unit)? = null
    private var lastSatelliteCount = 0
    private var lastLogTime = 0L
    
    // NMEA-Bypass: Falls der Standard-Provider am Pi 5 keine Daten liefert
    private val nmeaListener = OnNmeaMessageListener { message, _ ->
        if (message.contains("RMC")) {
            val location = NmeaParser.parseRmc(message)
            if (location != null && location.latitude != 0.0) {
                onLocationChanged(location)
            }
        } else if (message.contains("GGA")) {
            val location = NmeaParser.parseGga(message)
            if (location != null && location.latitude != 0.0) {
                // Wir loggen den NMEA-Fix nur gelegentlich
                if (System.currentTimeMillis() - lastLogTime > 30000) {
                    FileLogger.log("LocationProvider: Fix via NMEA-GGA active (Alt: ${location.altitude}m)")
                    lastLogTime = System.currentTimeMillis()
                }
                onLocationChanged(location)
            }
        }
    }
    
    private val gnssStatusCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            for (i in 0 until status.satelliteCount) { 
                if (status.usedInFix(i)) used++ 
            }
            lastSatelliteCount = used
        }
    }

    @SuppressLint("MissingPermission")
    override fun startLocationUpdates(callback: (Location) -> Unit) {
        this.onLocationReceived = callback
        FileLogger.log("LocationProvider: Starting Hybrid Mode (Native + NMEA)")
        
        fun doRegister() {
            try {
                lm.addNmeaListener(nmeaListener, Handler(Looper.getMainLooper()))
                lm.registerGnssStatusCallback(gnssStatusCallback, Handler(Looper.getMainLooper()))
                
                val providers = lm.allProviders
                providers.forEach { p ->
                    if (lm.isProviderEnabled(p)) {
                        FileLogger.log("LocationProvider: Requesting $p updates")
                        lm.requestLocationUpdates(p, 1000L, 0f, this, Looper.getMainLooper())
                    }
                }
            } catch (e: Exception) {
                FileLogger.log("LocationProvider: Registration error: ${e.message}")
            }
        }

        doRegister()

        // AUTO-RECOVERY: Falls der Dienst gestoppt wird, alle 30 Sek neu versuchen
        Handler(Looper.getMainLooper()).postDelayed(object : Runnable {
            override fun run() {
                if (onLocationReceived != null) {
                    doRegister()
                    Handler(Looper.getMainLooper()).postDelayed(this, 30000)
                }
            }
        }, 30000)
    }

    override fun stopLocationUpdates() {
        try {
            lm.removeUpdates(this)
            lm.removeNmeaListener(nmeaListener)
            lm.unregisterGnssStatusCallback(gnssStatusCallback)
        } catch (e: Exception) {}
        onLocationReceived = null
    }

    override fun getSatelliteCount(): Int = lastSatelliteCount

    override fun onLocationChanged(l: Location) {
        if (l.latitude != 0.0 && l.longitude != 0.0) {
            // Periodic log to confirm data flow (every 30s)
            if (System.currentTimeMillis() - lastLogTime > 30000) {
                FileLogger.log("FIX RECEIVED: Lat=${l.latitude}, Lon=${l.longitude}, Acc=${l.accuracy}m via ${l.provider}")
                lastLogTime = System.currentTimeMillis()
            }
            
            // Sicherstellen, dass Bearing auch hier normalisiert ist (Zusatz-Schutz)
            if (l.hasBearing()) {
                l.bearing = GeoUtil.normalizeBearing(l.bearing)
            }
            onLocationReceived?.invoke(l)
        }
    }

    override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
}
