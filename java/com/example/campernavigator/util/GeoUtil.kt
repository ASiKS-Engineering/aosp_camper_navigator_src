package com.example.campernavigator.util

object GeoUtil {
    /**
     * Normalisiert den Kurswert auf den Bereich [0, 360).
     * Android wirft eine IllegalArgumentException, wenn der Wert exakt 360.0 oder höher ist.
     */
    fun normalizeBearing(bearing: Float): Float {
        if (!bearing.isFinite()) return 0f
        var normalized = bearing % 360f
        if (normalized < 0f) normalized += 360f
        // Sicherheitshalber: Wenn das Ergebnis 360.0 ist (durch Rundungsfehler), auf 0 setzen
        return if (normalized >= 360f || normalized.isNaN()) 0f else normalized
    }
}
