package com.example.campernavigator

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.campernavigator.util.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NavigatorWarmupService : Service() {

    companion object {
        const val ACTION_PREWARM = "com.example.campernavigator.action.PREWARM"
        const val EXTRA_REASON = "com.example.campernavigator.extra.PREWARM_REASON"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reason = intent?.getStringExtra(EXTRA_REASON) ?: "unspecified"
        FileLogger.log("NavigatorWarmupService: Warmup requested ($reason)")

        serviceScope.launch {
            NavigatorRuntime.prewarm(applicationContext, reason)
            stopSelfResult(startId)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}