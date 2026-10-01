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

        // Do NOT start MainActivity or a HOME intent here. CarLauncher is already the running
        // home and embeds MainActivity in its own TaskView. A HOME start at BOOT_COMPLETED
        // brings the home root task to front and moves the embedded nav task to the back,
        // where it stays hidden (empty nav area).
    }
}