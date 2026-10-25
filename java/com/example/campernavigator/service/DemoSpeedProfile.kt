package com.example.campernavigator.service

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Speed profile along a route for the demo drive: the vehicle accelerates and brakes with limited
 * acceleration, drives at the modelled speed of each road segment (which already includes the vehicle's
 * speed limit) and slows down for curves and turns. It starts and ends standing.
 *
 * The profile is the classic "velocity profile": a per-vertex speed ceiling from road speed and curvature,
 * limited by a forward pass (acceleration) and a backward pass (braking), so the vehicle is slow before
 * a corner and not only in it.
 */
class DemoSpeedProfile private constructor(
    /** Length in meters of each segment i -> i + 1. */
    val segmentLengths: DoubleArray,
    /** Speed in m/s at each route vertex. */
    private val vertexSpeed: DoubleArray
) {
    val totalLength: Double = segmentLengths.sum()

    /** Speed in m/s [offset] meters into [segment]; constant acceleration within the segment. */
    fun speedAt(segment: Int, offset: Double): Double {
        val v0 = vertexSpeed[segment]
        val v1 = vertexSpeed[segment + 1]
        val len = segmentLengths[segment]
        val f = if (len > 0.0) (offset / len).coerceIn(0.0, 1.0) else 1.0
        // Never exactly zero, otherwise a vehicle standing at the start could not set off.
        return max(MIN_MOVING_MS, sqrt(max(0.0, v0 * v0 + (v1 * v1 - v0 * v0) * f)))
    }

    fun vertexSpeedMs(index: Int): Double = vertexSpeed[index]

    class Params(
        val accelMs2: Double = 1.0,
        val brakeMs2: Double = 1.6,
        val lateralMs2: Double = 1.6,
        val minCurveSpeedMs: Double = 2.8,
        val fallbackKmh: Double = 50.0
    )

    companion object {
        private const val MIN_MOVING_MS = 0.8
        private const val EARTH_M_PER_DEG = 111_320.0
        private val CURVE_WINDOWS_M = doubleArrayOf(10.0, 30.0, 80.0)

        fun build(lat: DoubleArray, lon: DoubleArray, segmentSpeedKmh: FloatArray?, p: Params = Params()): DemoSpeedProfile {
            val n = lat.size
            require(n >= 2 && lon.size == n) { "A route needs at least two points" }

            // Local plane in meters; accurate enough for the short distances a curve window spans.
            val x = DoubleArray(n)
            val y = DoubleArray(n)
            val cosLat = cos(Math.toRadians(lat[0]))
            for (i in 0 until n) {
                x[i] = (lon[i] - lon[0]) * cosLat * EARTH_M_PER_DEG
                y[i] = (lat[i] - lat[0]) * EARTH_M_PER_DEG
            }
            val len = DoubleArray(n - 1) { Math.hypot(x[it + 1] - x[it], y[it + 1] - y[it]) }
            val s = DoubleArray(n)
            for (i in 1 until n) s[i] = s[i - 1] + len[i - 1]

            val ceiling = DoubleArray(n)
            for (i in 0 until n) {
                val before = if (i > 0) roadSpeedMs(segmentSpeedKmh, i - 1, p) else Double.POSITIVE_INFINITY
                val after = if (i < n - 1) roadSpeedMs(segmentSpeedKmh, i, p) else Double.POSITIVE_INFINITY
                ceiling[i] = min(before, after)
                if (i in 1 until n - 1) ceiling[i] = min(ceiling[i], curveSpeedMs(x, y, s, i, p))
            }
            ceiling[0] = 0.0
            ceiling[n - 1] = 0.0

            val v = ceiling.copyOf()
            for (i in 1 until n) v[i] = min(v[i], sqrt(v[i - 1] * v[i - 1] + 2 * p.accelMs2 * len[i - 1]))
            for (i in n - 2 downTo 0) v[i] = min(v[i], sqrt(v[i + 1] * v[i + 1] + 2 * p.brakeMs2 * len[i]))
            return DemoSpeedProfile(len, v)
        }

        private fun roadSpeedMs(speedKmh: FloatArray?, segment: Int, p: Params): Double {
            val kmh = speedKmh?.getOrNull(segment)?.toDouble()?.takeIf { it > 0.0 } ?: p.fallbackKmh
            return kmh / 3.6
        }

        // Highest speed through vertex i so that the lateral acceleration stays comfortable. Several window
        // sizes catch both tight corners and long gentle bends; the arc is capped so that a sharp corner
        // between two long polyline segments is still treated as a corner.
        private fun curveSpeedMs(x: DoubleArray, y: DoubleArray, s: DoubleArray, i: Int, p: Params): Double {
            var best = Double.POSITIVE_INFINITY
            for (w in CURVE_WINDOWS_M) {
                val j = lastIndexAtMost(s, s[i] - w, i - 1)
                val k = firstIndexAtLeast(s, s[i] + w, i + 1)
                val arc = min(s[k] - s[j], 2 * w)
                if (arc <= 0.0) continue
                val ax = x[i] - x[j]; val ay = y[i] - y[j]
                val bx = x[k] - x[i]; val by = y[k] - y[i]
                val turn = abs(atan2(ax * by - ay * bx, ax * bx + ay * by))
                val curvature = turn / arc
                if (curvature > 1e-5) best = min(best, sqrt(p.lateralMs2 / curvature))
            }
            return max(best, p.minCurveSpeedMs)
        }

        private fun lastIndexAtMost(s: DoubleArray, target: Double, upper: Int): Int {
            var j = upper
            while (j > 0 && s[j] > target) j--
            return j
        }

        private fun firstIndexAtLeast(s: DoubleArray, target: Double, lower: Int): Int {
            var k = lower
            while (k < s.size - 1 && s[k] < target) k++
            return k
        }
    }
}
