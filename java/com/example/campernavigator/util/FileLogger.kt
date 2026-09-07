package com.example.campernavigator.util

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLogger {
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun init(context: Context) {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetFile = File(downloadDir, "camper_navi_logs.txt")
        
        try {
            if (!downloadDir.exists()) downloadDir.mkdirs()
            if (!targetFile.exists()) targetFile.createNewFile()
            logFile = targetFile
        } catch (e: Exception) {
            logFile = File(context.getExternalFilesDir(null), "camper_navi_logs.txt")
        }
        
        log("--- LOGGER START (Device: ${android.os.Build.MODEL}, API: ${android.os.Build.VERSION.SDK_INT}) ---")
        setupUncaughtExceptionHandler()
    }

    fun log(message: String, tag: String = "AppLog") {
        val timestamp = dateFormat.format(Date())
        val logLine = "$timestamp [$tag]: $message\n"
        
        Log.d(tag, message)

        try {
            logFile?.let { file ->
                FileWriter(file, true).use { writer ->
                    writer.append(logLine)
                    writer.flush()
                }
            }
        } catch (e: Exception) {
            Log.e("FileLogger", "Write failed: ${e.message}")
        }
    }

    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sw = java.io.StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            log("FATAL CRASH [${thread.name}]:\n$sw", "CRASH")
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
