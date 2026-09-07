package com.example.campernavigator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.campernavigator.util.FileLogger

/**
 * Receiver for Android Automotive launcher visibility events.
 * Triggered when the user switches between Home and Navigation screens.
 */
class LauncherMapsVisibilityReceiver : BroadcastReceiver() {
    
    // We'll need a way to communicate with the ViewModel. 
    // Since we don't have a singleton ViewModel yet, we'll use a listener or a global state for now, 
    // or just log it to verify the intent works.
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.android.car.carlauncher.action.MAPS_VISIBILITY_CHANGED") {
            val visible = intent.getBooleanExtra("com.android.car.carlauncher.extra.MAPS_VISIBLE", true)
            FileLogger.log("LauncherMapsVisibilityReceiver: MAPS_VISIBLE = $visible")
            
            // Notification mechanism for the app
            val updateIntent = Intent("com.example.campernavigator.LOCAL_VISIBILITY_CHANGED")
            updateIntent.putExtra("VISIBLE", visible)
            context.sendBroadcast(updateIntent)
        }
    }
}
