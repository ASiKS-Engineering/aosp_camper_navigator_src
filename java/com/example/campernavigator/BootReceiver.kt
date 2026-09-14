package com.example.campernavigator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val NAVIGATION_STATE_PREFS = "navigation_state"
        private const val KEY_LAST_UI_MODE = "last_ui_mode"
        private const val MODE_FULLSCREEN = "FULLSCREEN"
        private const val MODE_HOME = "HOME"
        private const val EXTRA_NAVIGATION_UI_MODE =
            "com.example.campernavigator.extra.NAVIGATION_UI_MODE"
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

        val lastMode = context.getSharedPreferences(
            NAVIGATION_STATE_PREFS,
            Context.MODE_PRIVATE
        ).getString(KEY_LAST_UI_MODE, MODE_HOME) ?: MODE_HOME

        val launchIntent = if (lastMode == MODE_FULLSCREEN) {
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_NAVIGATION_UI_MODE, MODE_FULLSCREEN)
            }
        } else {
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(launchIntent)
    }
}