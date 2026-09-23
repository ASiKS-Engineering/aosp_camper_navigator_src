package com.example.campernavigator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.io.File
import java.nio.charset.StandardCharsets

class BootReceiver : BroadcastReceiver() {
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

        // Do NOT start MainActivity here as a standalone task: CarLauncher's TaskView already
        // embeds it via its own maps intent. Starting it separately and then immediately
        // requesting HOME below moves that freshly-started task straight to the back, where
        // nothing ever brings it forward again (the embedded nav view stays invisible forever).
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        context.startActivity(launcherIntent)
    }
}