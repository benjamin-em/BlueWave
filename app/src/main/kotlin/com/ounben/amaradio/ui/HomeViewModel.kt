package com.ounben.amaradio.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils
import com.ounben.amaradio.database.AMARadioDatabase
import com.ounben.amaradio.database.toDataStation
import com.ounben.amaradio.geo.ChinaProvinces
import com.ounben.amaradio.geo.CuratedStations
import com.ounben.amaradio.geo.HomeLocation
import com.ounben.amaradio.geo.HomeLocationResolver
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home screen: geographic + curated groups.
 * Groups: 我的置顶 / 本市 / 本省 / 中央台 / 英语精选.
 * Data comes from the local catalog DB (offline-first); curated lists
 * fall back to the radio-browser API for UUIDs missing locally.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    data class StationGroup(
        val title: String,
        val stations: List<DataRadioStation>
    )

    data class HomeUiState(
        val groups: List<StationGroup> = emptyList(),
        val favoriteIds: Set<String> = emptySet(),
        val pinnedIds: Set<String> = emptySet(),
        val expandedGroups: Set<String> = emptySet(),
        val isLoading: Boolean = true,
        val location: HomeLocation? = null,
        val isGrid: Boolean = false,
        val error: String? = null
    )

    companion object {
        private const val TAG = "HomeViewModel"
        const val GROUP_PREVIEW_SIZE = 10
        private const val GROUP_MAX_SIZE = 100

        /** Provincial-station brand names that do not contain the province name. */
        private val provinceBrands = mapOf(
            "湖北" to listOf("楚天", "鄂"),
            "湖南" to listOf("芒果"),
            "广东" to listOf("南方"),
            "江苏" to listOf("江苏"),
            "四川" to listOf("蜀"),
            "山东" to listOf("齐鲁"),
            "河南" to listOf("中原"),
            "陕西" to listOf("秦"),
            "山西" to listOf("三晋")
        )
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val app = application as AMARadioApp
    private val sharedPref = PreferenceManager.getDefaultSharedPreferences(application)

    /** Last resolved location used for loading; reload when it changes. */
    private var loadedLocation: HomeLocation? = null

    init {
        // Live-update the pinned group and pin-state from the manager flows
        viewModelScope.launch {
            app.homePinManager.stationsFlow.collect { pins ->
                _uiState.update { it.copy(pinnedIds = pins.map { p -> p.StationUuid }.toSet()) }
            }
        }
        viewModelScope.launch {
            app.favouriteManager.stationsFlow.collect { favorites ->
                _uiState.update { it.copy(favoriteIds = favorites.map { f -> f.StationUuid }.toSet()) }
            }
        }
        refreshGridMode()
        // Location prefs may change while this VM is alive (settings screen);
        // also retry auto-location until a fix is cached.
        viewModelScope.launch {
            while (true) {
                val loc = HomeLocationResolver.resolve(app)
                if (loc != loadedLocation) {
                    loadedLocation = loc
                    reload(loc)
                }
                delay(3000)
            }
        }
    }

    fun refreshGridMode() {
        val isGrid = sharedPref.getBoolean("icons_only_favorites_style", false)
        _uiState.update { it.copy(isGrid = isGrid) }
    }

    /** Force a full reload (pull-to-refresh). */
    fun refresh() {
        val loc = loadedLocation ?: HomeLocationResolver.resolve(app)
        loadedLocation = loc
        reload(loc)
    }

    private fun reload(loc: HomeLocation) {
        _uiState.update { it.copy(isLoading = true, location = loc, error = null) }
        viewModelScope.launch {
            try {
                val groups = buildGroups(loc)
                _uiState.update { it.copy(groups = groups, isLoading = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to build home groups", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private suspend fun buildGroups(loc: HomeLocation): List<StationGroup> {
        val context = getApplication<Application>()
        val catalogDao = AMARadioDatabase.getDatabase(app).stationDao()
        val groups = mutableListOf<StationGroup>()

        // Geographic groups (CN only). If we still have no province, try a
        // fresh coarse-location read once — the result gets cached in prefs.
        var effectiveLoc = loc
        if (loc.isChina && loc.province == null) {
            HomeLocationResolver.refreshAutoLocation(context)?.let { effectiveLoc = it }
        }

        var cityStations: List<DataRadioStation> = emptyList()
        var provinceStations: List<DataRadioStation> = emptyList()
        var provinceTitle: String? = null
        var cityTitle: String? = null

        if (effectiveLoc.isChina && effectiveLoc.province != null) {
            val province = ChinaProvinces.byKey[effectiveLoc.province]
            if (province != null) {
                provinceTitle = province.key
                val aliases = (province.aliases + province.key).map { it.lowercase() }.distinct()
                val allProvince = withContext(Dispatchers.IO) {
                    catalogDao.getCnStationsForProvince(aliases, province.key, GROUP_MAX_SIZE * 3)
                }.map { it.toDataStation() }

                // City/county level: name matches the city or one of its districts
                val keywords = ChinaProvinces.cityKeywordsFor(province.key, effectiveLoc.city)
                cityStations = if (keywords.isNotEmpty()) {
                    allProvince.filter { st -> keywords.any { kw -> st.Name.contains(kw, ignoreCase = true) } }
                } else emptyList()
                cityTitle = keywords.firstOrNull()

                // Provincial level: name carries the province name / a provincial brand and
                // does not belong to another city. Other cities' own stations are left out.
                val cityUuids = cityStations.map { it.StationUuid }.toSet()
                val brands = listOf(province.key) + (provinceBrands[province.key] ?: emptyList())
                val otherCities = ChinaProvinces.otherCityKeywords(province.key, effectiveLoc.city)
                    .filter { it != province.key }
                provinceStations = allProvince.filter { st ->
                    st.StationUuid !in cityUuids &&
                        brands.any { st.Name.contains(it, ignoreCase = true) } &&
                        otherCities.none { st.Name.contains(it, ignoreCase = true) }
                }
            }
        }

        // Assemble in display order; skip empty geographic groups.
        val pinned = app.homePinManager.getList()
        groups.add(StationGroup(context.getString(R.string.home_group_pinned), pinned))
        if (cityStations.isNotEmpty() && cityTitle != null) {
            groups.add(StationGroup(cityTitle.removeSuffix("市"), cityStations))
        }
        if (provinceStations.isNotEmpty() && provinceTitle != null) {
            groups.add(StationGroup(provinceTitle, provinceStations))
        }
        if (effectiveLoc.isChina) {
            val central = resolveCurated(CuratedStations.centralCn)
            if (central.isNotEmpty()) groups.add(StationGroup(context.getString(R.string.home_group_central), central))
        }
        val english = resolveCurated(CuratedStations.englishWorld)
        if (english.isNotEmpty()) groups.add(StationGroup(context.getString(R.string.home_group_english), english))

        return groups
    }

    /**
     * Resolve curated UUID lists against the local catalog; missing entries
     * are fetched from the radio-browser API (best effort).
     */
    private suspend fun resolveCurated(uuids: List<String>): List<DataRadioStation> {
        if (uuids.isEmpty()) return emptyList()
        val catalogDao = AMARadioDatabase.getDatabase(app).stationDao()
        val local = withContext(Dispatchers.IO) { catalogDao.getStationsByUuids(uuids) }
        val localByUuid = local.associateBy { it.stationUuid }
        val missing = uuids.filter { it !in localByUuid }

        var fetchedByUuid: Map<String, DataRadioStation> = emptyMap()
        if (missing.isNotEmpty()) {
            fetchedByUuid = try {
                withContext(Dispatchers.IO) {
                    Utils.getStationsByUuid(app.httpClient, app, missing)
                }?.associateBy { it.StationUuid } ?: emptyMap()
            } catch (e: Exception) {
                Log.w(TAG, "Curated fetch failed for ${missing.size} uuids", e)
                emptyMap()
            }
        }

        // Preserve curated order
        return uuids.mapNotNull { uuid ->
            localByUuid[uuid]?.toDataStation() ?: fetchedByUuid[uuid]
        }
    }

    fun toggleGroupExpanded(title: String) {
        _uiState.update { state ->
            val expanded = state.expandedGroups.toMutableSet()
            if (!expanded.remove(title)) expanded.add(title)
            state.copy(expandedGroups = expanded)
        }
    }
}
