package com.example.campernavigator.util

object JsonUtil {
    /**
     * Sanitizes a JSON string by:
     * 1. Replacing commas used as decimal separators with dots (e.g. "5,8" -> "5.8")
     * 2. Removing trailing commas before closing braces/brackets
     * 3. Adding missing commas between closing braces and next keys
     */
    fun sanitizeJson(json: String): String {
        var result = json
        
        // 1. Physische Dezimal-Kommata fixen (z.B. 5,8 -> 5.8)
        result = result.replace(Regex("(?<=\\d),(?=\\d)"), ".")
        
        // 2. Trailing commas entfernen (z.B. "a": 1, } -> "a": 1 })
        result = result.replace(Regex(",\\s*([}\\]])"), "$1")
        
        // 3. Fehlende Kommata zwischen Objekten/Arrays und dem nächsten Key fixen (z.B. } "builder" -> }, "builder")
        result = result.replace(Regex("([}\\]])\\s*\""), "$1, \"")
        
        return result
    }
}
