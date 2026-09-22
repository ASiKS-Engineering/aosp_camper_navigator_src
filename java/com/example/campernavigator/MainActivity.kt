package com.example.campernavigator

import android.Manifest
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.campernavigator.data.SearchRepository
import com.example.campernavigator.data.TrafficRepository
import com.example.campernavigator.overlay.AudioOverlayManager
import com.example.campernavigator.service.FakeRoutingService
import com.example.campernavigator.service.LocationProviderFactory
import com.example.campernavigator.service.VoiceService
import com.example.campernavigator.ui.map.MapScreen
import com.example.campernavigator.ui.map.MapViewModel
import com.example.campernavigator.ui.map.NavigationUiMode
import com.example.campernavigator.ui.splash.SplashScreen
import com.example.campernavigator.ui.theme.CamperNavigatorTheme
import com.example.campernavigator.util.ConnectivityObserver
import com.example.campernavigator.util.FileLogger
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_SET_CAMPER_NAVIGATOR_MODE =
            "com.asiks.camper.navigator.action.SET_MODE"

        const val EXTRA_CAMER_NAVIGATOR_MODE =
            "com.asiks.camper.navigator.extra.MODE"

        const val EXTRA_APPLY_SCREEN_TRANSITION =
            "com.asiks.camper.navigator.extra.APPLY_SCREEN_TRANSITION"

        const val ACTION_NAVIGATION_UI_MODE_CHANGED =
            "com.example.campernavigator.action.NAVIGATION_UI_MODE_CHANGED"

        const val EXTRA_NAVIGATION_UI_MODE =
            "com.example.campernavigator.extra.NAVIGATION_UI_MODE"

        const val EXTRA_SHOW_MENU = "com.example.campernavigator.extra.SHOW_MENU"

        private const val NAVIGATION_STATE_PREFS = "navigation_state"
        private const val KEY_LAST_UI_MODE = "last_ui_mode"

        const val MODE_HOME = "HOME"
        const val MODE_FULLSCREEN = "FULLSCREEN"
    }

    private var mapViewModel: MapViewModel? = null
    private var showSettingsMenu by mutableStateOf(false)
    private var audioOverlayManager: AudioOverlayManager? = null
    private var isInHomeMode = false

    private val navigationUiModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_NAVIGATION_UI_MODE_CHANGED) {
                return
            }

            when (intent.getStringExtra(EXTRA_NAVIGATION_UI_MODE)) {
                MODE_HOME -> {
                    FileLogger.log("MainActivity: navigation UI mode -> HOME")
                    setNavigationUiMode(MODE_HOME)
                }

                MODE_FULLSCREEN -> {
                    FileLogger.log("MainActivity: navigation UI mode -> FULLSCREEN")
                    setNavigationUiMode(MODE_FULLSCREEN)
                }
            }
        }
    }

    private val shutdownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SHUTDOWN) {
                val lastMode = getSharedPreferences(NAVIGATION_STATE_PREFS, MODE_PRIVATE)
                    .getString(KEY_LAST_UI_MODE, MODE_HOME) ?: MODE_HOME
                persistToLumFile(lastMode)
                FileLogger.log("MainActivity: Persisted mode to LUM on shutdown: $lastMode")
            }
        }
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        FileLogger.log("MainActivity: Permissions checked: $permissions")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        FileLogger.init(applicationContext)
        NavigatorRuntime.initialize(applicationContext)
        FileLogger.log("!!! CamperNavigator Boot - MainActivity - Version 1.2.0 !!!")

        window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Initialize overlay manager
        audioOverlayManager = AudioOverlayManager(applicationContext)

        // Configure window for proper multi-window rendering
        configureWindowForMultiWindow()

        applyNavigationIntent(intent)

        NavigatorRuntime.configureMapRuntime(applicationContext)

        ContextCompat.registerReceiver(
            this,
            navigationUiModeReceiver,
            IntentFilter(ACTION_NAVIGATION_UI_MODE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )

        ContextCompat.registerReceiver(
            this,
            shutdownReceiver,
            IntentFilter(Intent.ACTION_SHUTDOWN),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        enableEdgeToEdge()
        setContent {
            CamperNavigatorTheme {
                val downloadManager = remember {
                    NavigatorRuntime.mapDownloadManager(applicationContext)
                }
                val vehicleManager = remember {
                    NavigatorRuntime.vehicleManager(applicationContext)
                }
                val locationProvider = remember {
                    LocationProviderFactory().create(applicationContext)
                }
                val graphHopperEngine = remember {
                    NavigatorRuntime.graphHopperEngine(applicationContext)
                }
                val searchService = remember {
                    NavigatorRuntime.searchService(applicationContext)
                }
                val voiceService = remember { VoiceService(applicationContext) }
                val trafficService = remember {
                    NavigatorRuntime.open511Service(applicationContext)
                }
                val database = remember {
                    NavigatorRuntime.appDatabase(applicationContext)
                }
                val destinationDao = remember { database.destinationDao() }
                val trafficDao = remember { database.trafficEventDao() }
                val cachedSearchDao = remember { database.cachedSearchDao() }

                val searchRepository = remember {
                    SearchRepository(searchService, cachedSearchDao)
                }
                val trafficRepository = remember {
                    TrafficRepository(trafficService, trafficDao)
                }
                val connectivityObserver = remember {
                    ConnectivityObserver(applicationContext)
                }

                val mapViewModelInstance: MapViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return MapViewModel(
                                graphHopperEngine.getRoutingService() ?: FakeRoutingService(),
                                downloadManager,
                                graphHopperEngine,
                                searchRepository,
                                voiceService,
                                destinationDao,
                                trafficRepository,
                                connectivityObserver,
                                vehicleManager,
                                locationProvider
                            ) as T
                        }
                    }
                )
                mapViewModel = mapViewModelInstance

                val initialMode = intent?.getStringExtra(EXTRA_NAVIGATION_UI_MODE)
                    ?: getSharedPreferences(NAVIGATION_STATE_PREFS, MODE_PRIVATE)
                        .getString(KEY_LAST_UI_MODE, MODE_HOME)
                    ?: MODE_HOME
                mapViewModelInstance.setNavigationUiMode(
                    if (initialMode == MODE_FULLSCREEN) {
                        NavigationUiMode.FULLSCREEN
                    } else {
                        NavigationUiMode.HOME
                    }
                )

                val uiState by mapViewModelInstance.uiState.collectAsState()
                var forceHideSplash by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    delay(5000)
                    forceHideSplash = true
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        MapScreen(
                            viewModel = mapViewModelInstance,
                            openSettingsMenu = showSettingsMenu,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (!uiState.isMapReady && !forceHideSplash) {
                            SplashScreen()
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        applyNavigationIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        FileLogger.log("MainActivity: onResume")
        mapViewModel?.startLocationTracking()
    }

    override fun onPause() {
        super.onPause()
        FileLogger.log("MainActivity: onPause")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(navigationUiModeReceiver)
        } catch (_: Exception) {
        }
        try {
            unregisterReceiver(shutdownReceiver)
        } catch (_: Exception) {
        }
        
        // Clean up overlay manager
        audioOverlayManager?.cleanup()
        FileLogger.log("MainActivity: onDestroy - AudioOverlayManager cleaned up")
    }

    private fun checkGpsSettings() {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
            false
        }

        if (!isGpsEnabled) {
            FileLogger.log("MainActivity: GPS is DISABLED, attempting background enable...")
            try {
                Settings.Secure.putInt(contentResolver, Settings.Secure.LOCATION_MODE, 3)
                FileLogger.log("MainActivity: SUCCESS: Force-enabled Location Mode 3")
            } catch (e: Exception) {
                FileLogger.log(
                    "MainActivity: Could not force enable GPS via Secure Settings: ${e.message}"
                )
            }
        }

        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                }
            }
        } catch (e: Exception) {
            FileLogger.log("MainActivity: Battery Optimization request failed: ${e.message}")
        }
    }

    private fun applyNavigationIntent(intent: Intent?) {
        showSettingsMenu = intent?.getBooleanExtra(EXTRA_SHOW_MENU, false) == true

        when (intent?.getStringExtra(EXTRA_NAVIGATION_UI_MODE)) {
            MODE_HOME -> setNavigationUiMode(MODE_HOME)
            MODE_FULLSCREEN -> setNavigationUiMode(MODE_FULLSCREEN)
            else -> {
                val lastMode = getSharedPreferences(NAVIGATION_STATE_PREFS, MODE_PRIVATE)
                    .getString(KEY_LAST_UI_MODE, MODE_HOME) ?: MODE_HOME
                setNavigationUiMode(lastMode)
            }
        }
    }

    private fun setNavigationUiMode(mode: String) {
        getSharedPreferences(NAVIGATION_STATE_PREFS, MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_UI_MODE, mode)
            .apply()
        
        val uiMode = if (mode == MODE_FULLSCREEN) NavigationUiMode.FULLSCREEN else NavigationUiMode.HOME
        mapViewModel?.setNavigationUiMode(uiMode)
        
        // Update window management states
        when (mode) {
            MODE_HOME -> {
                FileLogger.log("MainActivity: setNavigationUiMode -> HOME: Launcher coming to foreground")
                mapViewModel?.setLauncherInForeground(true)
                mapViewModel?.setMapVisible(true)  // Map still visible behind launcher
                isInHomeMode = true
            }
            MODE_FULLSCREEN -> {
                FileLogger.log("MainActivity: setNavigationUiMode -> FULLSCREEN: Map in full foreground")
                mapViewModel?.setLauncherInForeground(false)
                mapViewModel?.setMapVisible(true)  // Map is fully visible
                isInHomeMode = false
            }
        }
    }

    private fun configureWindowForMultiWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val params = window.attributes
            params.flags =
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
            window.attributes = params
        }
    }

    private fun persistToLumFile(mode: String) {
        try {
            val lumFile = File(filesDir, "nav_ui_mode.lum")
            lumFile.writeText(mode)
        } catch (e: Exception) {
            FileLogger.log("MainActivity: Failed to persist mode to LUM file: ${e.message}")
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_HOME) {
            FileLogger.log("MainActivity: HOME key pressed")
            if (isInHomeMode) {
                moveTaskToBack(true)
            } else {
                isInHomeMode = true
                setNavigationUiMode(MODE_HOME)
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isInHomeMode) {
            moveTaskToBack(true)
        }
    }

    fun bringTaskToFront(context: Context) {
        val packageName = context.packageName
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.getRunningTasks(Int.MAX_VALUE).forEach { taskInfo ->
            if (taskInfo.topActivity?.packageName == packageName) {
                am.moveTaskToFront(taskInfo.id, 0)
                return
            }
        }
    }
}
