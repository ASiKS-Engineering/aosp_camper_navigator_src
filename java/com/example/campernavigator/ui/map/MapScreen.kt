package com.example.campernavigator.ui.map

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowLeft
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.RoundaboutLeft
import androidx.compose.material.icons.filled.RoundaboutRight
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.RvHookup
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Shower
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Wc
import com.example.campernavigator.service.CampingFeature
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.FileUpload
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Videocam
import org.maplibre.android.annotations.MarkerOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.campernavigator.model.RoutingMode
import com.example.campernavigator.service.DownloadState
import com.example.campernavigator.ui.components.VehicleProfileSelector
import com.example.campernavigator.util.FileLogger
import com.example.campernavigator.util.GeoUtil
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.OnLocationCameraTransitionListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

@Composable
fun MapScreen(
    viewModel: MapViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var isMenuOpen by remember { mutableStateOf(false) }
    var isPanelExpanded by remember { mutableStateOf(false) } // Default eingeklappt
    var isMapManagementOpen by remember { mutableStateOf(false) }
    var isFavoritesOpen by remember { mutableStateOf(false) }
    var isCampingSubmenuOpen by remember { mutableStateOf(false) }
    var campingSearchQuery by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchOverlayVisible by remember { mutableStateOf(false) }
    var isSettingHome by remember { mutableStateOf(false) }
    var hasInitialLocationZoom by remember { mutableStateOf(false) }
    var showMapClickDialog by remember { mutableStateOf<LatLng?>(null) }
    var lastInteractionTime by remember { mutableLongStateOf(0L) }
    val focusRequester = remember { FocusRequester() }

    // Zeit für die Statusbar-Aktualisierung
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(10000) // Alle 10 Sekunden aktualisieren
        }
    }

    // Automatisch einklappen, wenn eine Route berechnet wurde oder die Navigation startet
    LaunchedEffect(uiState.currentRoute, uiState.isNavigating) {
        if (uiState.currentRoute != null || uiState.isNavigating) {
            isPanelExpanded = false
        }
    }

    // Automatisch einklappen nach 5 Sekunden, wenn Navigation aktiv ist
    LaunchedEffect(isPanelExpanded, uiState.isNavigating) {
        if (isPanelExpanded && uiState.isNavigating) {
            delay(5000)
            isPanelExpanded = false
        }
    }

    DisposableEffect(uiState.mapMode) {
        val sensorManager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val lightSensor = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_LIGHT)
        
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                val lux = event.values[0]
                viewModel.updateNightMode(lux)
            }
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
        }
        
        if (uiState.mapMode == com.example.campernavigator.ui.map.MapMode.AUTO && lightSensor != null) {
            sensorManager.registerListener(listener, lightSensor, android.hardware.SensorManager.SENSOR_DELAY_UI)
        } else if (uiState.mapMode == com.example.campernavigator.ui.map.MapMode.AUTO) {
            // AAOS / Head Unit Fallback: Wenn kein Lichtsensor da ist, Standard-Tagmodus nutzen
            viewModel.updateNightMode(false)
        } else {
            viewModel.updateNightMode(uiState.mapMode == com.example.campernavigator.ui.map.MapMode.NIGHT)
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    val mapView = remember(context) {
        FileLogger.log("MapScreen: Creating MapView instance")
        val view = MapView(context)
        // WICHTIG: onCreate muss nur einmal aufgerufen werden. 
        // Falls die Activity verschoben wird, reicht onStart/onResume.
        view.onCreate(null)
        FileLogger.log("MapScreen: MapView.onCreate finished")
        view
    }

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    val isCameraTracking = uiState.isCameraTracking
    var isSystemMovingCamera by remember { mutableStateOf(false) }
    var styleUpdateTrigger by remember { mutableStateOf(0) }
    
    // Konstanten für Route-Layer
    val FULL_ROUTE_SOURCE_ID = "full-route-source"
    val FULL_ROUTE_LAYER_ID = "full-route-layer"
    val TRAVELED_PATH_SOURCE_ID = "traveled-path-source"
    val TRAVELED_PATH_LAYER_ID = "traveled-path-layer"
    
    // Custom Location Engine für Map Matching
    class SnappedLocationEngine : org.maplibre.android.location.engine.LocationEngine {
        private var lastLoc: android.location.Location? = null
        private val callbacks = mutableSetOf<org.maplibre.android.location.engine.LocationEngineCallback<org.maplibre.android.location.engine.LocationEngineResult>>()

        fun feed(location: android.location.Location, snapped: LatLng, bearing: Float, speed: Float) {
            val safeBearing = GeoUtil.normalizeBearing(bearing)
            val newLoc = Location(location).apply {
                latitude = snapped.latitude
                longitude = snapped.longitude
                this.bearing = safeBearing
                this.speed = speed
            }
            lastLoc = newLoc
            val result = org.maplibre.android.location.engine.LocationEngineResult.create(newLoc)
            callbacks.forEach { it.onSuccess(result) }
        }

        override fun getLastLocation(callback: org.maplibre.android.location.engine.LocationEngineCallback<org.maplibre.android.location.engine.LocationEngineResult>) {
            lastLoc?.let { callback.onSuccess(org.maplibre.android.location.engine.LocationEngineResult.create(it)) }
        }

        override fun requestLocationUpdates(request: org.maplibre.android.location.engine.LocationEngineRequest, callback: org.maplibre.android.location.engine.LocationEngineCallback<org.maplibre.android.location.engine.LocationEngineResult>, looper: android.os.Looper?) {
            callbacks.add(callback)
        }

        override fun requestLocationUpdates(request: org.maplibre.android.location.engine.LocationEngineRequest, pendingIntent: android.app.PendingIntent?) {}
        override fun removeLocationUpdates(callback: org.maplibre.android.location.engine.LocationEngineCallback<org.maplibre.android.location.engine.LocationEngineResult>) { callbacks.remove(callback) }
        override fun removeLocationUpdates(pendingIntent: android.app.PendingIntent?) {}
    }

    // Hilfsfunktion zum Initialisieren der Route-Layer
    fun setupRouteLayers(style: org.maplibre.android.maps.Style) {
        android.util.Log.d("MapScreen", "Initialisiere Route-Layer...")
        
        // Wir platzieren die Route UNTER dem Fahrzeug-Schatten (unterste Ebene des LocationComponents)
        val locationShadowLayer = org.maplibre.android.location.LocationComponentConstants.SHADOW_LAYER
        
        if (style.getSource(FULL_ROUTE_SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(FULL_ROUTE_SOURCE_ID))
            val layer = LineLayer(FULL_ROUTE_LAYER_ID, FULL_ROUTE_SOURCE_ID).withProperties(
                PropertyFactory.lineColor(Color(0xFF8BC34A).toArgb()), // Material Green (matching progress bar)
                PropertyFactory.lineWidth(10f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
            
            // Wenn möglich, unter das Fahrzeug legen
            if (style.getLayer(locationShadowLayer) != null) {
                style.addLayerBelow(layer, locationShadowLayer)
            } else {
                style.addLayer(layer)
            }
        }

        if (style.getSource(TRAVELED_PATH_SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(TRAVELED_PATH_SOURCE_ID))
            val layer = LineLayer(TRAVELED_PATH_LAYER_ID, TRAVELED_PATH_SOURCE_ID).withProperties(
                PropertyFactory.lineColor(Color.LightGray.toArgb()),
                PropertyFactory.lineWidth(10f),
                PropertyFactory.lineCap(Property.LINE_CAP_BUTT),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
            style.addLayerAbove(layer, FULL_ROUTE_LAYER_ID)
        }
        android.util.Log.i("MapScreen", "Route-Layer bereit.")
    }

    val snappedLocationEngine = remember { SnappedLocationEngine() }

    fun buildLocationOptions(topPadding: Int): org.maplibre.android.location.LocationComponentOptions {
        return org.maplibre.android.location.LocationComponentOptions.builder(context)
            .padding(intArrayOf(0, topPadding, 0, 0))
            .accuracyAlpha(0f) // Grauen Kreis ausblenden
            .trackingAnimationDurationMultiplier(1.0f) // FLÜSSIGES GLEITEN AKTIVIEREN
            .maxZoomIconScale(1.2f) // Camper-Look wiederherstellen
            .minZoomIconScale(1.2f)
            .compassAnimationEnabled(false)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun enableLocation(map: MapLibreMap) {
        val style = map.style ?: return
        
        val hasFineLocation = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        if (!hasFineLocation) {
            android.util.Log.w("MapScreen", "Location permission missing.")
            return
        }

        val locationComponent = map.locationComponent
        
        // CCP erst einmal zentriert lassen (kein Padding), bis der erste Fix da ist.
        val topPadding = if (uiState.isInitialZoomPerformed) {
            if (mapView.height > 0) mapView.height / 3 else 0
        } else 0
        
        android.util.Log.d("MapScreen", "Aktiviere LocationComponent (Padding: $topPadding)")

        val options = LocationComponentActivationOptions.builder(context, style)
            .locationEngine(snappedLocationEngine)
            .locationComponentOptions(buildLocationOptions(topPadding))
            .build()
        
        locationComponent.activateLocationComponent(options)
        locationComponent.isLocationComponentEnabled = true
        // Sicherstellen, dass die Kamera-Modi nach Stilwechsel korrekt bleiben
        locationComponent.cameraMode = if (uiState.isInitialZoomPerformed) CameraMode.TRACKING else CameraMode.NONE
        locationComponent.renderMode = RenderMode.GPS

        android.util.Log.i("MapScreen", "LocationComponent bereit: Enabled=${locationComponent.isLocationComponentEnabled}")

        locationComponent.addOnCameraTrackingChangedListener(object : org.maplibre.android.location.OnCameraTrackingChangedListener {
            override fun onCameraTrackingDismissed() {
                if (!isSystemMovingCamera) {
                    viewModel.setCameraTracking(false)
                }
            }

            override fun onCameraTrackingChanged(currentMode: Int) {
                if (!isSystemMovingCamera) {
                    viewModel.setCameraTracking(currentMode != CameraMode.NONE)
                }
            }
        })
    }

    fun updateMapPadding(isOverview: Boolean = false) {
        val map = mapInstance ?: return
        val h = mapView.height
        val w = mapView.width
        if (h <= 0) return
        
        // Im Overview-Modus (Routenvorschau) nutzen wir den vollen Schirm
        if (isOverview) {
            map.setPadding(0, 0, 0, 0)
            try {
                map.locationComponent.applyStyle(buildLocationOptions(0))
            } catch (e: Exception) {}
            return
        }

        // Hintergrund-Modus (Launcher): 1/6 Verschiebung nach rechts (Padding links = 1/3)
        // Dies lässt rechts 1/3 Platz für das Media-Overlay
        when (uiState.navigationUiMode) {

            NavigationUiMode.HOME -> {

                // Map stays physically fullscreen.
                // Only the camera/CCP is shifted to make room
                // for the CarLauncher overlay.
                val leftPadding =
                    (w * 0.18f).roundToInt()

                val topPadding =
                    (h * 0.20f).roundToInt()

                map.setPadding(
                    leftPadding,
                    topPadding,
                    0,
                    0
                )

                try {
                    map.locationComponent.applyStyle(
                        buildLocationOptions(topPadding)
                    )
                } catch (e: Exception) {
                }

                return
            }

            NavigationUiMode.FOREGROUND -> {

                val topPadding = h / 3

                map.setPadding(
                    0,
                    topPadding,
                    0,
                    0
                )

                try {
                    map.locationComponent.applyStyle(
                        buildLocationOptions(topPadding)
                    )
                } catch (e: Exception) {
                }
            }
        }
    }

    fun resetCameraTracking() {
        updateMapPadding()
        val map = mapInstance ?: return
        
        // Safety check: LocationComponent must be activated before calling setCameraMode
        if (!uiState.isMapReady || !map.locationComponent.isLocationComponentActivated) {
            Log.w("MapScreen", "resetCameraTracking: LocationComponent not activated, skipping.")
            return
        }

        // WICHTIG: Alle laufenden Animationen (z.B. Overview-Zoom) abbrechen,
        // bevor wir das Tracking neu setzen, um Konflikte zu vermeiden.
        map.cancelTransitions()
        isSystemMovingCamera = true
        
        // Einheitliche Logik für den Rücksprung zum CCP (mit/ohne Navigation)
        // Wir nutzen setCameraMode für eine perfekte Synchronität zwischen Kamera und Icon.
        val targetMode = if (uiState.isNorthUp) {
            CameraMode.TRACKING 
        } else {
            // Wenn nicht North-Up, dann immer 3D Verfolgung (GPS Mode)
            CameraMode.TRACKING_GPS
        }

        val targetZoom = if (uiState.isNavigating) (uiState.suggestedZoom ?: 16.5) else 17.5
        val targetBearing = if (!uiState.isNorthUp) uiState.currentBearing.toDouble() else 0.0
        val targetTilt = if (!uiState.isNorthUp) (uiState.suggestedTilt ?: 45.0) else 0.0

        try {
            map.locationComponent.setCameraMode(
                targetMode,
                1000, // Dauer des Übergangs
                targetZoom,
                targetBearing,
                targetTilt,
                object : OnLocationCameraTransitionListener {
                    override fun onLocationCameraTransitionFinished(cameraMode: Int) {
                        isSystemMovingCamera = false
                        viewModel.setCameraTracking(true)
                        if (uiState.isNavigating) {
                            viewModel.onNavigationCameraReady()
                        }
                    }
                    override fun onLocationCameraTransitionCanceled(cameraMode: Int) {
                        isSystemMovingCamera = false
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("MapScreen", "Error in setCameraMode: ${e.message}")
            isSystemMovingCamera = false
        }
    }

    // Display-Logik: Keep Screen On & Energiespar-Dimmen
    LaunchedEffect(uiState.isNavigating, uiState.isPowerSaveModeEnabled, uiState.distanceToNextInstruction, lastInteractionTime) {
        val activity = context as? Activity ?: return@LaunchedEffect
        val window = activity.window

        if (uiState.isNavigating) {
            // 1. Bildschirm dauerhaft an lassen während der Navigation
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // 2. Dimmen bei Energiesparmodus
            if (uiState.isPowerSaveModeEnabled) {
                val dist = uiState.distanceToNextInstruction ?: 0.0
                val isRecentlyInteracted = System.currentTimeMillis() - lastInteractionTime < 5000 // 5s Puffer
                
                if (dist > 500.0 && !isRecentlyInteracted) {
                    // Weit weg vom nächsten Manöver: Abdunkeln (10% Brightness)
                    val lp = window.attributes
                    lp.screenBrightness = 0.1f
                    window.attributes = lp
                } else {
                    // Nah dran oder Interaktion: Hell machen (System-Default)
                    val lp = window.attributes
                    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    window.attributes = lp
                }
            } else {
                // Energiesparmodus aus: Immer hell
                val lp = window.attributes
                if (lp.screenBrightness != WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
                    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    window.attributes = lp
                }
            }
        } else {
            // Navigation beendet: Flags und Brightness zurücksetzen
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val lp = window.attributes
            if (lp.screenBrightness != WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = lp
            }
        }
    }

    // Kamera-Zustände für Übergänge
    var wasNavigating by remember { mutableStateOf(false) }
    var lastZoomedRoute by remember { mutableStateOf<com.example.campernavigator.service.Route?>(null) }
    var isWaitingForFirstFixAfterStop by remember { mutableStateOf(false) }

    LaunchedEffect(isWaitingForFirstFixAfterStop, uiState.snappedLocation) {
        if (isWaitingForFirstFixAfterStop && uiState.snappedLocation != null) {
            viewModel.setCameraTracking(true)
            resetCameraTracking()
            isWaitingForFirstFixAfterStop = false
        }
    }

    Box(modifier = modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(pass = PointerEventPass.Initial)
                lastInteractionTime = System.currentTimeMillis()
            }
        }
    ) {
        AndroidView(
            factory = {
                mapView.apply {
                    // Automatisches Padding-Update bei Layout-Änderungen
                    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                         updateMapPadding(isOverview = uiState.currentRoute != null && !uiState.isNavigating)
                    }

                    getMapAsync { map ->
                        mapInstance = map
                        
                        // Zoom-Limits festlegen
                        map.setMaxZoomPreference(19.0) // Verhindert Zoom näher als ca. 25m Scale (vorher 20.0)
                        map.setMinZoomPreference(2.0)  // Verhindert zu weites Auszoomen
                        
                        fun loadStyle() {
                            // Wenn wir hasLocalTiles haben, lassen wir die Initialisierung 
                            // durch den LaunchedEffect (unten) erledigen, um Dopplungen zu vermeiden.
                            if (uiState.hasLocalTiles && uiState.activeRegionId != null) {
                                android.util.Log.d("MapScreen", "Initialer Load wird an LaunchedEffect delegiert.")
                                return
                            }

                            val url = if (uiState.isNightMode) "https://tiles.openfreemap.org/styles/fiord" 
                                     else "https://tiles.openfreemap.org/styles/liberty"
                                     
                            android.util.Log.d("MapScreen", "Setze Initial-Stil (Online): $url")
                            map.setStyle(org.maplibre.android.maps.Style.Builder().fromUri(url)) { style ->
                                FileLogger.log("MapScreen: Style loaded, Map is ready")
                                updateMapPadding()
                                enableLocation(map)
                                setupRouteLayers(style)
                                styleUpdateTrigger++
                                viewModel.setMapReady(true)
                            }
                            
                            // Überwachung: Wenn nach 10 Sek. kein Stil da ist, Log schreiben
                            Handler(Looper.getMainLooper()).postDelayed({
                                if (map.style == null) {
                                    FileLogger.log("MapScreen: WARNING - Style still not loaded after 10s. Internet issues?", "ERROR")
                                }
                            }, 10000)
                        }

                        loadStyle()

                        map.addOnMapClickListener { point ->
                            // Klick nur verarbeiten, wenn Karte bereit ist UND wir reale Koordinaten haben
                            if (uiState.isMapReady && point.latitude > -1.0) {
                                if (!uiState.isNavigating) {
                                    val start = uiState.snappedLocation ?: uiState.rawLocation ?: LatLng(48.13, 11.57)
                                    viewModel.calculateRoute(start, point)
                                }
                            }
                            true
                        }

                        map.addOnMapLongClickListener { point ->
                            showMapClickDialog = point
                            true
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { }
        )

        // --- NEW AUTOMOTIVE LAYOUT ---

        if (uiState.isLauncherVisible) {
            // Top Status Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .align(Alignment.TopCenter),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    // Ankunftszeit (ETA) während der Navigation
                    if (uiState.isNavigating && uiState.remainingTime != null) {
                        val arrivalTime = currentTime + uiState.remainingTime!!
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = null,
                                tint = Color(0xFF8BC34A),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = formatArrivalTime(arrivalTime),
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(32.dp))
                    }

                    if (uiState.isOffline) {
                        Icon(
                            imageVector = Icons.Default.SignalCellularAlt,
                            contentDescription = "Offline",
                            tint = Color(0xFFE57373), // Red
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "OFFLINE",
                            color = Color(0xFFE57373),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(16.dp))
                    }

                    Icon(Icons.Default.Wifi, null, tint = if (uiState.isOffline) Color.White.copy(alpha = 0.3f) else Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))

                    // Satellite Count
                    if (uiState.satelliteCount != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.SatelliteAlt,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                "${uiState.satelliteCount}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }

                    Icon(Icons.Default.SignalCellularAlt, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("ARABELLA", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(16.dp))
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(currentTime)),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // --- LEFT ROUTE PROGRESS BAR ---
            if (uiState.isNavigating && uiState.currentRoute != null) {
                val totalDist = uiState.currentRoute?.distance ?: 1.0
                val remainDist = uiState.remainingDistance ?: totalDist
                val progress = (1.0 - (remainDist / totalDist)).coerceIn(0.0, 1.0).toFloat()

                Surface(
                    modifier = Modifier
                        .padding(start = 8.dp, top = 56.dp, bottom = 16.dp) // Von ganz oben bis zur Unterkante Menü
                        .width(32.dp)
                        .align(Alignment.TopStart),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.fillMaxHeight().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Flag,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            // Background Track (Gray)
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(4.dp)
                                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                            )

                            // Remaining Route (Green - decreases as we drive)
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .fillMaxHeight(1f - progress)
                                    .align(Alignment.TopCenter)
                                    .background(Color(0xFF8BC34A), RoundedCornerShape(2.dp))
                            )

                            // Small CCP Indicator (Moving Arrow)
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight(progress)
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Navigation,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .offset(y = (-8).dp) // Center the arrow on the progress point
                                )
                            }
                        }

                        Icon(
                            Icons.Default.MyLocation,
                            null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Left Panel (Menu + Search or Navigation Info)
            Surface(
                modifier = Modifier
                    .padding(start = 48.dp, bottom = 16.dp)
                    .width(if (uiState.isNavigating) 240.dp else 280.dp)
                    .align(Alignment.BottomStart),
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.85f),
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    // Icon Grid (Always available when expanded)
                    AnimatedVisibility(visible = isPanelExpanded) {
                        Column {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier.heightIn(max = 200.dp),
                                contentPadding = PaddingValues(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    IconButton(onClick = {
                                        uiState.homeAddress?.let { home ->
                                            val start = uiState.snappedLocation ?: uiState.rawLocation ?: LatLng(52.5200, 13.4050)
                                            viewModel.calculateRoute(start = start, end = home.location)
                                            isPanelExpanded = false
                                        } ?: run { isMapManagementOpen = true }
                                    }) {
                                        Icon(
                                            Icons.Default.Home,
                                            "Home",
                                            tint = if (uiState.homeAddress != null) Color.White else Color.White.copy(alpha = 0.3f)
                                        )
                                    }
                                }
                                item {
                                    IconButton(onClick = {
                                        isCampingSubmenuOpen = true
                                        isPanelExpanded = false
                                    }) {
                                        Icon(
                                            Icons.Default.Terrain,
                                            "Campingplätze",
                                            tint = Color.White
                                        )
                                    }
                                }
                                item {
                                    IconButton(onClick = {
                                        if (uiState.isNavigating) {
                                            viewModel.stopNavigation()
                                        } else {
                                            isFavoritesOpen = true
                                        }
                                    }) {
                                        Icon(
                                            Icons.Default.Flag,
                                            contentDescription = if (uiState.isNavigating) "Navigation beenden" else "Ziele",
                                            tint = if (uiState.isNavigating) MaterialTheme.colorScheme.error else Color.White
                                        )
                                    }
                                }
                                item { IconButton(onClick = { }) { Icon(Icons.Default.LocalParking, "Parking", tint = Color.White) } }
                                item { IconButton(onClick = { }) { Icon(Icons.Default.EvStation, "Charging", tint = Color.White) } }
                                item { IconButton(onClick = { isMapManagementOpen = true }) { Icon(Icons.Default.Settings, "Settings", tint = Color.White) } }
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }

                    // Bottom Row (Stats or Search)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.isNavigating) {
                            // --- NAVIGATION INFO ---
                            // Stop Button (Gray / Matching design)
                            Surface(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clickable { viewModel.stopNavigation() },
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.15f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Stop",
                                        tint = Color(0xFFE57373), // Soft Red
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.width(16.dp))

                            // Remaining Stats
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Flag, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = formatDuration(uiState.remainingTime ?: 0L),
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "${String.format("%.0f", (uiState.remainingDistance ?: 0.0) / 1000.0)} km",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        } else {
                            // --- SEARCH BAR ---
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .clickable { isSearchOverlayVisible = true }
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.6f))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = if (searchQuery.isEmpty()) "Hier suchen" else searchQuery,
                                    color = if (searchQuery.isEmpty()) Color.Gray else Color.White,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )
                            }
                        }

                        // Expand/Collapse Chevron (Always available)
                        IconButton(onClick = { isPanelExpanded = !isPanelExpanded }) {
                            Icon(
                                imageVector = if (isPanelExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            // Bottom Street Name Bar
            if (!uiState.currentStreetName.isNullOrBlank()) {
                val panelWidth = if (uiState.isNavigating) 240.dp else 280.dp
                Surface(
                    modifier = Modifier
                        .padding(start = 48.dp + panelWidth + 16.dp, bottom = 16.dp)
                        .align(Alignment.BottomStart),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Text(
                        uiState.currentStreetName!!,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // --- SPEED INDICATOR (Top Left) ---
            Column(
                modifier = Modifier
                    .padding(top = 56.dp, start = 48.dp) // Ein Stück nach rechts gerückt (Beginn Menü)
                    .align(Alignment.TopStart)
            ) {
                val speedValue = when {
                    uiState.currentSpeed < 0.277f -> 0 // Stillstand-Schwelle (~1 km/h)
                    uiState.isMphEnabled -> (uiState.currentSpeed * 2.23694 + 1.86).toInt() // +3 km/h (~1.86 mph)
                    else -> (uiState.currentSpeed * 3.6 + 3.0).toInt() // +3 km/h Aufschlag
                }
                val textColor = if (uiState.isNightMode) Color.White else Color.Black
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$speedValue",
                        color = textColor,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (uiState.isMphEnabled) "mph" else "km/h",
                        color = textColor.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }
        }if (uiState.navigationUiMode == NavigationUiMode.FOREGROUND) {
            // Top Status Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .align(Alignment.TopCenter),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    // Ankunftszeit (ETA) während der Navigation
                    if (uiState.isNavigating && uiState.remainingTime != null) {
                        val arrivalTime = currentTime + uiState.remainingTime!!
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = null,
                                tint = Color(0xFF8BC34A),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = formatArrivalTime(arrivalTime),
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(32.dp))
                    }

                    if (uiState.isOffline) {
                        Icon(
                            imageVector = Icons.Default.SignalCellularAlt,
                            contentDescription = "Offline",
                            tint = Color(0xFFE57373), // Red
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "OFFLINE",
                            color = Color(0xFFE57373),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(16.dp))
                    }

                    Icon(Icons.Default.Wifi, null, tint = if (uiState.isOffline) Color.White.copy(alpha = 0.3f) else Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    
                    // Satellite Count
                    if (uiState.satelliteCount != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.SatelliteAlt, 
                                null, 
                                tint = Color.White, 
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                "${uiState.satelliteCount}", 
                                color = Color.White, 
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }

                    Icon(Icons.Default.SignalCellularAlt, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("ARABELLA", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(16.dp))
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(currentTime)),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // --- LEFT ROUTE PROGRESS BAR ---
            if (uiState.isNavigating && uiState.currentRoute != null) {
                val totalDist = uiState.currentRoute?.distance ?: 1.0
                val remainDist = uiState.remainingDistance ?: totalDist
                val progress = (1.0 - (remainDist / totalDist)).coerceIn(0.0, 1.0).toFloat()
                
                Surface(
                    modifier = Modifier
                        .padding(start = 8.dp, top = 56.dp, bottom = 16.dp) // Von ganz oben bis zur Unterkante Menü
                        .width(32.dp)
                        .align(Alignment.TopStart),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.fillMaxHeight().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Flag, 
                            null, 
                            tint = Color.White, 
                            modifier = Modifier.size(20.dp)
                        )
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            // Background Track (Gray)
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(4.dp)
                                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                            )
                            
                            // Remaining Route (Green - decreases as we drive)
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .fillMaxHeight(1f - progress)
                                    .align(Alignment.TopCenter)
                                    .background(Color(0xFF8BC34A), RoundedCornerShape(2.dp))
                            )
                            
                            // Small CCP Indicator (Moving Arrow)
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight(progress)
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Navigation,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .offset(y = (-8).dp) // Center the arrow on the progress point
                                )
                            }
                        }
                        
                        Icon(
                            Icons.Default.MyLocation, 
                            null, 
                            tint = Color.White.copy(alpha = 0.5f), 
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Left Panel (Menu + Search or Navigation Info)
            Surface(
                modifier = Modifier
                    .padding(start = 48.dp, bottom = 16.dp)
                    .width(if (uiState.isNavigating) 240.dp else 280.dp)
                    .align(Alignment.BottomStart),
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.85f),
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    // Icon Grid (Always available when expanded)
                    AnimatedVisibility(visible = isPanelExpanded) {
                        Column {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier.heightIn(max = 200.dp),
                                contentPadding = PaddingValues(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item { 
                                    IconButton(onClick = { 
                                        uiState.homeAddress?.let { home ->
                                            val start = uiState.snappedLocation ?: uiState.rawLocation ?: LatLng(52.5200, 13.4050)
                                            viewModel.calculateRoute(start = start, end = home.location)
                                            isPanelExpanded = false
                                        } ?: run { isMapManagementOpen = true }
                                    }) { 
                                        Icon(
                                            Icons.Default.Home, 
                                            "Home", 
                                            tint = if (uiState.homeAddress != null) Color.White else Color.White.copy(alpha = 0.3f)
                                        ) 
                                    } 
                                }
                                item { 
                                    IconButton(onClick = { 
                                        isCampingSubmenuOpen = true
                                        isPanelExpanded = false
                                    }) { 
                                        Icon(
                                            Icons.Default.Terrain, 
                                            "Campingplätze", 
                                            tint = Color.White
                                        ) 
                                    } 
                                }
                                item { 
                                    IconButton(onClick = { 
                                        if (uiState.isNavigating) {
                                            viewModel.stopNavigation()
                                        } else {
                                            isFavoritesOpen = true
                                        }
                                    }) { 
                                        Icon(
                                            Icons.Default.Flag, 
                                            contentDescription = if (uiState.isNavigating) "Navigation beenden" else "Ziele", 
                                            tint = if (uiState.isNavigating) MaterialTheme.colorScheme.error else Color.White
                                        ) 
                                    } 
                                }
                                item { IconButton(onClick = { }) { Icon(Icons.Default.LocalParking, "Parking", tint = Color.White) } }
                                item { IconButton(onClick = { }) { Icon(Icons.Default.EvStation, "Charging", tint = Color.White) } }
                                item { IconButton(onClick = { isMapManagementOpen = true }) { Icon(Icons.Default.Settings, "Settings", tint = Color.White) } }
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }

                    // Bottom Row (Stats or Search)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.isNavigating) {
                            // --- NAVIGATION INFO ---
                            // Stop Button (Gray / Matching design)
                            Surface(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clickable { viewModel.stopNavigation() },
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.15f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Stop",
                                        tint = Color(0xFFE57373), // Soft Red
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            
                            Spacer(Modifier.width(16.dp))
                            
                            // Remaining Stats
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Flag, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = formatDuration(uiState.remainingTime ?: 0L),
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "${String.format("%.0f", (uiState.remainingDistance ?: 0.0) / 1000.0)} km",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        } else {
                            // --- SEARCH BAR ---
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .clickable { isSearchOverlayVisible = true }
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.6f))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = if (searchQuery.isEmpty()) "Hier suchen" else searchQuery,
                                    color = if (searchQuery.isEmpty()) Color.Gray else Color.White,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )
                            }
                        }

                        // Expand/Collapse Chevron (Always available)
                        IconButton(onClick = { isPanelExpanded = !isPanelExpanded }) {
                            Icon(
                                imageVector = if (isPanelExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            // Bottom Street Name Bar
            if (!uiState.currentStreetName.isNullOrBlank()) {
                val panelWidth = if (uiState.isNavigating) 240.dp else 280.dp
                Surface(
                    modifier = Modifier
                        .padding(start = 48.dp + panelWidth + 16.dp, bottom = 16.dp)
                        .align(Alignment.BottomStart),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Text(
                        uiState.currentStreetName!!,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // --- SPEED INDICATOR (Top Left) ---
            Column(
                modifier = Modifier
                    .padding(top = 56.dp, start = 48.dp) // Ein Stück nach rechts gerückt (Beginn Menü)
                    .align(Alignment.TopStart)
            ) {
                val speedValue = when {
                    uiState.currentSpeed < 0.277f -> 0 // Stillstand-Schwelle (~1 km/h)
                    uiState.isMphEnabled -> (uiState.currentSpeed * 2.23694 + 1.86).toInt() // +3 km/h (~1.86 mph)
                    else -> (uiState.currentSpeed * 3.6 + 3.0).toInt() // +3 km/h Aufschlag
                }
                val textColor = if (uiState.isNightMode) Color.White else Color.Black
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$speedValue",
                        color = textColor,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (uiState.isMphEnabled) "mph" else "km/h",
                        color = textColor.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }
        }

        // Right Action Column
        if (uiState.navigationUiMode == NavigationUiMode.FOREGROUND) {
            Column(
                modifier = Modifier
                    .padding(bottom = 60.dp, end = 16.dp)
                    .align(Alignment.BottomEnd),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
            // Speed Limit Sign
            Box(contentAlignment = Alignment.BottomEnd) {
                // Next Speed Limit Preview (Shadow)
                uiState.nextSpeedLimit?.let { nextLimit ->
                    val displayedNextLimit = if (uiState.isMphEnabled) {
                        (nextLimit * 0.621371).toInt()
                    } else {
                        nextLimit
                    }
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.offset(x = (-38).dp, y = (-32).dp)
                    ) {
                        // Distance to next speed limit (Now attached to the preview sign)
                        uiState.distanceToNextSpeedLimit?.let { dist ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.Black.copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 2.dp)
                            ) {
                                Text(
                                    text = "${dist.toInt()} m",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = Color.White.copy(alpha = 0.9f),
                            border = androidx.compose.foundation.BorderStroke(6.dp, Color.Gray)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$displayedNextLimit",
                                    color = Color.Gray,
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }

                // Current Speed Limit Sign
                uiState.currentSpeedLimit?.let { limit ->
                    val displayedLimit = if (uiState.isMphEnabled) {
                        (limit * 0.621371).toInt()
                    } else {
                        limit
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (uiState.isAdvisorySpeed) {
                            // Richtgeschwindigkeit (Blaues Schild, quadratisch)
                            Surface(
                                modifier = Modifier.size(64.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF003399), // Autobahn-Blau
                                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "$displayedLimit",
                                        color = Color.White,
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            // Normales Speed Limit (Weißes Schild, Roter Rand, Rund)
                            Surface(
                                modifier = Modifier.size(64.dp),
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = Color.White,
                                border = androidx.compose.foundation.BorderStroke(6.dp, Color.Red),
                                tonalElevation = 4.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "$displayedLimit",
                                        color = Color.Black,
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Location / Re-center Button
            AnimatedVisibility(
                visible = uiState.isInitialZoomPerformed && !isCameraTracking,
                enter = expandHorizontally(),
                exit = shrinkHorizontally()
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (!isCameraTracking && uiState.isNavigating) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        Color.Black.copy(alpha = 0.75f),
                    tonalElevation = 6.dp
                ) {
                    IconButton(
                        onClick = { resetCameraTracking() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.MyLocation, 
                            "My Location", 
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    // Elevation & Scale Card
        Row(
            modifier = Modifier
                .padding(bottom = 16.dp, end = 16.dp)
                .align(Alignment.BottomEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Orientation Toggle
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.7f)
            ) {
                IconButton(
                    onClick = { viewModel.toggleMapOrientation() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (uiState.isNorthUp) Icons.Default.Explore else Icons.Default.Navigation,
                        contentDescription = "Ansicht",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Altitude
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.7f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Terrain, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${uiState.currentAltitude?.toInt() ?: "---"} m", 
                        color = Color.White, 
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Scale Bar
            mapInstance?.let { map ->
                ScaleBar(map = map)
            }
        }

        // Side Menu (Overlay) - REMOVED (Replaced by Full Screen Settings)

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        val message = uiState.loadingMessage ?: "Route wird berechnet..."
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Route Card
        AnimatedVisibility(
            visible = uiState.currentRoute != null && !uiState.isNavigating, 
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 56.dp, end = 16.dp)
        ) {
            uiState.currentRoute?.let { route ->
                RouteSummaryCard(
                    route = route, 
                    onClear = { viewModel.clearRoute() },
                    onStart = { viewModel.startNavigation() }
                )
            }
        }
        
        // Navigation Overlay (Maneuver Box Top Right)
        if (uiState.isNavigating && uiState.currentInstruction != null) {
            Surface(
                modifier = Modifier
                    .padding(top = 56.dp, end = 16.dp)
                    .width(340.dp) // Slightly wider for better text flow
                    .align(Alignment.TopEnd),
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.85f), // Darker for better contrast
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), // Thin border
                tonalElevation = 12.dp
            ) {
                Column {
                    // Distance Progress Bar (Top of the card)
                    val rawDist = uiState.distanceToNextInstruction ?: 0.0
                    // Typical "Near" distance for maneuvers is 500m
                    val maneuverProgress = (1.0 - (rawDist / 500.0)).coerceIn(0.0, 1.0).toFloat()
                    
                    if (rawDist < 500.0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(Color.White.copy(alpha = 0.1f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(maneuverProgress)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }

                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Maneuver Icon
                            val maneuverIcon = getManeuverIcon(uiState.currentInstructionSign)
                            
                            Box(contentAlignment = Alignment.Center) {
                                Surface(
                                    modifier = Modifier.size(60.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = maneuverIcon,
                                            contentDescription = null,
                                            tint = Color.White, // Always white for maximum contrast
                                            modifier = Modifier.size(42.dp)
                                        )
                                    }
                                }
                                
                                // Exit Number Badge for Roundabouts
                                if ((uiState.currentInstructionSign == 6 || uiState.currentInstructionSign == 7) && 
                                    uiState.currentRoundaboutExit != null) {
                                    Surface(
                                        modifier = Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 6.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        border = BorderStroke(2.dp, Color.Black)
                                    ) {
                                        Text(
                                            text = "${uiState.currentRoundaboutExit}",
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                            
                            Spacer(Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                val distToManeuver = (Math.round(rawDist / 10.0) * 10).toDouble()
                                
                                val isMph = uiState.isMphEnabled
                                val distStr = if (isMph) {
                                    if (distToManeuver >= 1609.34) "${String.format("%.1f", distToManeuver / 1609.34)} mi"
                                    else "${(distToManeuver * 1.09361).toInt()} yd"
                                } else {
                                    if (distToManeuver >= 1000.0) "${String.format("%.1f", distToManeuver / 1000.0)} km"
                                    else "${distToManeuver.toInt()} m"
                                }

                                val primaryText = if (!uiState.currentManeuverStreet.isNullOrBlank()) {
                                    uiState.currentManeuverStreet!!
                                } else {
                                    when(uiState.currentInstructionSign) {
                                        1, 2, 3 -> "Rechts abbiegen"
                                        -1, -2, -3 -> "Links abbiegen"
                                        4, 5 -> "Ankunft"
                                        6, 7 -> "Kreisverkehr"
                                        8, 9, 10 -> "Wenden"
                                        else -> "Geradeaus"
                                    }
                                }

                                Text(
                                    text = primaryText,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                
                                Text(
                                    text = distStr,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        // Placeholder for Lane Assist
                        // (Would show icons here if lane data existed in uiState)
                        
                        // Upcoming Maneuver Preview (Look-ahead)
                        if (uiState.upcomingInstructionSign != null) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp), 
                                color = Color.White.copy(alpha = 0.15f)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Dann: ", 
                                    style = MaterialTheme.typography.labelMedium, 
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                                Icon(
                                    imageVector = getManeuverIcon(uiState.upcomingInstructionSign),
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = (uiState.upcomingManeuverStreet ?: "Weiterfahren") + 
                                           if ((uiState.upcomingInstructionSign == 6 || uiState.upcomingInstructionSign == 7) && uiState.upcomingRoundaboutExit != null) " (Ausfahrt ${uiState.upcomingRoundaboutExit})" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Error Dialog
        uiState.showErrorDialog?.let { message ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissErrorDialog() },
                title = { Text("Hinweis") },
                text = { Text(message) },
                confirmButton = {
                    Button(onClick = { viewModel.dismissErrorDialog() }) {
                        Text("OK")
                    }
                }
            )
        }

        // Resume Navigation Dialog
        val isSplashScreenGone = (uiState.isLocationDetermined && uiState.isMapReady && uiState.isInitialZoomPerformed) || uiState.forceExitSplash
        if (uiState.showResumeNavigationDialog && isSplashScreenGone) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissResumeDialog() },
                title = { Text("Zielführung fortsetzen?") },
                text = { 
                    Text("Möchtest du die Navigation zu '${uiState.savedNavigationDestination?.name ?: "deinem letzten Ziel"}' fortsetzen?")
                },
                confirmButton = {
                    Button(onClick = { viewModel.resumeNavigation() }) {
                        Text("Fortsetzen")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissResumeDialog() }) {
                        Text("Abbrechen")
                    }
                }
            )
        }

        // --- MAP CLICK DIALOG (Add Waypoint / Start New) ---
        showMapClickDialog?.let { point ->
            Dialog(onDismissRequest = { showMapClickDialog = null }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.9f),
                    contentColor = Color.White,
                    tonalElevation = 8.dp,
                    modifier = Modifier.width(320.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Zieloptionen",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Was möchtest du mit diesem Standort tun?",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Buttons
                        Button(
                            onClick = {
                                viewModel.addWaypoint(point)
                                showMapClickDialog = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Als Zwischenziel hinzufügen")
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Button(
                            onClick = {
                                val start = uiState.snappedLocation ?: uiState.rawLocation ?: LatLng(52.5200, 13.4050)
                                viewModel.calculateRoute(start, point)
                                showMapClickDialog = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text("Zielführung hierhin starten")
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        TextButton(
                            onClick = { showMapClickDialog = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Abbrechen", color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }

        // --- CAMPING SUBMENU ---
        if (isCampingSubmenuOpen) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { 
                            isCampingSubmenuOpen = false
                            viewModel.clearCampingResults()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Campingplatz suchen", style = MaterialTheme.typography.headlineMedium)
                    }

                    if (uiState.isNavigating) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.searchCamping(query = "", alongRoute = true) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Entlang Route")
                            }
                            Button(
                                onClick = { viewModel.searchCamping(query = "", location = uiState.destinationPoint) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Am Ziel")
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = campingSearchQuery,
                            onValueChange = { 
                                campingSearchQuery = it
                                viewModel.searchCamping(query = it)
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            placeholder = { Text("Ort eingeben für Campingplätze...") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            singleLine = true
                        )
                    }

                    if (uiState.isCampingSearchLoading) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(uiState.campingResults) { site ->
                                ListItem(
                                    headlineContent = { 
                                        Column {
                                            Text(site.name)
                                            if (site.campingFeatures.isNotEmpty()) {
                                                Row(
                                                    modifier = Modifier.padding(top = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    site.campingFeatures.forEach { feature ->
                                                        val icon = when(feature) {
                                                            CampingFeature.WATER -> Icons.Default.WaterDrop
                                                            CampingFeature.ELECTRICITY -> Icons.Default.Bolt
                                                            CampingFeature.WASTE -> Icons.Default.Delete
                                                            CampingFeature.WIFI -> Icons.Default.Wifi
                                                            CampingFeature.SHOWER -> Icons.Default.Shower
                                                            CampingFeature.PETS -> Icons.Default.Pets
                                                            CampingFeature.GREY_WATER -> Icons.Default.Waves
                                                            CampingFeature.BLACK_WATER -> Icons.Default.Wc
                                                        }
                                                        Icon(
                                                            imageVector = icon,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.secondary
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    supportingContent = { Text(site.description) },
                                    leadingContent = { Icon(Icons.Default.Terrain, null, tint = MaterialTheme.colorScheme.primary) },
                                    modifier = Modifier.clickable {
                                        viewModel.onCampingSiteSelected(site)
                                        isCampingSubmenuOpen = false
                                    }
                                )
                                HorizontalDivider()
                            }
                            if (uiState.campingResults.isEmpty()) {
                                item {
                                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            if (uiState.isNavigating) "Wähle eine Option oben aus" else "Suche nach einem Ort",
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- CAMPING SELECTION DIALOG (Waypoint vs Destination) ---
        uiState.pendingCampingSite?.let { site ->
            AlertDialog(
                onDismissRequest = { viewModel.cancelCampingSelection() },
                title = { Text("Campingplatz hinzufügen") },
                text = { Text("Möchtest du '${site.name}' als Zwischenziel hinzufügen oder als neues Hauptziel festlegen?") },
                confirmButton = {
                    Button(onClick = { viewModel.addCampingAsWaypoint(site) }) {
                        Text("Zwischenziel")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.setCampingAsNewDestination(site) }) {
                        Text("Neues Ziel")
                    }
                }
            )
        }

        // --- FULL SCREEN FAVORITES & HISTORY OVERLAY ---
        if (isFavoritesOpen) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { isFavoritesOpen = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Meine Ziele", style = MaterialTheme.typography.headlineMedium)
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        if (uiState.favorites.isNotEmpty()) {
                            item {
                                Text("Favoriten", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.primary)
                            }
                            items(uiState.favorites) { fav ->
                                DestinationItem(
                                    result = fav,
                                    isFavorite = true,
                                    onSelect = {
                                        isFavoritesOpen = false
                                        viewModel.selectSearchResult(fav)
                                        mapInstance?.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(fav.location, 19.0))
                                        val start = uiState.snappedLocation ?: uiState.rawLocation ?: LatLng(52.5200, 13.4050)
                                        viewModel.calculateRoute(start = start, end = fav.location)
                                    },
                                    onToggleFavorite = { viewModel.toggleFavorite(fav) }
                                )
                                HorizontalDivider()
                            }
                        }

                        if (uiState.recentDestinations.isNotEmpty()) {
                            item {
                                Text("Letzte Ziele", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.primary)
                            }
                            items(uiState.recentDestinations) { recent ->
                                DestinationItem(
                                    result = recent,
                                    isFavorite = uiState.favorites.any { it.name == recent.name },
                                    onSelect = {
                                        isFavoritesOpen = false
                                        viewModel.selectSearchResult(recent)
                                        mapInstance?.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(recent.location, 19.0))
                                        val start = uiState.snappedLocation ?: uiState.rawLocation ?: LatLng(52.5200, 13.4050)
                                        viewModel.calculateRoute(start = start, end = recent.location)
                                    },
                                    onToggleFavorite = { viewModel.toggleFavorite(recent) }
                                )
                                HorizontalDivider()
                            }
                        }
                        
                        if (uiState.favorites.isEmpty() && uiState.recentDestinations.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Noch keine Favoriten oder Ziele vorhanden.", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- FULL SCREEN MAP MANAGEMENT OVERLAY ---
        if (isMapManagementOpen) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    // Header with Back Button
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { isMapManagementOpen = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Einstellungen & Karten", 
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                    
                    HorizontalDivider()
                    
                    LazyColumn(modifier = Modifier.weight(1f).padding(top = 16.dp)) {
                        item {
                            MenuSection(title = "Allgemein", icon = Icons.Default.Settings) {
                                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                                    ListItem(
                                        headlineContent = { Text("Heimatadresse") },
                                        supportingContent = { Text(uiState.homeAddress?.name ?: "Nicht festgelegt") },
                                        leadingContent = { Icon(Icons.Default.Home, null) },
                                        trailingContent = {
                                            TextButton(onClick = { 
                                                isSettingHome = true
                                                isSearchOverlayVisible = true 
                                            }) {
                                                Text(if (uiState.homeAddress == null) "Einrichten" else "Ändern")
                                            }
                                        }
                                    )
                                    ListItem(
                                        headlineContent = { Text("Display-Energiesparmodus") },
                                        supportingContent = { Text("Bildschirm bei gerader Strecke automatisch abdunkeln") },
                                        leadingContent = { Icon(Icons.Default.Bolt, null) },
                                        trailingContent = {
                                            Switch(
                                                checked = uiState.isPowerSaveModeEnabled,
                                                onCheckedChange = { viewModel.togglePowerSaveMode() }
                                            )
                                        }
                                    )
                                    ListItem(
                                        headlineContent = { Text("Demo Modus") },
                                        supportingContent = { Text("Virtuelle Fahrt entlang der Route simulieren") },
                                        leadingContent = { Icon(Icons.Default.PlayArrow, null) },
                                        trailingContent = {
                                            Switch(
                                                checked = uiState.isDemoMode,
                                                onCheckedChange = { viewModel.toggleDemoMode() }
                                            )
                                        }
                                    )

                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.1f))
                                    Text("Routen-Optimierung", style = MaterialTheme.typography.labelMedium, color = Color.Gray, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(
                                            RoutingMode.FASTEST to "Schnellste",
                                            RoutingMode.SHORTEST to "Kürzeste"
                                        ).forEach { (mode, label) ->
                                            val isSelected = uiState.routingMode == mode
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(40.dp)
                                                    .clickable { viewModel.setRoutingMode(mode) },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.3f),
                                                border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(label, color = if (isSelected) Color.White else Color.Gray, style = MaterialTheme.typography.labelLarge)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }

                        item {
                            MenuSection(title = "Karten-Optionen", icon = Icons.Default.Layers) {
                                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                                    ListItem(
                                        headlineContent = { Text("Interessenpunkte (POIs) anzeigen") },
                                        leadingContent = { Icon(Icons.Default.Place, null) },
                                        trailingContent = {
                                            Switch(
                                                checked = uiState.showPois,
                                                onCheckedChange = { viewModel.togglePois() }
                                            )
                                        }
                                    )
                                    ListItem(
                                        headlineContent = { Text("3D-Gebäude anzeigen") },
                                        leadingContent = { Icon(Icons.Default.Domain, null) },
                                        trailingContent = {
                                            Switch(
                                                checked = uiState.showBuildings,
                                                onCheckedChange = { viewModel.toggleBuildings() }
                                            )
                                        }
                                    )
                                    ListItem(
                                        headlineContent = { Text("Automatischer Zoom (Navigation)") },
                                        leadingContent = { Icon(Icons.Default.Navigation, null) },
                                        trailingContent = {
                                            Switch(
                                                checked = uiState.isAutoZoomEnabled,
                                                onCheckedChange = { viewModel.toggleAutoZoom() }
                                            )
                                        }
                                    )
                                    ListItem(
                                        headlineContent = { Text("Einheit Geschwindigkeit") },
                                        supportingContent = { Text(if (uiState.isMphEnabled) "Meilen pro Stunde (mph)" else "Kilometer pro Stunde (km/h)") },
                                        leadingContent = { Icon(Icons.Default.SignalCellularAlt, null) },
                                        trailingContent = {
                                            Switch(
                                                checked = uiState.isMphEnabled,
                                                onCheckedChange = { viewModel.toggleSpeedUnit() }
                                            )
                                        }
                                    )

                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.1f))
                                    Text("Kartendesign", style = MaterialTheme.typography.labelMedium, color = Color.Gray, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(
                                            com.example.campernavigator.ui.map.MapMode.AUTO to "Auto",
                                            com.example.campernavigator.ui.map.MapMode.DAY to "Tag",
                                            com.example.campernavigator.ui.map.MapMode.NIGHT to "Nacht"
                                        ).forEach { (mode, label) ->
                                            Button(
                                                onClick = { viewModel.setMapMode(mode) },
                                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                                    containerColor = if (uiState.mapMode == mode) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f)
                                                ),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(label)
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }

                        if (uiState.availableCameras.isNotEmpty()) {
                            item {
                                MenuSection(title = "Kamerasystem", icon = Icons.Default.Videocam) {
                                    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                                        Text(
                                            "Wähle eine Kamera für die Rückfahr- oder Seitenansicht:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            uiState.availableCameras.forEach { cameraId ->
                                                val isSelected = uiState.selectedCameraId == cameraId
                                                Button(
                                                    onClick = { viewModel.selectCamera(cameraId) },
                                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f)
                                                    ),
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text("Kamera $cameraId")
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                            }
                        }

                        item {
                            MenuSection(title = "Fahrzeug-Konfiguration", icon = Icons.Default.RvHookup) {
                                ListItem(
                                    headlineContent = { Text("Mit Anhänger") },
                                    supportingContent = { Text("Berücksichtigt Maße und Einschränkungen für Gespanne") },
                                    leadingContent = { Icon(Icons.Default.RvHookup, null) },
                                    trailingContent = {
                                        Switch(
                                            checked = uiState.hasTrailer,
                                            onCheckedChange = { viewModel.toggleTrailer() }
                                        )
                                    }
                                )
                            }
                            Spacer(Modifier.height(24.dp))
                        }

                        item {
                            MenuSection(title = "Kartenverwaltung", icon = Icons.Default.Map) {
                                MapManagementSection(
                                    state = uiState.downloadState,
                                    installedRegionIds = uiState.installedRegionIds,
                                    activeRegionId = uiState.activeRegionId,
                                    isValidating = uiState.isValidatingFile,
                                    validationError = uiState.fileValidationError,
                                    onValidateFile = { uri, onSuccess -> viewModel.validateMapFile(uri, onSuccess) },
                                    onClearValidationError = { viewModel.clearFileValidationError() },
                                    onImportLocalRegion = { name, uri -> viewModel.importRegion(name, uri) },
                                    onDeleteRegion = { viewModel.deleteRegion(it) },
                                    onLoadRegion = { viewModel.loadRegion(it) },
                                    onClearAll = { viewModel.clearAllRoutingData() },
                                    onCancelImport = { viewModel.cancelMapImport() }
                                )
                            }
                            Spacer(Modifier.height(24.dp))
                        }

                        item {
                            MenuSection(title = "Fahrzeugverwaltung", icon = Icons.Default.DirectionsCar) {
                                VehicleManagementSection(
                                    state = uiState.vehicleImportState,
                                    installedVehicleIds = uiState.installedVehicleIds,
                                    activeVehicleId = uiState.activeVehicleId,
                                    onImportVehicle = { name, uri -> viewModel.importVehicle(name, uri) },
                                    onDeleteVehicle = { viewModel.deleteVehicle(it) },
                                    onLoadVehicle = { viewModel.loadVehicle(it) }
                                )
                            }
                        }

                        if (uiState.supportedEncodedValues.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(24.dp))
                                MenuSection(title = "Aktive Routing-Parameter", icon = Icons.Default.SignalCellularAlt) {
                                    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                                        Text(
                                            "Folgende Fahrzeugmaße werden bei der Routenberechnung berücksichtigt:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        
                                        val profile = uiState.selectedVehicle
                                        val parameters = mutableListOf(
                                            "Max. Höhe" to "${String.format("%.2f", profile.height)} m",
                                            "Max. Breite" to "${String.format("%.2f", profile.width)} m",
                                            "Gewicht" to "${String.format("%.1f", profile.weight)} t",
                                            "Länge" to "${String.format("%.1f", profile.length)} m"
                                        )
                                        
                                        if (uiState.hasTrailer) {
                                            parameters.add("Gespann" to "Aktiv")
                                        }

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                                .padding(16.dp)
                                        ) {
                                            parameters.chunked(2).forEach { rowItems ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                                ) {
                                                    rowItems.forEach { (label, value) ->
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                                            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                        }
                                                    }
                                                    if (rowItems.size == 1) {
                                                        Spacer(Modifier.weight(1f))
                                                    }
                                                }
                                            }
                                        }
                                        
                                        Spacer(Modifier.height(16.dp))
                                        Text(
                                            "Hinweis: Die Route wird unter Berücksichtigung dieser Durchfahrtsbeschränkungen berechnet.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray.copy(alpha = 0.6f),
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- FULL SCREEN SEARCH OVERLAY ---
        if (isSearchOverlayVisible) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Input Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { 
                            isSearchOverlayVisible = false
                            isSettingHome = false
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { 
                                searchQuery = it
                                viewModel.search(it)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            placeholder = { Text("Ziel suchen...") },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { 
                                        searchQuery = ""
                                        viewModel.search("")
                                    }) {
                                        Icon(Icons.Default.Clear, "Löschen")
                                    }
                                }
                            },
                            singleLine = true
                        )
                    }

                    // Results List
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(uiState.searchResults) { result ->
                            ListItem(
                                headlineContent = { Text(result.name) },
                                supportingContent = { Text(result.description) },
                                leadingContent = { Icon(Icons.Default.Flag, null) },
                                modifier = Modifier.clickable {
                                    searchQuery = result.name
                                    if (isSettingHome) {
                                        viewModel.setHomeAddress(result)
                                        isSettingHome = false
                                        isSearchOverlayVisible = false
                                        isMapManagementOpen = true // Zurück zu den Einstellungen
                                    } else {
                                        viewModel.selectSearchResult(result)
                                        isSearchOverlayVisible = false
                                        mapInstance?.animateCamera(
                                            org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(result.location, 19.0)
                                        )
                                        val start = uiState.snappedLocation ?: uiState.rawLocation ?: LatLng(52.5200, 13.4050)
                                        viewModel.calculateRoute(start = start, end = result.location)
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }

                // Auto-Focus when visible
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
            }
        }
    }

    // Automatischer Zoom & Tilt während der Fahrt (BMW Style Floating Camera)
    LaunchedEffect(uiState.suggestedZoom, uiState.suggestedTilt, uiState.isNavigating, uiState.isNorthUp) {
        val map = mapInstance ?: return@LaunchedEffect
        // Hard-Limit auf 19.0 (ca. 25m) auch im UI-Layer klammern
        var zoom = (uiState.suggestedZoom ?: return@LaunchedEffect).coerceAtMost(19.0)
        
        // ZWINGEND: Tilt auf 0.0 setzen, wenn North-Up aktiv ist
        val tilt = if (uiState.isNorthUp) 0.0 else (uiState.suggestedTilt ?: 45.0)
        
        // Zoom auf 0.1er Schritte quantisieren für stabilere "Schritte"
        zoom = Math.round(zoom * 10.0) / 10.0
        
        // Nur zoomen, wenn Tracking aktiv ist UND wir nicht gerade manuell schieben
        if (isCameraTracking && !isSystemMovingCamera) {
            if (uiState.isNavigating) {
                // Striktes Tracking: Wir nutzen die internen Methoden der LocationComponent.
                // Dadurch bleibt der CCP exakt an seiner Bildschirmposition fixiert.
                // Wir erzwingen hier erneut den korrekten Modus, falls er verloren ging.
                val targetMode = if (uiState.isNorthUp) CameraMode.TRACKING else CameraMode.TRACKING_GPS
                if (map.locationComponent.cameraMode != targetMode) {
                    map.locationComponent.cameraMode = targetMode
                }
                
                // BMW Style: Kürzere Animationen (500ms) verhindern Stau bei schnellen Änderungen
                map.locationComponent.zoomWhileTracking(zoom, 500)
                map.locationComponent.tiltWhileTracking(tilt, 500)
            } else {
                isSystemMovingCamera = true
                map.animateCamera(
                    org.maplibre.android.camera.CameraUpdateFactory.zoomTo(zoom),
                    1500, // Von 3000ms auf 1500ms reduziert für reaktivere Verfolgung
                    object : MapLibreMap.CancelableCallback {
                        override fun onFinish() { isSystemMovingCamera = false }
                        override fun onCancel() { isSystemMovingCamera = false }
                    }
                )
            }
        }
    }

    // Kamera auf Standort zoomen, sobald dieser verfügbar ist (beim Start)
    LaunchedEffect(mapInstance, hasInitialLocationZoom, uiState.isNavigating, uiState.rawLocation, mapView.height, uiState.isMapReady) {
        val map = mapInstance ?: return@LaunchedEffect
        if (!uiState.isMapReady || hasInitialLocationZoom || uiState.isNavigating || mapView.height <= 0) {
            // android.util.Log.d("MapScreen", "InitialZoom skip: Ready=${uiState.isMapReady}, Done=$hasInitialLocationZoom, Nav=${uiState.isNavigating}, H=${mapView.height}")
            return@LaunchedEffect
        }

        uiState.rawLocation?.let { loc ->
            FileLogger.log("MapScreen: TRIGGERING INITIAL ZOOM to $loc")
            isSystemMovingCamera = true
            viewModel.setCameraTracking(true) // Force tracking on first fix
            
            // Padding sicherstellen, damit CCP im unteren Drittel landet
            updateMapPadding()
            
            map.animateCamera(
                org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(loc, 17.5),
                1500,
                object : MapLibreMap.CancelableCallback {
                    override fun onFinish() {
                        if (uiState.isMapReady) {
                            try {
                                map.locationComponent.cameraMode = org.maplibre.android.location.modes.CameraMode.TRACKING
                            } catch (e: Exception) {
                                android.util.Log.e("MapScreen", "onFinish LocationComponent error: ${e.message}")
                            }
                        }
                        hasInitialLocationZoom = true
                        viewModel.setCameraTracking(true)
                        isSystemMovingCamera = false
                        viewModel.setInitialZoomPerformed(true)
                        resetCameraTracking() // Ensure perfect alignment
                    }
                    override fun onCancel() {
                        if (uiState.isMapReady) {
                            try {
                                map.locationComponent.cameraMode = org.maplibre.android.location.modes.CameraMode.TRACKING
                            } catch (e: Exception) {
                                android.util.Log.e("MapScreen", "onCancel LocationComponent error: ${e.message}")
                            }
                        }
                        hasInitialLocationZoom = true
                        viewModel.setCameraTracking(true)
                        isSystemMovingCamera = false
                        viewModel.setInitialZoomPerformed(true)
                    }
                }
            )
        }
    }

    val displayLocation = uiState.snappedLocation ?: uiState.rawLocation
    LaunchedEffect(displayLocation, uiState.currentBearing, uiState.currentSpeed) {
        val loc = displayLocation ?: return@LaunchedEffect
        
        val dummyLocation = android.location.Location("fused").apply {
            latitude = loc.latitude
            longitude = loc.longitude
            time = System.currentTimeMillis()
            accuracy = 5f
            altitude = uiState.currentAltitude ?: 0.0
            bearing = GeoUtil.normalizeBearing(uiState.currentBearing)
            speed = uiState.currentSpeed
        }
        
        snappedLocationEngine.feed(
            location = dummyLocation,
            snapped = loc,
            bearing = GeoUtil.normalizeBearing(uiState.currentBearing),
            speed = uiState.currentSpeed
        )
    }

    // --- STATIC MAP CONTENT (Markers, Full Route) ---
    LaunchedEffect(uiState.currentRoute, uiState.startPoint, uiState.destinationPoint, uiState.isNavigating, styleUpdateTrigger, mapInstance) {
        val map = mapInstance ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        // HINWEIS: Wir prüfen NICHT mehr auf style.isFullyLoaded, da lokale Stile 
        // oft länger brauchen um diesen Status zu melden, aber trotzdem schon zeichnen können.

        // Wir leeren die Karte nur für Marker und Annotations
        map.clear()
        
        uiState.waypoints.forEachIndexed { index, point ->
            map.addMarker(MarkerOptions().position(point).title("Zwischenziel ${index + 1}"))
        }
        uiState.destinationPoint?.let { map.addMarker(MarkerOptions().position(it).title("Ziel")) }

        // Verkehrsmeldungen anzeigen
        uiState.trafficEvents.forEach { event ->
            map.addMarker(
                MarkerOptions()
                    .position(event.location)
                    .title(event.headline)
                    .snippet(event.description)
            )
        }

        uiState.currentRoute?.let { route ->
            android.util.Log.d("MapScreen", "Zeichne Route mit ${route.points.size} Punkten. Start: ${route.points.firstOrNull()?.latitude},${route.points.firstOrNull()?.longitude}")
            // Die gesamte Route im GeoJsonSource aktualisieren
            var source = style.getSourceAs<GeoJsonSource>(FULL_ROUTE_SOURCE_ID)
            if (source == null) {
                android.util.Log.w("MapScreen", "Route-Source '$FULL_ROUTE_SOURCE_ID' nicht gefunden! Initialisiere Layer neu...")
                setupRouteLayers(style)
                source = style.getSourceAs<GeoJsonSource>(FULL_ROUTE_SOURCE_ID)
            }

            if (source != null) {
                val points = route.points.map { Point.fromLngLat(it.longitude, it.latitude) }
                source.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(points))))
                android.util.Log.i("MapScreen", "GeoJsonSource erfolgreich aktualisiert.")
            } else {
                android.util.Log.e("MapScreen", "KRITISCH: GeoJsonSource konnte nicht erstellt werden.")
            }

            // Nur zoomen, wenn wir NICHT navigieren, damit die Navigation die Kamera steuern kann
            if (route.points.isNotEmpty() && !uiState.isNavigating) {
                // Kamera-Zoom wird nun über die ZENTRALE LOGIK gesteuert, um Konflikte zu vermeiden.
                android.util.Log.d("MapScreen", "Route gezeichnet. Kamera-Handover an Zentrale Logik.")
            }
        } ?: run {
            android.util.Log.d("MapScreen", "Keine Route vorhanden, lösche Overlays.")
            // Route löschen
            style.getSourceAs<GeoJsonSource>(FULL_ROUTE_SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
            style.getSourceAs<GeoJsonSource>(TRAVELED_PATH_SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        }
    }

    // --- DYNAMIC MAP CONTENT (Traveled Path Overlay) ---
    LaunchedEffect(uiState.routeProjection, uiState.lastRoutePointIndex, uiState.isNavigating, styleUpdateTrigger, mapInstance) {
        val map = mapInstance ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        if (!style.isFullyLoaded) return@LaunchedEffect

        if (!uiState.isNavigating || uiState.currentRoute == null) {
            style.getSourceAs<GeoJsonSource>(TRAVELED_PATH_SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
            return@LaunchedEffect
        }

        // Falls wir gerade keine Route-Projektion haben (off-route), lassen wir die Linie 
        // am letzten bekannten Punkt stehen, um visuelle Sprünge zu vermeiden.
        val projection = uiState.routeProjection ?: return@LaunchedEffect

        uiState.currentRoute?.let { route ->
            val routePoints = route.points
            val source = style.getSourceAs<GeoJsonSource>(TRAVELED_PATH_SOURCE_ID) ?: return@LaunchedEffect

            // Puffer-Logik: Wir enden 5m hinter dem CCP, um Rendering-Lags zu maskieren.
            // Dies verhindert, dass die graue Linie jemals "vor" die Fahrzeugnase ragt.
            val bufferMeters = 5.0
            var remaining = bufferMeters
            var lastPos = projection
            var currentIdx = uiState.lastRoutePointIndex.coerceAtMost(routePoints.size - 1)
            var bufferPoint = projection
            
            fun dist(p1: LatLng, p2: LatLng): Double {
                val r = 6371000.0
                val dLat = Math.toRadians(p2.latitude - p1.latitude)
                val dLon = Math.toRadians(p2.longitude - p1.longitude)
                val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                        Math.cos(Math.toRadians(p1.latitude)) * Math.cos(Math.toRadians(p2.latitude)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2)
                return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            }

            while (currentIdx >= 0 && remaining > 0) {
                val prev = routePoints[currentIdx]
                val d = dist(lastPos, prev)
                if (d > remaining) {
                    val ratio = (d - remaining) / d
                    bufferPoint = LatLng(
                        prev.latitude + (lastPos.latitude - prev.latitude) * ratio,
                        prev.longitude + (lastPos.longitude - prev.longitude) * ratio
                    )
                    remaining = 0.0
                } else {
                    remaining -= d
                    lastPos = prev
                    bufferPoint = prev
                    currentIdx--
                }
            }

            val traveledLatLngs = routePoints.subList(0, (currentIdx + 1).coerceAtLeast(0)).toMutableList()
            traveledLatLngs.add(bufferPoint)
            
            val geoPoints = traveledLatLngs.map { Point.fromLngLat(it.longitude, it.latitude) }
            if (geoPoints.isNotEmpty()) {
                source.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(geoPoints))))
            }
        }
    }

    // ZENTRALE LOGIK: Kamera-Nachführung & Modus-Wechsel (Navigation, Übersicht, Freies Fahren)
    LaunchedEffect(uiState.isNavigating, uiState.isNorthUp, uiState.currentRoute, mapInstance) {
        val map = mapInstance ?: return@LaunchedEffect
        val style = map.style
        if (style == null || !style.isFullyLoaded) return@LaunchedEffect

        val isNavigating = uiState.isNavigating
        val route = uiState.currentRoute

        try {
            if (isNavigating) {
                // FALL 1: Zielführung startet oder ist aktiv
                if (!wasNavigating) {
                    android.util.Log.d("MapScreen", "Navigation gestartet -> Reset Tracking")
                    resetCameraTracking()
                    lastZoomedRoute = null // Reset, damit wir beim nächsten Mal wieder zoomen
                }
            } else if (route != null) {
                // FALL 2: Routenübersicht (Vorschau)
                // Wir führen den Zoom nur aus, wenn die Route NEU ist oder wir gerade aus der Nav kommen.
                // Wir prüfen zusätzlich auf Distanz-Änderung, falls die Route nur minimal korrigiert wurde.
                val isNewRoute = route != lastZoomedRoute || route.distance != lastZoomedRoute?.distance
                
                if (isNewRoute || wasNavigating) {
                    android.util.Log.d("MapScreen", "Routenübersicht aktiv (New: $isNewRoute, WasNav: $wasNavigating) -> Zoom")
                    
                    // ZWINGEND: Erst mal alle Animationen stoppen
                    map.cancelTransitions()
                    map.locationComponent.cameraMode = CameraMode.NONE
                    viewModel.setCameraTracking(false)
                    updateMapPadding(isOverview = true)

                    val builder = LatLngBounds.Builder()
                    route.points.forEach { builder.include(it) }
                    try {
                        isSystemMovingCamera = true
                        
                        // Dynamisches Prozent-Padding (BMW Style: Zentrierung im freien Bereich)
                        val w = mapView.width.coerceAtLeast(100)
                        val h = mapView.height.coerceAtLeast(100)
                        
                        val padLeft = (w * 0.15).toInt()  // 15% Platz für das Menü
                        val padRight = (w * 0.42).toInt() // 42% Platz für die Infokarte (großzügiger)
                        val padTop = (h * 0.15).toInt()   
                        val padBottom = (h * 0.15).toInt() 

                        android.util.Log.d("MapScreen", "Starte Overview-Zoom: L=$padLeft, T=$padTop, R=$padRight, B=$padBottom")
                        
                        // Zuerst Tilt und Bearing auf 0 setzen, um Verzerrungen beim Bounds-Zoom zu vermeiden
                        map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.tiltTo(0.0))
                        map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.bearingTo(0.0))

                        map.animateCamera(
                            org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(
                                builder.build(), padLeft, padTop, padRight, padBottom
                            ),
                            2000,
                            object : MapLibreMap.CancelableCallback {
                                override fun onFinish() { isSystemMovingCamera = false }
                                override fun onCancel() { isSystemMovingCamera = false }
                            }
                        )
                        lastZoomedRoute = route 
                    } catch (e: Exception) {
                        android.util.Log.e("MapScreen", "Fehler beim Übersichts-Zoom", e)
                        isSystemMovingCamera = false
                    }
                }
            } else {
                // FALL 3: Freies Fahren (keine Route, keine Navigation)
                // Wenn wir gerade eine Navigation oder eine Übersicht beendet haben, springen wir zurück.
                val hadRoute = lastZoomedRoute != null
                if (wasNavigating || hadRoute) {
                    android.util.Log.d("MapScreen", "Nav/Route beendet -> Rücksprung zum GPS")
                    lastZoomedRoute = null
                    
                    if (uiState.snappedLocation != null || uiState.rawLocation != null) {
                        viewModel.setCameraTracking(true)
                        resetCameraTracking()
                        isWaitingForFirstFixAfterStop = false
                    } else {
                        isWaitingForFirstFixAfterStop = true
                    }
                } else if (uiState.isCameraTracking) {
                    // Normales Tracking sicherstellen (z.B. nach App-Resume)
                    if (map.locationComponent.cameraMode == CameraMode.NONE) {
                        resetCameraTracking()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MapScreen", "Kamera-Update fehlgeschlagen: ${e.message}")
        }
        
        wasNavigating = isNavigating
    }

    // TRIGGER: Padding aktualisieren, sobald der initiale Zoom durch ist
    LaunchedEffect(uiState.isInitialZoomPerformed) {
        if (uiState.isInitialZoomPerformed) {
            android.util.Log.d("MapScreen", "Initialer Zoom beendet, aktualisiere Padding auf Soll-Position.")
            updateMapPadding()
        }
    }

    // TRIGGER: Sofortige Reaktion auf Launcher-Sichtbarkeit (Home/Nav Wechsel)
    LaunchedEffect(uiState.navigationUiMode) {
        Log.d(
            "MapScreen",
            "UI State Change: navigationUiMode = " +
                    "${uiState.navigationUiMode} -> Updating padding and menu"
        )

        updateMapPadding()
    }

    // TRIGGER: Sofortige Reaktion auf den Orientierungs-Toggle
    LaunchedEffect(uiState.isNorthUp) {
        if (styleUpdateTrigger == 0) return@LaunchedEffect // Warte bis Karte initial geladen ist
        android.util.Log.d("MapScreen", "Orientierung gewechselt: NorthUp=${uiState.isNorthUp} -> Reset Tracking")
        
        // Wir nutzen die bestehende reset-Logik, da diese setCameraMode verwendet.
        // setCameraMode unterbricht das Tracking NICHT, im Gegensatz zu animateCamera.
        resetCameraTracking()
    }

    // Map Style Effect (Night Mode / Local Tiles)
    LaunchedEffect(uiState.isNightMode, uiState.hasLocalTiles, uiState.activeRegionId) {
        val map = mapInstance ?: return@LaunchedEffect
        val regionId = uiState.activeRegionId ?: return@LaunchedEffect
        
        val builder = org.maplibre.android.maps.Style.Builder()
        val isOffline = uiState.hasLocalTiles && uiState.localMBTilesPath != null
        
        if (isOffline) {
            try {
                val template = context.assets.open("offline_style.json").bufferedReader().use { it.readText() }
                val encodedRegion = android.net.Uri.encode(regionId)
                val finalJson = template.replace("PLACEHOLDER_REGION", encodedRegion)
                
                android.util.Log.d("MapScreen", "Lade Offline-Stil für: $regionId (JSON Length: ${finalJson.length})")
                builder.fromJson(finalJson)
            } catch (e: Exception) {
                android.util.Log.e("MapScreen", "Error loading offline style", e)
                builder.fromUri("https://tiles.openfreemap.org/styles/liberty")
            }
        } else {
            val url = if (uiState.isNightMode) "https://tiles.openfreemap.org/styles/fiord" 
                     else "https://tiles.openfreemap.org/styles/liberty"
            android.util.Log.d("MapScreen", "Wechsle zu Online-Stil (URI): $url")
            builder.fromUri(url)
        }
            
        map.setStyle(builder) { style ->
            android.util.Log.i("MapScreen", "Stil '$regionId' erfolgreich geladen.")
            updateMapPadding()
            enableLocation(map)
            setupRouteLayers(style)
            styleUpdateTrigger++
            viewModel.setMapReady(true)
        }
    }

    // Map Options Effect (POIs, Buildings)
    LaunchedEffect(mapInstance, uiState.showPois, uiState.showBuildings) {
        val map = mapInstance ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        if (!style.isFullyLoaded) return@LaunchedEffect

        style.layers.forEach { layer ->
            val layerId = layer.id
            if (layerId.contains("poi", ignoreCase = true) || 
                layerId.contains("place", ignoreCase = true) ||
                layerId.contains("park", ignoreCase = true)) {
                layer.setProperties(org.maplibre.android.style.layers.PropertyFactory.visibility(
                    if (uiState.showPois) org.maplibre.android.style.layers.Property.VISIBLE else org.maplibre.android.style.layers.Property.NONE
                ))
            }
            if (layerId.contains("building", ignoreCase = true)) {
                layer.setProperties(org.maplibre.android.style.layers.PropertyFactory.visibility(
                    if (uiState.showBuildings) org.maplibre.android.style.layers.Property.VISIBLE else org.maplibre.android.style.layers.Property.NONE
                ))
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(mapView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    // Beim Fortsetzen der App prüfen wir, ob wir das Tracking wieder aktivieren müssen
                    // WICHTIG: Nur wenn die Karte wirklich bereit ist (LocationComponent aktiviert)
                    if (uiState.isCameraTracking && !uiState.isNavigating && uiState.currentRoute == null && uiState.isMapReady) {
                        try {
                            resetCameraTracking()
                        } catch (e: Exception) {
                            Log.w("MapScreen", "ON_RESUME: resetCameraTracking failed")
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> {
                    android.util.Log.d("MapScreen", "ON_DESTROY: Clearing map instance and destroying view")
                    mapInstance = null
                    mapView.onDestroy()
                }
                else -> {}
            }
        }
        
        val layoutListener = android.view.View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateMapPadding()
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.addOnLayoutChangeListener(layoutListener)
        
        onDispose { 
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.removeOnLayoutChangeListener(layoutListener)
        }
    }
}

@Composable
fun getManeuverIcon(sign: Int?): ImageVector {
    return when(sign) {
        1 -> Icons.Default.TurnSlightRight
        2 -> Icons.AutoMirrored.Filled.ArrowRight
        3 -> Icons.Default.TurnSharpRight
        -1 -> Icons.Default.TurnSlightLeft
        -2 -> Icons.AutoMirrored.Filled.ArrowLeft
        -3 -> Icons.Default.TurnSharpLeft
        4, 5 -> Icons.Default.Flag
        6, 7 -> Icons.Default.RoundaboutLeft
        8, 9, 10 -> Icons.AutoMirrored.Filled.ArrowBack
        else -> Icons.Default.Straight
    }
}

private fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    
    return if (hours > 0) {
        "$hours h $minutes min"
    } else {
        "$minutes min"
    }
}

private fun formatArrivalTime(ms: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(ms))
}

@Composable
fun ScaleBar(map: org.maplibre.android.maps.MapLibreMap, modifier: Modifier = Modifier) {
    var scaleText by remember { mutableStateOf("---") }
    val fixedWidthDp = 64.dp
    val density = androidx.compose.ui.platform.LocalDensity.current

    LaunchedEffect(map) {
        while (true) {
            try {
                val cameraPosition = map.cameraPosition
                val target = cameraPosition.target
                if (target != null) {
                    val lat = target.latitude
                    val metersPerPixel = map.projection.getMetersPerPixelAtLatitude(lat)
                    val widthPx = with(density) { fixedWidthDp.toPx() }
                    val meters = metersPerPixel * widthPx
                    
                    scaleText = when {
                        meters >= 10000 -> "${(meters / 1000).toInt()} km"
                        meters >= 1000 -> "${String.format(java.util.Locale.getDefault(), "%.1f", meters / 1000.0)} km"
                        else -> "${(Math.round(meters / 10.0) * 10).toInt()} m"
                    }
                }
            } catch (e: Exception) {
                // Falls Karte gerade zerstört wird
                break
            }
            delay(500)
        }
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color.Black.copy(alpha = 0.7f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .width(fixedWidthDp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = scaleText,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White)
            )
        }
    }
}

@Composable
fun MapManagementSection(
    state: DownloadState,
    installedRegionIds: Set<String>,
    activeRegionId: String?,
    isValidating: Boolean,
    validationError: String?,
    onValidateFile: (android.net.Uri, () -> Unit) -> Unit,
    onClearValidationError: () -> Unit,
    onImportLocalRegion: (String, android.net.Uri) -> Unit,
    onDeleteRegion: (String) -> Unit,
    onLoadRegion: (String) -> Unit,
    onClearAll: () -> Unit,
    onCancelImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showManageDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    
    val activity = LocalActivity.current
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null && activity != null) {
                try {
                    activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {}
                
                // Namen aus URI extrahieren
                val fileName = activity.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst()) cursor.getString(nameIndex) else null
                } ?: "Map"
                
                val cleanName = fileName.substringBeforeLast(".")
                
                onValidateFile(uri) {
                    onImportLocalRegion(cleanName, uri)
                }
            }
        }
    )

    Column(modifier = modifier) {
        if (activeRegionId != null) {
            Text("Aktiv: $activeRegionId", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
        }

        if (isValidating) {
            Text("Prüfe Datei...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), color = MaterialTheme.colorScheme.secondary)
        } else if (validationError != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(validationError, style = MaterialTheme.typography.bodySmall, color = Color.Red, modifier = Modifier.weight(1f))
                IconButton(onClick = onClearValidationError, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, "Fehler schließen", tint = Color.Red, modifier = Modifier.size(16.dp))
                }
            }
        }

        if (state is DownloadState.Processing) {
            val progress = state.progress
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Verarbeite Karte... ", style = MaterialTheme.typography.bodySmall)
                Text("$progress%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCancelImport) {
                    Text("Abbrechen", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        } else if (state is DownloadState.Cancelling) {
            Text("Import wird abgebrochen...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.error
            )
        } else if (state is DownloadState.Completed) {
            Text("Import erfolgreich!", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
        } else if (state is DownloadState.Error) {
            Text(state.message, style = MaterialTheme.typography.bodySmall, color = Color.Red)
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { showManageDialog = true }, modifier = Modifier.weight(1f)) {
                Text("Karten wählen (${installedRegionIds.size})")
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { filePickerLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.FileUpload, "Lokal importieren")
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { showClearConfirmDialog = true },
                modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.Delete, "Alles löschen", tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Daten löschen") },
            text = { Text("Möchtest du wirklich alle Routing-Daten und importierten Karten löschen? Dieser Vorgang kann nicht rückgängig gemacht werden.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAll()
                        showClearConfirmDialog = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Alles löschen") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirmDialog = false }) { Text("Abbrechen") } }
        )
    }

    if (showManageDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showManageDialog = false }) {
            Surface(modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.7f), shape = MaterialTheme.shapes.extraLarge, tonalElevation = 8.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Installierte Karten", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(installedRegionIds.toList()) { regionId ->
                            ListItem(
                                headlineContent = { Text(regionId, fontWeight = if (regionId == activeRegionId) androidx.compose.ui.text.font.FontWeight.Bold else null) },
                                trailingContent = {
                                    Row {
                                        if (regionId != activeRegionId) {
                                            IconButton(onClick = { onLoadRegion(regionId); showManageDialog = false }) {
                                                Icon(Icons.Default.PlayArrow, "Laden", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        IconButton(onClick = { onDeleteRegion(regionId) }) {
                                            Icon(Icons.Default.Delete, "Löschen", tint = Color.Red)
                                        }
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                    Button(onClick = { showManageDialog = false }, modifier = Modifier.align(Alignment.End)) { Text("Schließen") }
                }
            }
        }
    }
}

@Composable
fun VehicleManagementSection(
    state: DownloadState,
    installedVehicleIds: Set<String>,
    activeVehicleId: String?,
    onImportVehicle: (String, android.net.Uri) -> Unit,
    onDeleteVehicle: (String) -> Unit,
    onLoadVehicle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showVehicleDialog by remember { mutableStateOf(false) }
    
    val vehiclePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                onImportVehicle("Vehicle", uri)
            }
        }
    )

    Column(modifier = modifier) {
        if (activeVehicleId != null) {
            Text("Aktiv: $activeVehicleId", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
        }

        if (state is DownloadState.Processing) {
            val progress = state.progress
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Verarbeite Fahrzeug... ", style = MaterialTheme.typography.bodySmall)
                Text("$progress%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        } else if (state is DownloadState.Error) {
            Text(state.message, style = MaterialTheme.typography.bodySmall, color = Color.Red)
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { showVehicleDialog = true }, modifier = Modifier.weight(1f)) {
                Text("Fahrzeuge wählen (${installedVehicleIds.size})")
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { vehiclePickerLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.FileUpload, "Fahrzeug importieren")
            }
        }
    }

    if (showVehicleDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showVehicleDialog = false }) {
            Surface(modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.7f), shape = MaterialTheme.shapes.extraLarge, tonalElevation = 8.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Installierte Fahrzeuge", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(installedVehicleIds.toList()) { vehicleId ->
                            ListItem(
                                headlineContent = { Text(vehicleId, fontWeight = if (vehicleId == activeVehicleId) androidx.compose.ui.text.font.FontWeight.Bold else null) },
                                trailingContent = {
                                    Row {
                                        if (vehicleId != activeVehicleId) {
                                            IconButton(onClick = { onLoadVehicle(vehicleId); showVehicleDialog = false }) {
                                                Icon(Icons.Default.PlayArrow, "Laden", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        IconButton(onClick = { onDeleteVehicle(vehicleId) }) {
                                            Icon(Icons.Default.Delete, "Löschen", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                    Button(onClick = { showVehicleDialog = false }, modifier = Modifier.align(Alignment.End)) { Text("Schließen") }
                }
            }
        }
    }
}

@Composable
fun KeyButton(
    text: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.DarkGray
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(8.dp),
        color = color,
        tonalElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                if (text != null) {
                    if (icon != null) Spacer(Modifier.width(8.dp))
                    Text(text, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
fun MenuSection(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
fun DestinationItem(
    result: com.example.campernavigator.service.SearchResult,
    isFavorite: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable { onSelect() },
        headlineContent = { Text(result.name) },
        supportingContent = { Text(result.description) },
        leadingContent = { Icon(Icons.Default.Flag, null) },
        trailingContent = {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarOutline,
                    contentDescription = "Favorit",
                    tint = if (isFavorite) Color(0xFFFFD700) else Color.Gray
                )
            }
        }
    )
}

@Composable
fun RouteSummaryCard(route: com.example.campernavigator.service.Route, onClear: () -> Unit, onStart: () -> Unit) {
    Surface(
        modifier = Modifier.width(320.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.75f),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Container (Primary color alpha 0.2)
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Explore, 
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Zielführung starten",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = "${String.format(java.util.Locale.getDefault(), "%.1f", route.distance / 1000.0)} km | ${formatDuration(route.time)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
            
            // Clear Button (X)
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Default.Close, 
                    contentDescription = "Löschen", 
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
            
            // Start Button (Primary color)
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .clickable { onStart() },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Navigation, 
                        contentDescription = "Start", 
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
