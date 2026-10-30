package com.ounben.amaradio.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import com.ounben.amaradio.Utils
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import androidx.preference.PreferenceManager
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.R
import com.ounben.amaradio.players.PlayState
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.service.PlayerServiceUtil
import com.ounben.amaradio.history.TrackHistoryViewModel

sealed class Screen(val route: String, val titleRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Stations : Screen("stations", R.string.nav_item_stations, Icons.Default.Radio)
    object Favourites : Screen("starred", R.string.nav_item_starred, Icons.Default.Star)
    object History : Screen("history", R.string.nav_item_history, Icons.Default.History)
    object Settings : Screen("settings", R.string.nav_item_settings, Icons.Default.Settings)
    object About : Screen("about", R.string.settings_about, Icons.Default.Info)
    object Statistics : Screen("statistics", R.string.settings_statistics, Icons.Default.BarChart)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onSaveM3U: () -> Unit,
    onLoadM3U: () -> Unit,
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as AMARadioApp
    
    val mainViewModel: MainViewModel = viewModel()
    val playerViewModel: PlayerViewModel = viewModel()
    val trackHistoryViewModel: TrackHistoryViewModel = viewModel()
    val searchViewModel: SearchViewModel = viewModel()
    val serverInfoViewModel: ServerInfoViewModel = viewModel()
    
    val mainUiState by mainViewModel.uiState.collectAsState()
    val playerUiState by playerViewModel.uiState.collectAsState()
    val searchUiState by searchViewModel.uiState.collectAsState()
    val playerWarning by playerViewModel.warningMessage.collectAsState()
    
    // Observe player warnings
    LaunchedEffect(playerWarning) {
        playerWarning?.let { msgRes ->
            android.widget.Toast.makeText(context, msgRes, android.widget.Toast.LENGTH_LONG).show()
            playerViewModel.clearWarning()
        }
    }

    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showProxyDialog by remember { mutableStateOf(false) }
    var showPlayerSelectorDialog by remember { mutableStateOf<DataRadioStation?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<Int?>(null) }
    var isPlayerExpanded by remember { mutableStateOf(false) }

    val settingsViewModel: SettingsViewModel = viewModel()
    val settingsUiState by settingsViewModel.uiState.collectAsState()
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    // 1. Determine Initial Route (Startup Action)
    val startRoute = remember(settingsUiState.startupAction) {
        val action = settingsUiState.startupAction
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val lastRoute = sharedPref.getString("last_view_route", Screen.Stations.route) ?: Screen.Stations.route
        
        when (action) {
            "history" -> Screen.History.route
            "favorites" -> Screen.Favourites.route
            "stations" -> Screen.Stations.route
            "last" -> lastRoute
            else -> Screen.Stations.route
        }
    }

    // 2. Save Last View Route
    LaunchedEffect(currentRoute) {
        if (currentRoute != null && 
            currentRoute != Screen.About.route && 
            currentRoute != Screen.Statistics.route && 
            currentRoute != Screen.Settings.route) {
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
            sharedPref.edit {
                putString("last_view_route", currentRoute)
            }
        }
    }

    // Fix: Identify if we are in Settings sub-screens
    val isSettingsSubScreen = currentRoute == Screen.About.route || currentRoute == Screen.Statistics.route
    val isSettingsActive = currentRoute == Screen.Settings.route || isSettingsSubScreen

    BackHandler(enabled = isPlayerExpanded) { isPlayerExpanded = false }

    // Collapse if the station goes away, so the sheet cannot stay open over nothing.
    LaunchedEffect(playerUiState.currentStation) {
        if (playerUiState.currentStation == null) isPlayerExpanded = false
    }

    BackHandler(enabled = mainUiState.isSearching) {
        mainViewModel.setSearchActive(false)
        searchViewModel.clearResults()
    }

    val isCarMode by com.ounben.amaradio.CarModeManager.isCarMode.collectAsState()
    if (isCarMode) {
        CarModeScreen(playerViewModel = playerViewModel)
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            topBar = {
                MainTopBar(
                    isSearching = mainUiState.isSearching,
                    searchQuery = mainUiState.searchQuery,
                    isLoading = mainUiState.isLoading || searchUiState.isSearching,
                    onSearchQueryChange = { query -> 
                        mainViewModel.setSearchQuery(query)
                        searchViewModel.search(query)
                    },
                    onSearchToggle = { active -> 
                        if (active) isPlayerExpanded = false
                        mainViewModel.setSearchActive(active)
                        if (!active) searchViewModel.clearResults()
                    },
                    onFilterClick = {
                        isPlayerExpanded = false
                        // Tab layout: 0=Home, 1=Local, 2+=Filters
                        mainViewModel.setStationsInitialTab(2)
                        navController.navigate(Screen.Stations.route)
                    },
                    onSleepTimerClick = { showSleepTimerDialog = true },
                    onSaveClick = onSaveM3U,
                    onLoadClick = onLoadM3U,
                    onDeleteClick = { 
                        showDeleteConfirmDialog = if (currentRoute == Screen.Favourites.route) R.string.alert_delete_favorites else R.string.alert_delete_history 
                    },
                    onViewToggleClick = { mainViewModel.toggleGridView() },
                    isGridView = mainUiState.isGridView,
                    isDeleteVisible = currentRoute == Screen.Favourites.route || currentRoute == Screen.History.route,
                    deleteTitleRes = R.string.action_delete,
                    currentStation = playerUiState.currentStation,
                    onExportBackup = onExportBackup,
                    onImportBackup = onImportBackup
                )
            },
            bottomBar = {
                if (!mainUiState.isSearching) {
                    NavigationBar(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .height(64.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    ) {
                        val items = listOf(Screen.Stations, Screen.Favourites, Screen.History, Screen.Settings)
                        
                        items.forEach { screen ->
                            val isSelected = if (screen == Screen.Settings) isSettingsActive 
                                           else currentDestination?.hierarchy?.any { it.route == screen.route } == true

                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = stringResource(screen.titleRes)) },
                                selected = isSelected,
                                onClick = {
                                    if (screen == Screen.Settings && isSettingsSubScreen) {
                                        // Jump back to main settings if on sub-screen
                                        navController.popBackStack(Screen.Settings.route, false)
                                    } else {
                                        isPlayerExpanded = false
                                        if (screen == Screen.Stations) mainViewModel.setStationsInitialTab(0)
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                // Fixed bottom padding to avoid measurement loops during scrolling.
                // With no station loaded the mini player is gone, so the list uses the space.
                val hasStation = playerUiState.currentStation != null
                val contentBottomPadding = if (hasStation) 68.dp else 0.dp
                
                if (mainUiState.isSearching) {
                    Surface(
                        modifier = Modifier.fillMaxSize().padding(bottom = contentBottomPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        if (searchUiState.isSearching && searchUiState.results.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = AmaradioAmber)
                            }
                        } else if (searchUiState.results.isEmpty() && mainUiState.searchQuery.isNotEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
                                Text(stringResource(R.string.searchpreference_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            StationListTemplate(
                                stations = searchUiState.results,
                                isGrid = mainUiState.isGridView,
                                isLoading = searchUiState.isSearching,
                                error = searchUiState.error,
                                emptyMessage = stringResource(R.string.searchpreference_no_results),
                                onStationClick = { station ->
                                    if (sharedPrefHasExternalPlayer(context)) showPlayerSelectorDialog = station
                                    else playerViewModel.play(station)
                                    mainViewModel.setSearchActive(false)
                                    searchViewModel.clearResults()
                                },
                                onFavoriteClick = { station ->
                                    if (app.favouriteManager.has(station.StationUuid)) app.favouriteManager.remove(station.StationUuid)
                                    else app.favouriteManager.add(station)
                                },
                                isFavorite = { uuid -> searchUiState.favoriteIds.contains(uuid) }
                            )
                        }
                    }
                } else {
                    NavHost(
                        navController = navController,
                        startDestination = startRoute,
                        modifier = Modifier.fillMaxSize().padding(bottom = contentBottomPadding)
                    ) {
                        composable(Screen.Stations.route) {
                            TabsScreen(
                                initialTab = mainUiState.stationsInitialTab,
                                onStationClick = { station ->
                                    if (sharedPrefHasExternalPlayer(context)) showPlayerSelectorDialog = station
                                    else playerViewModel.play(station)
                                },
                                onFavoriteClick = { station ->
                                    if (app.favouriteManager.has(station.StationUuid)) app.favouriteManager.remove(station.StationUuid)
                                    else app.favouriteManager.add(station)
                                },
                                onCategoryClick = { }
                            )
                        }
                        composable(Screen.Favourites.route) {
                            val viewModel: LocalStationsViewModel = viewModel(key = "starred", factory = LocalStationsViewModelFactory(app, LocalManagerType.FAVOURITES))
                            StarredScreen(
                                viewModel = viewModel,
                                onStationClick = { station -> 
                                    if (sharedPrefHasExternalPlayer(context)) showPlayerSelectorDialog = station
                                    else playerViewModel.play(station)
                                },
                                onFavoriteClick = { station -> 
                                    if (app.favouriteManager.has(station.StationUuid)) app.favouriteManager.remove(station.StationUuid)
                                    else app.favouriteManager.add(station)
                                },
                                isFavorite = { uuid -> mainUiState.favoriteIds.contains(uuid) }
                            )
                        }
                        composable(Screen.History.route) {
                            val localViewModel: LocalStationsViewModel = viewModel(key = "history", factory = LocalStationsViewModelFactory(app, LocalManagerType.HISTORY))
                            HistoryScreen(
                                localStationsViewModel = localViewModel,
                                trackHistoryViewModel = trackHistoryViewModel,
                                onStationClick = { station -> 
                                    if (sharedPrefHasExternalPlayer(context)) showPlayerSelectorDialog = station
                                    else playerViewModel.play(station)
                                },
                                onFavoriteClick = { station -> 
                                    if (app.favouriteManager.has(station.StationUuid)) app.favouriteManager.remove(station.StationUuid)
                                    else app.favouriteManager.add(station)
                                },
                                onTrackClick = { },
                                isFavorite = { uuid -> mainUiState.favoriteIds.contains(uuid) }
                            )
                        }
                        composable(Screen.Settings.route) {
                            val viewModel: SettingsViewModel = viewModel()
                            SettingsScreen(
                                viewModel = viewModel,
                                serverInfoViewModel = serverInfoViewModel,
                                onOpenProxy = { showProxyDialog = true },
                                onOpenAbout = { 
                                    isPlayerExpanded = false
                                    navController.navigate(Screen.About.route) 
                                },
                                onOpenStatistics = { 
                                    isPlayerExpanded = false
                                    navController.navigate(Screen.Statistics.route) 
                                },
                                onOpenEqualizer = { 
                                    val intent = Intent("android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL")
                                    intent.putExtra("android.media.extra.PACKAGE_NAME", context.packageName)
                                    intent.putExtra("android.media.extra.AUDIO_SESSION", 0)
                                    try { context.startActivity(intent) } catch (_: Exception) {}
                                },
                                onBatteryOptimize = {
                                    Utils.openBatteryOrAppSettings(context)
                                },
                                onRateApp = {
                                    (context as? android.app.Activity)?.let {
                                        app.reviewManager.launchReviewFlow(it)
                                    }
                                },
                                onExportBackup = onExportBackup,
                                onImportBackup = onImportBackup
                            )
                        }
                        composable(Screen.About.route) { AboutScreen { navController.popBackStack() } }
                        composable(Screen.Statistics.route) { 
                            ServerInfoScreen(
                                viewModel = serverInfoViewModel,
                                currentPlayingUuid = playerUiState.currentStation?.StationUuid,
                                isPlaying = playerUiState.playState == PlayState.Playing,
                                onStationClick = { station ->
                                    if (sharedPrefHasExternalPlayer(context)) showPlayerSelectorDialog = station
                                    else playerViewModel.play(station)
                                },
                                onFavoriteClick = { station ->
                                    if (app.favouriteManager.has(station.StationUuid)) app.favouriteManager.remove(station.StationUuid)
                                    else app.favouriteManager.add(station)
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }

                // MiniPlayer / FullPlayer Container — only once a station is loaded
                if (hasStation) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isPlayerExpanded)
                                Modifier.fillMaxHeight()
                            else
                                Modifier.wrapContentHeight()
                        )
                        .align(Alignment.BottomCenter)
                        .then(
                            if (isPlayerExpanded) Modifier
                            else Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                        .clip(
                            if (isPlayerExpanded)
                                RoundedCornerShape(0.dp)
                            else RoundedCornerShape(20.dp)
                        )
                        .background(
                            if (isPlayerExpanded) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { /* ignore delta */ },
                            onDragStopped = { velocity ->
                                if (velocity < -500f) isPlayerExpanded = true
                                if (velocity > 500f) isPlayerExpanded = false
                            }
                        )
                ) {
                    if (isPlayerExpanded) {
                        Column {
                            Box(modifier = Modifier.fillMaxWidth().height(32.dp).clickable { isPlayerExpanded = false }, contentAlignment = Alignment.Center) {
                                Box(modifier = Modifier.size(40.dp, 4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(2.dp)))
                            }
                            FullPlayer(
                                playerViewModel = playerViewModel,
                                trackHistoryViewModel = trackHistoryViewModel,
                                onTrackClick = { }
                            )
                        }
                    } else {
                        MiniPlayer(
                            viewModel = playerViewModel,
                            isHeaderRole = false,
                            onToggleBottomSheet = { isPlayerExpanded = true },
                            onMoreClick = { }
                        )
                    }
                }
                }
            }
        }
    }

    if (showSleepTimerDialog) SleepTimerDialog(onDismiss = { showSleepTimerDialog = false })
    if (showProxyDialog) ProxySettingsDialogCompose(onDismiss = { showProxyDialog = false })
    showPlayerSelectorDialog?.let { station -> 
        PlayerSelectorDialogCompose(
            station = station, 
            playerViewModel = playerViewModel,
            onDismiss = { showPlayerSelectorDialog = null }
        ) 
    }
    showDeleteConfirmDialog?.let { msgRes ->
        val isDark = isSystemInDarkTheme()
        val deleteRedColor = if (isDark) SolidRedError else SolidRedErrorLight

        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = stringResource(R.string.action_delete),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = stringResource(msgRes),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (msgRes == R.string.alert_delete_favorites) app.favouriteManager.clear()
                        else app.historyManager.clear()
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = deleteRedColor)
                ) {
                    Text(
                        text = stringResource(R.string.yes),
                        fontWeight = FontWeight.Bold,
                        color = deleteRedColor
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Text(
                        text = stringResource(R.string.no),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

private fun sharedPrefHasExternalPlayer(context: Context): Boolean {
    return try {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).getBoolean("play_external", false)
    } catch (_: Exception) {
        false
    }
}
