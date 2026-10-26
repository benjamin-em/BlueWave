package com.ounben.amaradio.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.CountryCodeDictionary
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils
import com.ounben.amaradio.data.DataCategory
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.utils.EmojiUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.preference.PreferenceManager
import androidx.core.content.edit
import android.content.SharedPreferences
import com.ounben.amaradio.database.AMARadioDatabase
import com.ounben.amaradio.database.TagCacheEntity
import com.ounben.amaradio.database.LanguageCacheEntity
import com.ounben.amaradio.database.user.AMARadioUserDatabase
import com.ounben.amaradio.database.user.FilterTabEntity
import com.ounben.amaradio.database.toDataStation
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

@Serializable
data class FilterTabItem(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "New Filter",
    val name: String = "",
    val countryCode: String = "",
    val countryLabel: String = "",
    val countryEmoji: String = "",
    val languageCode: String = "",
    val languageLabel: String = "",
    val tag: String = "",
    val tag2: String = "",
    val tag3: String = "",
    val tagOperator: String = "AND",
    val codec: String = "",
    val bitrate: Int = 0,
    val bitrateOperator: String = ">=",
    val sortBy: String = "clickcount",
    val reverse: Boolean = true,
    @Transient val stations: List<DataRadioStation> = emptyList()
)

fun FilterTabItem.toEntity(pos: Int) = FilterTabEntity(
    id = id, label = label, name = name, countryCode = countryCode,
    countryLabel = countryLabel, countryEmoji = countryEmoji,
    languageCode = languageCode, languageLabel = languageLabel,
    tag = tag, tag2 = tag2, tag3 = tag3, tagOperator = tagOperator,
    codec = codec, bitrate = bitrate, bitrateOperator = bitrateOperator,
    sortBy = sortBy, reverse = reverse, position = pos
)

fun FilterTabEntity.toItem() = FilterTabItem(
    id = id, label = label, name = name, countryCode = countryCode,
    countryLabel = countryLabel, countryEmoji = countryEmoji,
    languageCode = languageCode, languageLabel = languageLabel,
    tag = tag, tag2 = tag2, tag3 = tag3, tagOperator = tagOperator,
    codec = codec, bitrate = bitrate, bitrateOperator = bitrateOperator,
    sortBy = sortBy, reverse = reverse
)

class FilterViewModel(application: Application) : AndroidViewModel(application) {

    data class FilterUiState(
        val tabs: List<FilterTabItem> = emptyList(),
        val selectedTabIndex: Int = 0,
        val favoriteIds: Set<String> = emptySet(),
        val isSearching: Boolean = false,
        val countries: List<CategoryItem> = emptyList(),
        val languages: List<CategoryItem> = emptyList(),
        val tags: List<CategoryItem> = emptyList(),
        val error: String? = null,
        val isGrid: Boolean = false,
        val isLoadedFromDb: Boolean = false
    )

    data class CategoryItem(val code: String, val label: String, val emoji: String = "", val count: Int = 0)

    private val _uiState = MutableStateFlow(FilterUiState())
    val uiState: StateFlow<FilterUiState> = _uiState.asStateFlow()

    private val sharedPref = PreferenceManager.getDefaultSharedPreferences(application)
    private val app = application as AMARadioApp
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "icons_only_favorites_style") {
            refreshGridMode()
        } else if (key == "settings_language") {
            fetchMetadata()
        }
    }

    private var isFirstTabsLoad = true

    init {
        viewModelScope.launch {
            val userDb = AMARadioUserDatabase.getDatabase(app)
            userDb.filterTabDao().getAllTabsFlow().collect { entities ->
                val dbTabs = entities.map { it.toItem() }
                val currentTabs = _uiState.value.tabs
                
                // SMART MERGE: Preserve stations in memory ONLY if criteria haven't changed
                val mergedTabs = dbTabs.map { dbTab ->
                    // Normalize sort keys for comparison
                    val fixedSortBy = when (dbTab.sortBy) {
                        "lastchange", "lastchangetime" -> "LastChangeTime"
                        "name" -> "Name"
                        "votes" -> "Votes"
                        else -> dbTab.sortBy
                    }
                    val tab = if (fixedSortBy != dbTab.sortBy) dbTab.copy(sortBy = fixedSortBy) else dbTab
                    
                    val existing = currentTabs.find { it.id == tab.id }
                    val criteriaChanged = existing != null && (
                        existing.name != tab.name ||
                        existing.countryCode != tab.countryCode ||
                        existing.tag != tab.tag ||
                        existing.languageCode != tab.languageCode ||
                        existing.sortBy != tab.sortBy ||
                        existing.reverse != tab.reverse
                    )

                    if (existing != null && existing.stations.isNotEmpty() && !criteriaChanged) {
                        tab.copy(stations = existing.stations)
                    } else {
                        tab // Criteria changed or new tab: Drop old stations
                    }
                }

                val savedIndex = sharedPref.getInt("filter_selected_index", 0)
                
                // FALLBACK: Only if confirmed empty
                val finalTabs = if (mergedTabs.isEmpty()) {
                    listOf(FilterTabItem(label = "Filter"))
                } else {
                    mergedTabs
                }

                _uiState.update { it.copy(
                    tabs = finalTabs, 
                    selectedTabIndex = savedIndex.coerceIn(0, finalTabs.size - 1),
                    isLoadedFromDb = true
                ) }
                
                // Trigger initial search ONLY on first load
                if (isFirstTabsLoad) {
                    isFirstTabsLoad = false
                    finalTabs.forEachIndexed { index, tab ->
                        if (tab.stations.isEmpty() && (tab.name.isNotEmpty() || tab.countryCode.isNotEmpty() || tab.tag.isNotEmpty())) {
                            performSearch(index)
                        }
                    }
                }
            }
        }
        
        // REAKTIVE DATEN: Nur für Tags und Sprachen (SQL-Tabellen)
        viewModelScope.launch {
            val db = AMARadioDatabase.getDatabase(app)
            
            launch {
                db.tagCacheDao().getAllTagsFlow().collect { entities ->
                    if (entities.isNotEmpty()) {
                        _uiState.update { it.copy(tags = entities.map { CategoryItem(it.tagName, it.tagName, count = it.stationCount ?: 0) }) }
                    }
                }
            }

            launch {
                db.languageCacheDao().getAllLanguagesFlow().collect { entities ->
                    if (entities.isNotEmpty()) {
                        _uiState.update { it.copy(languages = entities.map { 
                            val label = it.languageName.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(Locale.ROOT) else c.toString() }
                            CategoryItem(it.languageName, label, count = it.stationCount ?: 0)
                        }.sortedBy { it.label }) }
                    }
                }
            }
        }

        // Initiales Laden
        viewModelScope.launch {
            delay(100.milliseconds)
            fetchMetadata()
        }
        
        viewModelScope.launch {
            app.favouriteManager.stationsFlow.collect { favorites ->
                val ids = favorites.map { it.StationUuid }.toSet()
                _uiState.update { it.copy(favoriteIds = ids) }
            }
        }

        refreshGridMode()
        sharedPref.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onCleared() {
        sharedPref.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    fun refreshGridMode() {
        val isGrid = sharedPref.getBoolean("icons_only_favorites_style", false)
        _uiState.update { it.copy(isGrid = isGrid) }
    }

    fun saveFilters() {
        val state = _uiState.value
        if (!state.isLoadedFromDb) return

        viewModelScope.launch(Dispatchers.IO) {
            val userDb = AMARadioUserDatabase.getDatabase(app)
            val entities = state.tabs.mapIndexed { index, item -> item.toEntity(index) }
            // ATOMIC UPDATE: Use the transaction method to prevent empty emission flickering
            userDb.filterTabDao().updateAllTabs(entities)
            
            sharedPref.edit {
                putInt("filter_selected_index", state.selectedTabIndex)
            }
        }
    }

    // Tab Management
    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTabIndex = index) }
        sharedPref.edit {
            putInt("filter_selected_index", index)
        }
    }

    fun addTab() {
        if (_uiState.value.tabs.size >= 5) return
        _uiState.update { 
            val newTabs = it.tabs + FilterTabItem(label = "")
            it.copy(tabs = newTabs, selectedTabIndex = newTabs.size - 1)
        }
        saveFilters()
    }

    fun removeTab(index: Int) {
        if (_uiState.value.tabs.size <= 1) return
        _uiState.update { 
            val newTabs = it.tabs.toMutableList().apply { removeAt(index) }
            val newIndex = if (it.selectedTabIndex >= newTabs.size) newTabs.size - 1 else it.selectedTabIndex
            it.copy(tabs = newTabs, selectedTabIndex = newIndex)
        }
        saveFilters()
    }

    fun updateTabLabel(index: Int, newLabel: String) {
        _uiState.update { state ->
            val newTabs = state.tabs.toMutableList()
            if (index in newTabs.indices) {
                newTabs[index] = newTabs[index].copy(label = newLabel)
            }
            state.copy(tabs = newTabs)
        }
        saveFilters()
    }

    // Indexed Filter Updates
    private fun updateTabAt(index: Int, update: (FilterTabItem) -> FilterTabItem) {
        _uiState.update { state ->
            val newTabs = state.tabs.toMutableList()
            if (index in newTabs.indices) {
                newTabs[index] = update(newTabs[index])
            }
            state.copy(tabs = newTabs)
        }
    }

    fun onNameChange(index: Int, newName: String) {
        updateTabAt(index) { it.copy(name = newName) }
        saveFilters()
    }
    
    fun onCountrySelect(index: Int, code: String, label: String) {
        updateTabAt(index) { it.copy(countryCode = code, countryLabel = label, countryEmoji = EmojiUtils.getFlagEmoji(code) ?: "") }
        saveFilters()
    }

    fun onMultiCountrySelect(index: Int, selectedItems: List<CategoryItem>) {
        val codes = selectedItems.joinToString(",") { it.code }
        val labels = selectedItems.joinToString(", ") { it.label }
        val emojis = selectedItems.mapNotNull { it.emoji.ifEmpty { EmojiUtils.getFlagEmoji(it.code) } }.joinToString(" ")
        updateTabAt(index) { 
            it.copy(
                countryCode = codes,
                countryLabel = labels,
                countryEmoji = emojis
            ) 
        }
        saveFilters()
    }
    
    fun clearCountry(index: Int) {
        updateTabAt(index) { it.copy(countryCode = "", countryLabel = "", countryEmoji = "") }
        saveFilters()
    }
    
    fun onLanguageSelect(index: Int, code: String, label: String) {
        updateTabAt(index) { it.copy(languageCode = code, languageLabel = label) }
        saveFilters()
    }
    
    fun clearLanguage(index: Int) {
        updateTabAt(index) { it.copy(languageCode = "", languageLabel = "") }
        saveFilters()
    }
    
    fun onTagSelect(index: Int, selectedTag: String) {
        updateTabAt(index) { it.copy(tag = selectedTag) }
        saveFilters()
    }

    fun onTag2Select(index: Int, selectedTag: String) {
        updateTabAt(index) { it.copy(tag2 = selectedTag) }
        saveFilters()
    }

    fun onTag3Select(index: Int, selectedTag: String) {
        updateTabAt(index) { it.copy(tag3 = selectedTag) }
        saveFilters()
    }

    fun onTagOperatorSelect(index: Int, operator: String) {
        updateTabAt(index) { it.copy(tagOperator = operator) }
        saveFilters()
    }

    fun clearTag(index: Int, slot: Int = 1) {
        updateTabAt(index) {
            when (slot) {
                1 -> it.copy(tag = "")
                2 -> it.copy(tag2 = "")
                3 -> it.copy(tag3 = "")
                else -> it.copy(tag = "")
            }
        }
        saveFilters()
    }

    fun onCodecSelect(index: Int, selectedCodec: String) {
        updateTabAt(index) { it.copy(codec = selectedCodec) }
        saveFilters()
    }

    fun clearCodec(index: Int) {
        updateTabAt(index) { it.copy(codec = "") }
        saveFilters()
    }

    fun onBitrateSelect(index: Int, selectedBitrate: Int) {
        updateTabAt(index) { it.copy(bitrate = selectedBitrate) }
        saveFilters()
    }

    fun onBitrateOperatorSelect(index: Int, operator: String) {
        updateTabAt(index) { it.copy(bitrateOperator = operator) }
        saveFilters()
    }

    fun clearBitrate(index: Int) {
        updateTabAt(index) { it.copy(bitrate = 0) }
        saveFilters()
    }
    
    fun onSortByChange(index: Int, newSort: String) {
        updateTabAt(index) { it.copy(sortBy = newSort, stations = emptyList()) }
        saveFilters()
        performSearch(index)
    }
    
    fun onReverseChange(index: Int, newReverse: Boolean) {
        updateTabAt(index) { it.copy(reverse = newReverse, stations = emptyList()) }
        saveFilters()
        performSearch(index)
    }

    fun fetchMetadata() {
        viewModelScope.launch {
            val db = AMARadioDatabase.getDatabase(app)
            
            // 1. LÄNDER: Direkt aus R.raw.radio_browser_country_cache laden (100% offline, kein Netzwerkaufruf)
            val countriesData = withContext(Dispatchers.IO) { loadRawResource(R.raw.radio_browser_country_cache) }
            val processedCountries = withContext(Dispatchers.Default) {
                countriesData.map { 
                    val label = CountryCodeDictionary.instance.getCountryByCode(it.Name) ?: it.Name
                    val emoji = EmojiUtils.getFlagEmoji(it.Name) ?: ""
                    CategoryItem(it.Name, label, emoji, count = it.UsedCount)
                }.sortedBy { it.label }
            }

            // 2. TAGS & SPRACHEN: Direkt aus R.raw laden wenn DB leer ist (100% offline, kein Netzwerkaufruf)
            val localTags = withContext(Dispatchers.IO) { db.tagCacheDao().getAllTags() }
            val localLanguages = withContext(Dispatchers.IO) { db.languageCacheDao().getAllLanguages() }

            if (localTags.isEmpty()) {
                val tagsData = withContext(Dispatchers.IO) { loadRawResource(R.raw.radio_browser_tag_cache) }
                if (tagsData.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        db.tagCacheDao().insertAll(tagsData.map { TagCacheEntity(it.Name, it.UsedCount, it.UsedCount) })
                    }
                }
            }

            if (localLanguages.isEmpty()) {
                val languagesData = withContext(Dispatchers.IO) { loadRawResource(R.raw.radio_browser_language_cache) }
                if (languagesData.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        db.languageCacheDao().insertAll(languagesData.map { LanguageCacheEntity(it.Name, it.UsedCount, it.UsedCount) })
                    }
                }
            }

            // UI-State nur für Länder sofort setzen (Tags/Sprachen kommen über Flows)
            _uiState.update { it.copy(countries = processedCountries) }
        }
    }

    private fun loadRawResource(resId: Int): List<DataCategory> {
        return try {
            val content = app.resources.openRawResource(resId).bufferedReader().use { it.readText() }.trim()
            val jsonToDecode = if (content.startsWith("{")) {
                val root = json.parseToJsonElement(content).jsonObject
                if (root.containsKey("rows")) root["rows"]?.jsonArray?.toString() ?: "[]" else "[$content]"
            } else content
            DataCategory.DecodeJson(jsonToDecode).toList()
        } catch (e: Exception) {
            Log.e("FilterViewModel", "Error loading raw resource $resId", e)
            emptyList()
        }
    }

    fun performSearch(index: Int) {
        viewModelScope.launch {
            // 1. Clear current stations immediately to force a full reload and show loading
            updateTabAt(index) { it.copy(stations = emptyList()) }
            _uiState.update { it.copy(isSearching = true, error = null) }
            
            val state = _uiState.value
            val tab = state.tabs.getOrNull(index) ?: return@launch
            val nameQuery = tab.name.trim()
            val queryWords = nameQuery.split(Regex("\\s+")).filter { it.isNotBlank() }

            // 2. Fetch from DB
            val localResults = withContext(Dispatchers.IO) {
                val dao = AMARadioDatabase.getDatabase(app).stationDao()
                dao.getStationsFiltered(
                    w1 = queryWords.getOrNull(0),
                    w2 = queryWords.getOrNull(1),
                    w3 = queryWords.getOrNull(2),
                    countryCode = tab.countryCode.ifEmpty { null },
                    language = tab.languageCode.ifEmpty { null },
                    tag1 = tab.tag.ifEmpty { null },
                    tag2 = tab.tag2.ifEmpty { null },
                    tag3 = tab.tag3.ifEmpty { null },
                    tagOperator = tab.tagOperator,
                    codec = tab.codec.ifEmpty { null },
                    bitrate = tab.bitrate,
                    bitrateOperator = tab.bitrateOperator,
                    orderBy = tab.sortBy,
                    reverse = if (tab.reverse) 1 else 0
                )
            }

            val decoded = withContext(Dispatchers.Default) {
                val list = localResults.map { it.toDataStation() }
                
                // Define the primary comparator based on user preference
                val baseComparator = when (tab.sortBy) {
                    "Name" -> compareBy<DataRadioStation> { it.Name.trim().lowercase(Locale.ROOT) }
                    "Votes" -> compareBy<DataRadioStation> { it.Votes }
                    "clickcount" -> compareBy<DataRadioStation> { it.ClickCount }
                    "LastChangeTime" -> compareBy<DataRadioStation> { it.LastChangeTime }
                    else -> compareBy<DataRadioStation> { it.ClickCount }
                }
                
                val userComparator = if (tab.reverse) baseComparator.reversed() else baseComparator

                // PURE SORT: In Filter Tabs, we respect the user's sort choice strictly.
                // We don't use Search relevance weighting here to avoid "jumping" items.
                // Secondary sort is always 'clickcount' (if sorting by name) or 'Name' (otherwise).
                val finalComparator = if (tab.sortBy == "Name") {
                    userComparator.thenByDescending { it.ClickCount }
                } else {
                    userComparator.thenBy { it.Name.trim().lowercase(Locale.ROOT) }
                }

                list.sortedWith(finalComparator)
            }

            // 3. Atomic update to ensure we don't overwrite other tab changes
            updateTabAt(index) { it.copy(stations = decoded) }
            _uiState.update { it.copy(isSearching = false) }
        }
    }
}
