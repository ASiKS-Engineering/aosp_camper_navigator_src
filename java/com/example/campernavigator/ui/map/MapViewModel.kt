package com.example.campernavigator.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.hardware.camera2.CameraManager
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.campernavigator.model.VehicleProfile
import com.example.campernavigator.model.VehicleType
import com.example.campernavigator.model.RoutingMode
import java.io.File
import com.example.campernavigator.service.DownloadState
import com.example.campernavigator.service.MapDownloadManager
import com.example.campernavigator.service.Route
import com.example.campernavigator.service.RoutingService
import com.example.campernavigator.service.SearchResult
import com.example.campernavigator.service.SearchService
import com.example.campernavigator.service.TrafficEvent
import com.example.campernavigator.worker.MapImportWorker
import com.example.campernavigator.data.DestinationEntity
import com.example.campernavigator.data.DestinationDao
import com.example.campernavigator.data.SearchRepository
import com.example.campernavigator.data.TrafficRepository
import com.example.campernavigator.service.GraphHopperEngine
import com.example.campernavigator.service.LocationProvider
import com.example.campernavigator.util.ConnectionStatus
import com.example.campernavigator.util.ConnectivityObserver
import com.example.campernavigator.util.JsonUtil
import com.example.campernavigator.util.FileLogger
import com.example.campernavigator.service.VehicleManager
import com.example.campernavigator.service.VoiceService
import com.example.campernavigator.util.GeoUtil
import com.example.campernavigator.util.LocalTileRegistry
import com.example.campernavigator.util.MBTilesManager
import com.example.campernavigator.worker.VehicleImportWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.json.JSONObject

data class MapUiState(
    val currentRoute: Route? = null,
    val selectedVehicle: VehicleProfile = VehicleProfile("Standard Camper", VehicleType.CAMPER, 3.2, 2.3, 6.0, 3.5),
    val isLoading: Boolean = false,
    val loadingMessage: String? = null,
    val downloadState: DownloadState = DownloadState.Idle,
    val installedRegionIds: Set<String> = emptySet(),
    val searchResults: List<SearchResult> = emptyList(),
    val startPoint: LatLng? = null,
    val destinationPoint: LatLng? = null,
    val waypoints: List<LatLng> = emptyList(),
    val activeRegionId: String? = null,
    val isNavigating: Boolean = false,
    val isGuidanceActive: Boolean = false,
    val isNorthUp: Boolean = false,
    val showErrorDialog: String? = null,
    val currentAltitude: Double? = null,
    val currentStreetName: String? = null,
    val currentInstruction: String? = null,
    val currentInstructionSign: Int? = null,
    val snappedLocation: LatLng? = null,
    val rawLocation: LatLng? = null,
    val currentBearing: Float = 0f,
    val currentSpeed: Float = 0f,
    val currentSpeedLimit: Int? = null,
    val isAdvisorySpeed: Boolean = false,
    val suggestedZoom: Double? = null,
    val remainingDistance: Double? = null,
    val remainingTime: Long? = null,
    val distanceToNextInstruction: Double? = null,
    val nextSpeedLimit: Int? = null,
    val distanceToNextSpeedLimit: Double? = null,
    val favorites: List<SearchResult> = emptyList(),
    val recentDestinations: List<SearchResult> = emptyList(),
    val supportedEncodedValues: List<String> = emptyList(),
    val showPois: Boolean = true,
    val showBuildings: Boolean = true,
    val isAutoZoomEnabled: Boolean = true,
    val hasTrailer: Boolean = false,
    val isMphEnabled: Boolean = false,
    val mapMode: MapMode = MapMode.AUTO,
    val isNightMode: Boolean = false,
    val homeAddress: SearchResult? = null,
    val activeVehicleId: String? = null,
    val availableCameras: List<String> = emptyList(),
    val selectedCameraId: String? = null,
    val campingResults: List<SearchResult> = emptyList(),
    val isCampingSearchLoading: Boolean = false,
    val pendingCampingSite: SearchResult? = null,
    val trafficEvents: List<TrafficEvent> = emptyList(),
    val isLocationDetermined: Boolean = false,
    val isMapReady: Boolean = false,
    val isInitialZoomPerformed: Boolean = false,
    val forceExitSplash: Boolean = false,
    val isOffline: Boolean = false,
    val isValidatingFile: Boolean = false,
    val fileValidationError: String? = null,
    val lastRoutePointIndex: Int = 0,
    val isDemoMode: Boolean = false,
    val suggestedTilt: Double? = null,
    val isMinorRecalculation: Boolean = false,
    val routeProjection: LatLng? = null,
    val isSnappedToRoute: Boolean = false,
    val satelliteCount: Int? = null,
    val showResumeNavigationDialog: Boolean = false,
    val savedNavigationDestination: SearchResult? = null,
    val savedWaypoints: List<LatLng> = emptyList(),
    val hasLocalTiles: Boolean = false,
    val localMBTilesPath: String? = null,
    val isTransitioningFromDemo: Boolean = false,
    val currentManeuverStreet: String? = null,
    val currentRoundaboutExit: Int? = null,
    val upcomingInstructionSign: Int? = null,
    val upcomingManeuverStreet: String? = null,
    val upcomingInstruction: String? = null,
    val upcomingRoundaboutExit: Int? = null,
    val isPowerSaveModeEnabled: Boolean = false,
    val routingMode: RoutingMode = RoutingMode.FASTEST,
    val installedVehicleIds: Set<String> = emptySet(),
    val vehicleImportState: DownloadState = DownloadState.Idle,
    val isLauncherVisible: Boolean = false, // Default to Home mode for safer embedding
    val isCameraTracking: Boolean = true
)

enum class MapMode { DAY, NIGHT, AUTO }

data class SnapResult(val position: LatLng, val bearing: Float, val isSnapped: Boolean)

class MapViewModel(
    private var routingService: RoutingService,
    private val mapDownloadManager: MapDownloadManager,
    private val graphHopperEngine: GraphHopperEngine,
    private val searchRepository: SearchRepository,
    private val voiceService: VoiceService? = null,
    private val destinationDao: DestinationDao? = null,
    private val trafficRepository: TrafficRepository? = null,
    private val connectivityObserver: ConnectivityObserver? = null,
    private val vehicleManager: VehicleManager,
    private val locationProvider: LocationProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var lastSpokenInstructionIndex = -1
    private var wasCurrentInstructionSpoken = false
    private var searchJob: Job? = null
    private var campingSearchJob: Job? = null
    private val MAX_ALLOWED_ZOOM = 19.0
    private var isInitializingGraphHopper = false

    private var lastRoutePointIndex = 0
    private var lastGpsUpdateMillis = 0L
    private var simulationJob: Job? = null
    
    private val PREFS_NAME = "navigation_state"
    private val KEY_NORTH_UP = "is_north_up"
    private val KEY_POWER_SAVE = "power_save"
    private val KEY_AUTO_ZOOM = "auto_zoom"
    private val KEY_POIS = "show_pois"
    private val KEY_BUILDINGS = "show_buildings"
    private val KEY_MPH = "is_mph"
    private val KEY_MAP_MODE = "map_mode"
    private val KEY_ROUTING_MODE = "routing_mode"
    private val KEY_ACTIVE_VEHICLE = "active_vehicle_id"

    private var isLocationTrackingActive = false

    init {
        // 1. Zuerst alle verfügbaren Daten scannen
        refreshInstalledRegions()
        vehicleManager.cleanUpOrphanedFolders()
        refreshInstalledVehicles()
        refreshAvailableCameras()

        // 2. Einstellungen laden (inkl. aktives Fahrzeug)
        loadSettings()
        
        startLocationTracking()

        // AUTO-LOAD MAP: Falls eine fertige Karte "MapPack_..." existiert, laden wir sie sofort
        viewModelScope.launch {
            delay(1000)
            val foundMap = uiState.value.installedRegionIds.find { it.startsWith("MapPack_") }
            if (foundMap != null && uiState.value.activeRegionId == null) {
                FileLogger.log("MapViewModel: Auto-loading discovered map: $foundMap")
                loadRegion(foundMap)
            }
        }

        viewModelScope.launch {
            connectivityObserver?.observe()?.collect { status ->
                _uiState.update { it.copy(isOffline = status != ConnectionStatus.Available) }
            }
        }

        // --- Import Beobachter (Sanierung) ---
        viewModelScope.launch {
            val wm = WorkManager.getInstance(graphHopperEngine.context)
            wm.getWorkInfosByTagFlow("map_import").collect { workInfos ->
                // Wir nehmen NUR das EINE WorkInfo, das gerade aktiv ist oder als letztes beendet wurde
                val activeWork = workInfos.find { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
                    ?: workInfos.maxByOrNull { it.id.toString() } ?: return@collect
                
                when (activeWork.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = activeWork.progress.getInt(MapImportWorker.KEY_PROGRESS, 0)
                        _uiState.update { it.copy(downloadState = DownloadState.Processing(progress)) }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        if (_uiState.value.downloadState !is DownloadState.Completed) {
                            val id = activeWork.outputData.getString(MapImportWorker.KEY_DISCOVERED_ID)
                            // Nur laden, wenn die ID vorhanden ist (Ghost worker geben keine ID zurück)
                            if (id != null && !id.startsWith("tmp_") && !id.contains("stream_")) {
                                FileLogger.log("MapViewModel: Import Success for $id")
                                _uiState.update { it.copy(downloadState = DownloadState.Completed) }
                                refreshInstalledRegions()
                                launch { delay(3000); _uiState.update { it.copy(downloadState = DownloadState.Idle) } }
                                loadRegion(id)
                            } else {
                                FileLogger.log("MapViewModel: Ignored ghost worker success signal.")
                            }
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        val error = activeWork.outputData.getString(MapImportWorker.KEY_ERROR_MESSAGE)
                        _uiState.update { it.copy(downloadState = DownloadState.Error(error ?: "Fehler")) }
                    }
                    WorkInfo.State.CANCELLED -> {
                        FileLogger.log("MapViewModel: Import was CANCELLED by user.")
                        // Wir lassen den "Abgebrochen" Status kurz stehen
                        _uiState.update { it.copy(downloadState = DownloadState.Cancelling) }
                        launch {
                            delay(3000)
                            _uiState.update { it.copy(downloadState = DownloadState.Idle) }
                        }
                    }
                    else -> {
                        // Für alle anderen Zustände (z.B. BLOCKED) gehen wir auf Idle
                        if (activeWork.state.isFinished && activeWork.state != WorkInfo.State.SUCCEEDED && activeWork.state != WorkInfo.State.FAILED) {
                            _uiState.update { it.copy(downloadState = DownloadState.Idle) }
                        }
                    }
                }
            }
        }

        // SPLASH SCREEN TIMEOUT
        viewModelScope.launch {
            delay(10000)
            if (!_uiState.value.isLocationDetermined) {
                _uiState.update { it.copy(isLocationDetermined = true, isInitialZoomPerformed = true, forceExitSplash = true) }
            }
        }
    }

    private fun DestinationEntity.toSearchResult() = SearchResult(name, description, LatLng(latitude, longitude))

    private fun loadSettings() {
        val prefs = graphHopperEngine.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val activeVehicleId = prefs.getString(KEY_ACTIVE_VEHICLE, null)
        
        _uiState.update { it.copy(
            isNorthUp = prefs.getBoolean(KEY_NORTH_UP, false),
            isPowerSaveModeEnabled = prefs.getBoolean(KEY_POWER_SAVE, false),
            isAutoZoomEnabled = prefs.getBoolean(KEY_AUTO_ZOOM, true),
            showPois = prefs.getBoolean(KEY_POIS, true),
            showBuildings = prefs.getBoolean(KEY_BUILDINGS, true),
            isMphEnabled = prefs.getBoolean(KEY_MPH, false),
            mapMode = MapMode.valueOf(prefs.getString(KEY_MAP_MODE, MapMode.AUTO.name) ?: MapMode.AUTO.name),
            routingMode = RoutingMode.valueOf(prefs.getString(KEY_ROUTING_MODE, RoutingMode.FASTEST.name) ?: RoutingMode.FASTEST.name)
        ) }

        if (activeVehicleId != null) {
            loadVehicle(activeVehicleId)
        }
    }

    private fun saveSetting(key: String, value: Any) {
        val prefs = graphHopperEngine.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            when (value) {
                is Boolean -> putBoolean(key, value)
                is String -> putString(key, value)
                is Int -> putInt(key, value)
            }
            apply()
        }
    }

    fun startLocationTracking() {
        if (isLocationTrackingActive) return
        try {
            locationProvider.startLocationUpdates { location -> 
                updateUserLocation(location) 
                // Satellitenzahl aus dem Provider synchronisieren
                _uiState.update { it.copy(satelliteCount = locationProvider.getSatelliteCount()) }
            }
            isLocationTrackingActive = true
        } catch (e: Exception) {
            FileLogger.log("MapViewModel: Error starting location updates: ${e.message}", "ERROR")
        }
    }

    fun updateUserLocation(location: Location) {
        if (!_uiState.value.isLocationDetermined) {
            FileLogger.log("MapViewModel: FIRST FIX from ${location.provider}")
            _uiState.update { it.copy(isLocationDetermined = true) }
        }
        lastGpsUpdateMillis = System.currentTimeMillis()
        
        // CRASH-FIX: Normalisierung des Bearing-Werts für Pi 5 HAL (muss [0, 360) sein)
        val safeBearing = GeoUtil.normalizeBearing(location.bearing)

        val rawLatLng = LatLng(location.latitude, location.longitude)
        
        viewModelScope.launch(Dispatchers.Default) {
            val isActive = _uiState.value.activeRegionId != null && !_uiState.value.isLoading
            val snapResult = if (isActive) {
                SnapResult(routingService.snapToRoad(rawLatLng), safeBearing, false)
            } else SnapResult(rawLatLng, safeBearing, false)
            
            withContext(Dispatchers.Main) {
                // HÖHEN-FILTER: Wir ignorieren Sprünge auf exakt 0.0m (oft ein Zeichen für verlorenen 3D Fix)
                val newAlt = if (location.altitude != 0.0) {
                    Math.round(location.altitude / 10.0) * 10.0
                } else {
                    _uiState.value.currentAltitude // Behalte letzte bekannte Höhe
                }

                _uiState.update { it.copy(
                    snappedLocation = snapResult.position,
                    rawLocation = rawLatLng,
                    currentBearing = snapResult.bearing,
                    currentSpeed = location.speed,
                    currentAltitude = newAlt
                ) }
                if (_uiState.value.isNavigating) checkNavigationInstructions(snapResult.position)
            }
        }
    }

    private fun checkNavigationInstructions(location: LatLng) {
        val route = _uiState.value.currentRoute ?: return
        val instructions = route.instructions
        if (instructions.isEmpty()) return
        val nextIndex = lastSpokenInstructionIndex + 1
        if (nextIndex >= instructions.size) return
        val nextManeuver = instructions[nextIndex]
        val dist = calculateDistance(location, nextManeuver.location)
        if (dist < 70.0 && !wasCurrentInstructionSpoken) {
            voiceService?.speak(nextManeuver.text)
            wasCurrentInstructionSpoken = true
        }
        if (dist < 20.0) {
            lastSpokenInstructionIndex = nextIndex
            wasCurrentInstructionSpoken = false
        }
    }

    fun updateNightMode(lux: Float) { if (_uiState.value.mapMode == MapMode.AUTO) { val isNight = lux < 20f; if (_uiState.value.isNightMode != isNight) _uiState.update { it.copy(isNightMode = isNight) } } }
    fun updateNightMode(isNight: Boolean) { if (_uiState.value.isNightMode != isNight) _uiState.update { it.copy(isNightMode = isNight) } }
    fun onNavigationCameraReady() { if (!_uiState.value.isNavigating || _uiState.value.isGuidanceActive) return; _uiState.update { it.copy(isGuidanceActive = true) } }
    fun toggleMapOrientation() { val newValue = !_uiState.value.isNorthUp; _uiState.update { it.copy(isNorthUp = newValue) }; saveSetting(KEY_NORTH_UP, newValue) }
    
    fun calculateRoute(start: LatLng, end: LatLng, waypoints: List<LatLng> = emptyList()) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingMessage = "Berechne Route...") }
            try {
                val route = routingService.calculateRoute(start, end, _uiState.value.selectedVehicle, _uiState.value.hasTrailer, waypoints)
                _uiState.update { it.copy(currentRoute = route, isLoading = false, destinationPoint = end, waypoints = waypoints) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, showErrorDialog = e.message) }
            }
        }
    }

    fun clearRoute() { _uiState.update { it.copy(currentRoute = null, destinationPoint = null, waypoints = emptyList(), isNavigating = false, isGuidanceActive = false) } }
    fun startNavigation() { if (_uiState.value.currentRoute != null) _uiState.update { it.copy(isNavigating = true, isGuidanceActive = true) } }
    fun stopNavigation() { _uiState.update { it.copy(isNavigating = false, currentRoute = null) } }
    fun dismissErrorDialog() { _uiState.update { it.copy(showErrorDialog = null) } }
    fun dismissResumeDialog() { _uiState.update { it.copy(showResumeNavigationDialog = false) } }
    fun resumeNavigation() { startNavigation() }

    fun search(query: String) {
        searchJob?.cancel()
        if (query.length < 3) { _uiState.update { it.copy(searchResults = emptyList()) }; return }
        searchJob = viewModelScope.launch { delay(500); val results = searchRepository.search(query); _uiState.update { it.copy(searchResults = results) } }
    }

    fun selectSearchResult(result: SearchResult) {
        val start = _uiState.value.snappedLocation ?: _uiState.value.rawLocation ?: LatLng(48.13, 11.57)
        calculateRoute(start, result.location)
    }

    fun toggleFavorite(result: SearchResult) { }
    fun togglePowerSaveMode() { val n = !_uiState.value.isPowerSaveModeEnabled; _uiState.update { it.copy(isPowerSaveModeEnabled = n) }; saveSetting(KEY_POWER_SAVE, n) }
    fun toggleDemoMode() { _uiState.update { it.copy(isDemoMode = !_uiState.value.isDemoMode) } }
    fun setRoutingMode(mode: RoutingMode) { _uiState.update { it.copy(routingMode = mode) }; saveSetting(KEY_ROUTING_MODE, mode.name) }
    fun togglePois() { val n = !_uiState.value.showPois; _uiState.update { it.copy(showPois = n) }; saveSetting(KEY_POIS, n) }
    fun toggleBuildings() { val n = !_uiState.value.showBuildings; _uiState.update { it.copy(showBuildings = n) }; saveSetting(KEY_BUILDINGS, n) }
    fun toggleAutoZoom() { val n = !_uiState.value.isAutoZoomEnabled; _uiState.update { it.copy(isAutoZoomEnabled = n) }; saveSetting(KEY_AUTO_ZOOM, n) }
    fun toggleSpeedUnit() { val n = !_uiState.value.isMphEnabled; _uiState.update { it.copy(isMphEnabled = n) }; saveSetting(KEY_MPH, n) }
    fun setMapMode(mode: MapMode) { _uiState.update { it.copy(mapMode = mode) }; saveSetting(KEY_MAP_MODE, mode.name) }
    fun selectCamera(id: String?) { _uiState.update { it.copy(selectedCameraId = id) } }
    fun toggleTrailer() { _uiState.update { it.copy(hasTrailer = !_uiState.value.hasTrailer) } }

    fun validateMapFile(uri: Uri, onSuccess: () -> Unit) { onSuccess() }
    fun clearFileValidationError() { _uiState.update { it.copy(fileValidationError = null) } }
    fun importRegion(id: String, uri: Uri) { mapDownloadManager.importRegion(id, uri) }
    fun deleteRegion(id: String) { mapDownloadManager.deleteRegion(id); refreshInstalledRegions() }
    fun clearAllRoutingData() { mapDownloadManager.clearAllRoutingData(); refreshInstalledRegions() }
    fun cancelMapImport() { mapDownloadManager.cancelImport(); refreshInstalledRegions() }

    fun importVehicle(name: String, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(vehicleImportState = DownloadState.Processing(0)) }
            vehicleManager.importVehicle(name, uri)
        }
    }
    fun deleteVehicle(id: String) { vehicleManager.deleteVehicle(id); refreshInstalledVehicles() }
    fun loadVehicle(id: String) {
        viewModelScope.launch {
            FileLogger.log("MapViewModel: Loading vehicle profile: $id")
            val internalVehicleDir = File(graphHopperEngine.context.filesDir, "vehicles/$id")
            val externalVehicleDir = File(graphHopperEngine.context.getExternalFilesDir(null), "vehicles/$id")
            
            val vehicleDir = if (internalVehicleDir.exists()) internalVehicleDir else externalVehicleDir
            val vFile = File(vehicleDir, "vehicles").listFiles { f -> f.extension == "json" }?.firstOrNull()
            
            if (vFile != null) {
                try {
                    val sanitized = JsonUtil.sanitizeJson(vFile.readText())
                    val json = JSONObject(sanitized).optJSONObject("vehicle")
                    if (json != null) {
                        val profile = VehicleProfile(
                            name = json.optString("name", id),
                            type = VehicleType.CAMPER,
                            height = json.optDouble("height", 3.22),
                            width = json.optDouble("width", 2.35),
                            length = json.optDouble("length", 7.2),
                            weight = json.optDouble("weight", 3.5)
                        )
                        _uiState.update { it.copy(selectedVehicle = profile, activeVehicleId = id) }
                        saveSetting(KEY_ACTIVE_VEHICLE, id)
                        FileLogger.log("MapViewModel: Vehicle $id loaded successfully")
                    }
                } catch (e: Exception) {
                    FileLogger.log("MapViewModel: Error parsing vehicle file: ${e.message}", "ERROR")
                }
            } else {
                FileLogger.log("MapViewModel: No JSON found in vehicles/$id/vehicles/", "ERROR")
            }
        }
    }
    fun setHomeAddress(result: SearchResult) { _uiState.update { it.copy(homeAddress = result) } }
    fun setInitialZoomPerformed(p: Boolean) { _uiState.update { it.copy(isInitialZoomPerformed = p) } }
    fun setMapReady(r: Boolean) { _uiState.update { it.copy(isMapReady = r) }; if (r) startLocationTracking() }
    fun setLauncherVisibility(visible: Boolean) { 
        FileLogger.log("MapViewModel: setLauncherVisibility changed to $visible")
        _uiState.update { it.copy(isLauncherVisible = visible) } 
    }
    fun setCameraTracking(active: Boolean) { _uiState.update { it.copy(isCameraTracking = active) } }

    fun searchCamping(query: String = "", alongRoute: Boolean = false, location: LatLng? = null) { }
    fun clearCampingResults() { _uiState.update { it.copy(campingResults = emptyList()) } }
    fun onCampingSiteSelected(site: SearchResult) { _uiState.update { it.copy(pendingCampingSite = site) } }
    fun cancelCampingSelection() { _uiState.update { it.copy(pendingCampingSite = null) } }
    fun addCampingAsWaypoint(site: SearchResult) { addWaypoint(site.location); _uiState.update { it.copy(pendingCampingSite = null) } }
    fun addWaypoint(point: LatLng) {
        val currentWaypoints = _uiState.value.waypoints.toMutableList()
        currentWaypoints.add(point)
        val start = _uiState.value.snappedLocation ?: _uiState.value.rawLocation ?: return
        val end = _uiState.value.destinationPoint ?: return
        calculateRoute(start, end, waypoints = currentWaypoints)
    }
    fun setCampingAsNewDestination(site: SearchResult) { calculateRoute(_uiState.value.snappedLocation ?: LatLng(48.13, 11.57), site.location); _uiState.update { it.copy(pendingCampingSite = null) } }

    private fun refreshAvailableCameras() { }
    private fun refreshInstalledRegions() {
        val rootInternal = File(graphHopperEngine.context.filesDir, "routing")
        val rootExternal = File(graphHopperEngine.context.getExternalFilesDir(null), "routing")
        val ids = mutableSetOf<String>()
        
        listOf(rootInternal, rootExternal).forEach { dir ->
            if (dir.exists()) {
                dir.listFiles { f -> 
                    f.isDirectory && !f.name.startsWith(".") && 
                    !f.name.startsWith("tmp_") && !f.name.startsWith("stream_") && !f.name.startsWith("diag_")
                }?.forEach { ids.add(it.name) }
            }
        }
        _uiState.update { it.copy(installedRegionIds = ids) }
    }
    private fun refreshInstalledVehicles() { _uiState.update { it.copy(installedVehicleIds = vehicleManager.getInstalledVehicles().toSet()) } }

    fun loadRegion(id: String) { viewModelScope.launch { initGraphHopper(id) } }
    private suspend fun initGraphHopper(id: String) {
        if (isInitializingGraphHopper) return
        isInitializingGraphHopper = true
        _uiState.update { it.copy(isLoading = true, loadingMessage = "Lade Karte: $id...") }
        try {
            FileLogger.log("MapViewModel: Initializing GraphHopper for $id")
            routingService = graphHopperEngine.init(id, _uiState.value.routingMode)
            
            // MBTiles Suche (Intern & Extern)
            val internalDir = File(graphHopperEngine.context.filesDir, "routing/$id")
            val externalDir = File(graphHopperEngine.context.getExternalFilesDir(null), "routing/$id")
            val dirToSearch = if (internalDir.exists()) internalDir else externalDir
            
            val filesInDir = dirToSearch.list()?.joinToString(", ") ?: "none"
            FileLogger.log("MapViewModel: Files in region dir: $filesInDir")
            
            val mbtilesFile = dirToSearch.listFiles()?.find { it.name.endsWith(".mbtiles") }
            
            if (mbtilesFile != null) {
                FileLogger.log("MapViewModel: SUCCESS - Found local MBTiles at ${mbtilesFile.absolutePath}")
                LocalTileRegistry.register(id, mbtilesFile.absolutePath)
                _uiState.update { it.copy(activeRegionId = id, isLoading = false, hasLocalTiles = true, localMBTilesPath = mbtilesFile.absolutePath) }
            } else {
                FileLogger.log("MapViewModel: WARNING - No MBTiles found in $id. Using online fallback.", "ERROR")
                _uiState.update { it.copy(activeRegionId = id, isLoading = false, hasLocalTiles = false) }
            }
        } catch (e: Exception) {
            FileLogger.log("MapViewModel: GraphHopper initialization FAILED: ${e.message}", "ERROR")
            _uiState.update { it.copy(isLoading = false, showErrorDialog = e.message) }
        } finally { isInitializingGraphHopper = false }
    }

    private fun calculateDistance(p1: LatLng, p2: LatLng): Double {
        val r = 6371e3
        val a = Math.sin(Math.toRadians(p2.latitude - p1.latitude) / 2).let { it * it } +
                Math.cos(Math.toRadians(p1.latitude)) * Math.cos(Math.toRadians(p2.latitude)) *
                Math.sin(Math.toRadians(p2.longitude - p1.longitude) / 2).let { it * it }
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    override fun onCleared() {
        super.onCleared()
        MBTilesManager.closeAll()
        if (isLocationTrackingActive) locationProvider.stopLocationUpdates()
    }
}
