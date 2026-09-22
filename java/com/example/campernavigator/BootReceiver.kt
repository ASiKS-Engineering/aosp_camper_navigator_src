package com.example.campernavigator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.io.File
import java.nio.charset.StandardCharsets

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val NAVIGATION_STATE_PREFS = "navigation_state"
        private const val KEY_LAST_UI_MODE = "last_ui_mode"
        private const val MODE_FULLSCREEN = "FULLSCREEN"
        private const val MODE_HOME = "HOME"
        private const val EXTRA_NAVIGATION_UI_MODE =
            "com.example.campernavigator.extra.NAVIGATION_UI_MODE"
        private const val LUM_FILE_NAME = "nav_ui_mode.lum"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        try {
            Settings.Secure.putInt(
                context.contentResolver,
                Settings.Secure.LOCATION_MODE,
                3
            )
        } catch (_: SecurityException) {
            // The product default permission grant must be present in the image.
        }

        val lastMode = readPersistedNavigationUiMode(context)

        // Do NOT start MainActivity here as a standalone task: CarLauncher's TaskView already
        // embeds it via its own maps intent. Starting it separately and then immediately
        // requesting HOME below moves that freshly-started task straight to the back, where
        // nothing ever brings it forward again (the embedded nav view stays invisible forever).
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_NAVIGATION_UI_MODE, lastMode)
        }

        context.startActivity(launcherIntent)
    }

    private fun readPersistedNavigationUiMode(context: Context): String {
        // Try to read from LUM file first (source of truth from last shutdown)
        val lumFile = File(context.filesDir, LUM_FILE_NAME)
        if (lumFile.exists()) {
            return try {
                val content = lumFile.readText(StandardCharsets.UTF_8).trim()
                if (content == MODE_FULLSCREEN) MODE_FULLSCREEN else MODE_HOME
            } catch (e: Exception) {
                MODE_HOME
            }
        }

        // Fallback to SharedPreferences if LUM file not available
        return context.getSharedPreferences(
            NAVIGATION_STATE_PREFS,
            Context.MODE_PRIVATE
        ).getString(KEY_LAST_UI_MODE, MODE_HOME) ?: MODE_HOME
    }
}