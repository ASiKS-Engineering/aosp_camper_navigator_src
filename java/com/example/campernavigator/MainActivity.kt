package com.example.campernavigator

import android.Manifest
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.campernavigator.service.FakeRoutingService
import com.example.campernavigator.service.GraphHopperEngine
import com.example.campernavigator.service.MapDownloadManager
import com.example.campernavigator.service.VehicleManager
import com.example.campernavigator.service.SearchService
import com.example.campernavigator.service.VoiceService
import com.example.campernavigator.data.SearchRepository
import com.example.campernavigator.data.TrafficRepository
import com.example.campernavigator.util.ConnectivityObserver
import com.example.campernavigator.ui.map.MapScreen
import com.example.campernavigator.ui.map.MapViewModel
import com.example.campernavigator.ui.splash.SplashScreen
import com.example.campernavigator.ui.theme.CamperNavigatorTheme
import com.example.campernavigator.util.FileLogger
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.campernavigator.data.AppDatabase
import com.example.campernavigator.service.LocationProviderFactory
import com.example.campernavigator.service.Open511Service
import com.example.campernavigator.util.TileInterceptor
import kotlinx.coroutines.delay
import org.maplibre.android.module.http.HttpRequestUtil
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Context
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

class MainActivity : ComponentActivity() {
    
    private var mapViewModel: MapViewModel? = null

    private val visibilityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.example.campernavigator.LOCAL_VISIBILITY_CHANGED") {
                val visible = intent.getBooleanExtra("VISIBLE", true)
                FileLogger.log("MainActivity: Local visibility update received: $visible")
                mapViewModel?.setLauncherVisibility(visible)
            }
        }
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions -> 
        FileLogger.log("MainActivity: Permissions checked: $permissions")
        checkGpsSettings()
    }

    private fun checkGpsSettings() {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            false
        }
        
        if (!isGpsEnabled) {
            FileLogger.log("MainActivity: GPS is DISABLED, attempting background enable...")
            // Versuchen wir, es via Secure Settings zu erzwingen (falls Berechtigung via ADB erteilt wurde)
            try {
                Settings.Secure.putInt(contentResolver, Settings.Secure.LOCATION_MODE, 3)
                FileLogger.log("MainActivity: SUCCESS: Force-enabled Location Mode 3")
            } catch (e: Exception) {
                FileLogger.log("MainActivity: Could not force enable GPS via Secure Settings: ${e.message}")
                // Wir verzichten hier auf den Intent-Fallback, wenn es am Pi 5 zu Popups führt
            }
        }
        
        // Battery Optimierung anfragen (Nur wenn die Activity existiert)
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                // Auf dem Pi 5 Automotive Build gibt es oft keine Battery Settings Activity
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                }
            }
        } catch (e: Exception) {
            FileLogger.log("MainActivity: Battery Optimization request failed: ${e.message}")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val showMenu = intent.getBooleanExtra("SHOW_MENU", false)
        FileLogger.log("MainActivity: onNewIntent received - SHOW_MENU=$showMenu")
        if (showMenu) {
            mapViewModel?.setLauncherVisibility(true)
        }
    }

    override fun onResume() {
        super.onResume()
        val inMultiWindow = isInMultiWindowMode
        FileLogger.log("MainActivity: onResume - inMultiWindow=$inMultiWindow")
        
        // AUTO-DETECTION: If we are NOT in multi-window (TaskView), we are fullscreen.
        if (!inMultiWindow) {
            FileLogger.log("MainActivity: Fullscreen detected, forcing menu visibility ON")
            mapViewModel?.setLauncherVisibility(true)
        } else {
            FileLogger.log("MainActivity: TaskView (Home) detected, menu visibility handled by Launcher signal")
        }
        
        mapViewModel?.startLocationTracking()
    }

    override fun onPause() {
        super.onPause()
        FileLogger.log("MainActivity: onPause")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(visibilityReceiver)
        } catch (e: Exception) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        FileLogger.init(applicationContext)
        FileLogger.log("!!! CamperNavigator Boot - MainActivity - Version 1.2.0 !!!")
        
        window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        MapLibre.getInstance(this)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(visibilityReceiver, IntentFilter("com.example.campernavigator.LOCAL_VISIBILITY_CHANGED"),
                RECEIVER_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(visibilityReceiver, IntentFilter("com.example.campernavigator.LOCAL_VISIBILITY_CHANGED"))
        }
        
        val mapHttpClient = OkHttpClient.Builder()
            .addInterceptor(TileInterceptor())
            .build()
        HttpRequestUtil.setOkHttpClient(mapHttpClient)
        
        locationPermissionRequest.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))

        enableEdgeToEdge()
        setContent {
            CamperNavigatorTheme {
                val okHttpClient = remember { OkHttpClient() }
                val downloadManager = remember { MapDownloadManager(applicationContext) }
                val vehicleManager = remember { VehicleManager(applicationContext) }
                val locationProvider = remember { LocationProviderFactory().create(applicationContext) }
                val graphHopperEngine = remember { GraphHopperEngine(applicationContext) }
                val searchService = remember { SearchService(okHttpClient) }
                val voiceService = remember { VoiceService(applicationContext) }
                val trafficService = remember { Open511Service(okHttpClient) }
                val database = remember { AppDatabase.getDatabase(applicationContext) }
                val destinationDao = remember { database.destinationDao() }
                val trafficDao = remember { database.trafficEventDao() }
                val cachedSearchDao = remember { database.cachedSearchDao() }
                
                val searchRepository = remember { SearchRepository(searchService, cachedSearchDao) }
                val trafficRepository = remember { TrafficRepository(trafficService, trafficDao) }
                val connectivityObserver = remember { ConnectivityObserver(applicationContext) }
                
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

                val uiState by mapViewModelInstance.uiState.collectAsState()
                var forceHideSplash by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    delay(5000) // Nach 5 Sek. Splash auf jeden Fall weg (Pi 5 Safe)
                    forceHideSplash = true
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        MapScreen(viewModel = mapViewModelInstance, modifier = Modifier.fillMaxSize())
                        
                        if (!uiState.isMapReady && !forceHideSplash) {
                            SplashScreen()
                        }
                    }
                }
            }
        }
    }
}
