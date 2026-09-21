package com.example.campernavigator.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import com.example.campernavigator.util.FileLogger

/**
 * Manages overlay windows for audio controls and launcher UI elements
 * Handles multi-window z-order and visibility management
 */
class AudioOverlayManager(private val context: Context) {
    
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var isOverlayShown = false
    
    companion object {
        // Z-order layers for multi-window management
        const val Z_ORDER_LAUNCHER = 1000
        const val Z_ORDER_AUDIO_OVERLAY = 1500
        const val Z_ORDER_SYSTEM_UI = 2000
        const val Z_ORDER_NAVIGATOR_BACKGROUND = 500
    }

    /**
     * Shows the audio overlay window with proper z-order
     */
    fun showAudioOverlay(content: @Composable () -> Unit) {
        if (isOverlayShown) {
            FileLogger.log("AudioOverlayManager: Overlay already shown")
            return
        }

        try {
            val layoutParams = createOverlayLayoutParams(Z_ORDER_AUDIO_OVERLAY)
            
            val containerView = FrameLayout(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }

            // Create ComposeView for content
            val composeView = ComposeView(context).apply {
                setContent {
                    content()
                }
            }
            
            containerView.addView(composeView)
            windowManager.addView(containerView, layoutParams)
            
            overlayView = containerView
            isOverlayShown = true
            
            FileLogger.log("AudioOverlayManager: Overlay shown with z-order=$Z_ORDER_AUDIO_OVERLAY")
        } catch (e: Exception) {
            FileLogger.log("AudioOverlayManager ERROR: Failed to show overlay: ${e.message}")
        }
    }

    /**
     * Hides the audio overlay window
     */
    fun hideAudioOverlay() {
        if (!isOverlayShown || overlayView == null) {
            FileLogger.log("AudioOverlayManager: Overlay not shown, nothing to hide")
            return
        }

        try {
            windowManager.removeView(overlayView)
            overlayView = null
            isOverlayShown = false
            
            FileLogger.log("AudioOverlayManager: Overlay hidden")
        } catch (e: Exception) {
            FileLogger.log("AudioOverlayManager ERROR: Failed to hide overlay: ${e.message}")
        }
    }

    /**
     * Updates overlay visibility
     */
    fun setOverlayVisible(visible: Boolean) {
        if (visible && !isOverlayShown) {
            // Lazy creation - show with default content
            showAudioOverlay { /* Default empty content */ }
        } else if (!visible && isOverlayShown) {
            hideAudioOverlay()
        }
    }

    /**
     * Cleans up overlay resources
     */
    fun cleanup() {
        hideAudioOverlay()
    }

    /**
     * Creates WindowManager.LayoutParams for overlay windows
     */
    private fun createOverlayLayoutParams(zOrder: Int): WindowManager.LayoutParams {
        val layoutParams = WindowManager.LayoutParams()

        // Window type based on API level
        layoutParams.type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        // Basic layout properties
        layoutParams.format = PixelFormat.TRANSLUCENT
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
        layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
        
        // Z-order management
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        
        // Alpha blending for layering
        layoutParams.alpha = 1.0f

        FileLogger.log("AudioOverlayManager: LayoutParams created with type=${layoutParams.type}, z-order=$zOrder")

        return layoutParams
    }
}
