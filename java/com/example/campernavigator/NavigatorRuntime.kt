package com.example.campernavigator

import android.content.Context
import com.example.campernavigator.data.AppDatabase
import com.example.campernavigator.model.RoutingMode
import com.example.campernavigator.service.GraphHopperEngine
import com.example.campernavigator.service.MapDownloadManager
import com.example.campernavigator.service.Open511Service
import com.example.campernavigator.service.SearchService
import com.example.campernavigator.service.VehicleManager
import com.example.campernavigator.util.FileLogger
import com.example.campernavigator.util.TileInterceptor
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil

object NavigatorRuntime {
    const val PREFS_NAME = "navigation_state"
    const val KEY_ACTIVE_REGION = "active_region_id"
    private const val KEY_ROUTING_MODE = "routing_mode"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var mapRuntimeConfigured = false

    private val lock = Any()

    fun initialize(context: Context) {
        if (appContext != null) {
            return
        }

        synchronized(lock) {
            if (appContext == null) {
                appContext = context.applicationContext
            }
        }
    }

    fun configureMapRuntime(context: Context) {
        initialize(context)
        if (!mapRuntimeConfigured) {
            synchronized(lock) {
                if (!mapRuntimeConfigured) {
                    MapLibre.getInstance(requireContext())
                    mapRuntimeConfigured = true
                }
            }
        }

        HttpRequestUtil.setOkHttpClient(mapTileClient(context))
    }

    fun appHttpClient(context: Context): OkHttpClient {
        initialize(context)
        return sharedAppHttpClient
    }

    fun mapTileClient(context: Context): OkHttpClient {
        initialize(context)
        return sharedMapTileClient
    }

    fun graphHopperEngine(context: Context): GraphHopperEngine {
        initialize(context)
        return sharedGraphHopperEngine
    }

    fun appDatabase(context: Context): AppDatabase {
        initialize(context)
        return sharedDatabase
    }

    fun mapDownloadManager(context: Context): MapDownloadManager {
        initialize(context)
        return sharedMapDownloadManager
    }

    fun vehicleManager(context: Context): VehicleManager {
        initialize(context)
        return sharedVehicleManager
    }

    fun searchService(context: Context): SearchService {
        initialize(context)
        return sharedSearchService
    }

    fun open511Service(context: Context): Open511Service {
        initialize(context)
        return sharedOpen511Service
    }

    suspend fun prewarm(context: Context, reason: String) {
        initialize(context)
        FileLogger.init(requireContext())
        configureMapRuntime(requireContext())

        val database = sharedDatabase
        database.destinationDao()
        database.trafficEventDao()
        database.cachedSearchDao()
        sharedMapDownloadManager
        sharedVehicleManager
        sharedSearchService
        sharedOpen511Service

        val regionId = findWarmRegionId(requireContext())
        if (regionId == null) {
            FileLogger.log("NavigatorRuntime: No region available for prewarm ($reason)")
            return
        }

        val routingMode = getSavedRoutingMode(requireContext())
        try {
            sharedGraphHopperEngine.init(regionId, routingMode)
            FileLogger.log(
                "NavigatorRuntime: Prewarm completed for region $regionId in mode $routingMode ($reason)"
            )
        } catch (e: Exception) {
            FileLogger.log(
                "NavigatorRuntime: Prewarm failed for region $regionId: ${e.message}",
                "ERROR"
            )
        }
    }

    fun saveActiveRegion(context: Context, regionId: String) {
        initialize(context)
        prefs(requireContext()).edit().putString(KEY_ACTIVE_REGION, regionId).apply()
    }

    fun findWarmRegionId(context: Context): String? {
        initialize(context)

        val preferredRegionId = prefs(requireContext()).getString(KEY_ACTIVE_REGION, null)
        if (!preferredRegionId.isNullOrBlank() && regionExists(requireContext(), preferredRegionId)) {
            return preferredRegionId
        }

        val installedRegions = findInstalledRegions(requireContext())
        return installedRegions.firstOrNull { it.startsWith("MapPack_") }
            ?: installedRegions.firstOrNull()
    }

    private fun getSavedRoutingMode(context: Context): RoutingMode {
        initialize(context)
        val storedMode = prefs(requireContext()).getString(KEY_ROUTING_MODE, RoutingMode.FASTEST.name)
        return try {
            RoutingMode.valueOf(storedMode ?: RoutingMode.FASTEST.name)
        } catch (_: IllegalArgumentException) {
            RoutingMode.FASTEST
        }
    }

    private fun regionExists(context: Context, regionId: String): Boolean {
        return findInstalledRegions(context).contains(regionId)
    }

    private fun findInstalledRegions(context: Context): List<String> {
        val regionIds = linkedSetOf<String>()
        val roots = listOf(
            context.filesDir.resolve("routing"),
            context.getExternalFilesDir(null)?.resolve("routing")
        )

        roots.filterNotNull().forEach { root ->
            root.listFiles { file ->
                file.isDirectory && !file.name.startsWith(".")
                    && !file.name.startsWith("tmp_")
                    && !file.name.startsWith("stream_")
                    && !file.name.startsWith("diag_")
            }?.sortedBy { it.name }?.forEach { regionIds.add(it.name) }
        }

        return regionIds.toList()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun requireContext(): Context = checkNotNull(appContext) {
        "NavigatorRuntime.initialize must be called before accessing shared state"
    }

    private val sharedAppHttpClient: OkHttpClient by lazy {
        OkHttpClient()
    }

    private val sharedMapTileClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(TileInterceptor())
            .build()
    }

    private val sharedGraphHopperEngine: GraphHopperEngine by lazy {
        GraphHopperEngine(requireContext())
    }

    private val sharedDatabase: AppDatabase by lazy {
        AppDatabase.getDatabase(requireContext())
    }

    private val sharedMapDownloadManager: MapDownloadManager by lazy {
        MapDownloadManager(requireContext())
    }

    private val sharedVehicleManager: VehicleManager by lazy {
        VehicleManager(requireContext())
    }

    private val sharedSearchService: SearchService by lazy {
        SearchService(sharedAppHttpClient)
    }

    private val sharedOpen511Service: Open511Service by lazy {
        Open511Service(sharedAppHttpClient)
    }
}