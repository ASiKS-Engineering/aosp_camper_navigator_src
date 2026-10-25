package com.example.campernavigator.util

import java.io.File
import java.io.FileInputStream

/**
 * The GPU driver keeps one memory mapping per GL buffer, and the kernel limits the mappings per process
 * (vm.max_map_count, 65530 by default). When the limit is hit the driver aborts the whole app, so the map
 * is asked to release its buffers well before that.
 */
object MappingUsage {
    private const val DEFAULT_LIMIT = 65_530

    fun limit(): Int = try {
        File("/proc/sys/vm/max_map_count").readText().trim().toInt()
    } catch (e: Exception) {
        DEFAULT_LIMIT
    }

    /** Number of memory mappings of this process (lines of /proc/self/maps), or -1 if unreadable. */
    fun count(): Int = try {
        var lines = 0
        val buffer = ByteArray(64 * 1024)
        FileInputStream("/proc/self/maps").use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                for (i in 0 until read) if (buffer[i] == '\n'.code.toByte()) lines++
            }
        }
        lines
    } catch (e: Exception) {
        -1
    }
}
