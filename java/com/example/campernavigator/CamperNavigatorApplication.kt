package com.example.campernavigator

import android.app.Application
import com.example.campernavigator.util.FileLogger

class CamperNavigatorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FileLogger.init(applicationContext)
        NavigatorRuntime.initialize(applicationContext)
    }
}