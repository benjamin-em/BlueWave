package com.ounben.amaradio.ui

import android.content.Context
import android.telephony.TelephonyManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.launch

private sealed class MainTab {
    object Home : MainTab()
    object Local : MainTab()
    data class Filter(val index: Int, val id: String, val label: String) : MainTab()
}

@Composable
fun TabsScreen(
    initialTab: Int = 0,
    onStationClick: (DataRadioStation) -> Unit,
    onFavoriteClick: (DataRadioStation) -> Unit,
    onCategoryClick: (String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as AMARadioApp
    val countryCode = Utils.getCountryCode(context) ?: "DE"
    val filterViewModel: FilterViewModel = viewModel()
    val filterState by filterViewModel.uiState.collectAsState()
    val playerViewModel: PlayerViewModel = viewModel()
    val homeViewModel: HomeViewModel = viewModel()
    val coroutineScope = rememberCoroutineScope()

    // Build Dynamic Tab List (Guaranteed non-empty: Home tab always exists)
    val tabs = remember(countryCode, filterState.tabs) {
        mutableListOf<MainTab>().apply {
            add(MainTab.Home)
            add(MainTab.Local)
            filterState.tabs.forEachIndexed { index, tab ->
                add(MainTab.Filter(index, tab.id, tab.label))
            }
        }
    }

    val pagerState = rememberPagerState(
        initialPage = if (tabs.isEmpty()) 0 else initialTab.coerceIn(0, tabs.size - 1),
        pageCount = { tabs.size }
    )

    // Sync Pager -> ViewModel
    LaunchedEffect(pagerState.currentPage, tabs) {
        val currentTab = tabs.getOrNull(pagerState.currentPage)
        if (currentTab is MainTab.Filter) {
            filterViewModel.selectTab(currentTab.index)
        }
    }

    // Scroll to a newly added filter tab ONLY on explicit user request.
    // (Previously any tabs.size change auto-scrolled to the last page — that
    // fired during startup when tabs loaded from DB, dropping users on the
    // empty Filter page instead of Home.)
    val scrollToTab by filterViewModel.scrollToTab.collectAsState()
    LaunchedEffect(scrollToTab, tabs) {
        if (scrollToTab >= 0 && scrollToTab < filterState.tabs.size) {
            // +2 offset: Home and Local occupy pages 0 and 1
            pagerState.animateScrollToPage(scrollToTab + 2)
            filterViewModel.consumeScrollRequest()
        }
    }

    // Honor external navigation (e.g. top-bar filter button -> tab index 2+)
    var lastInitialTab by remember { mutableIntStateOf(initialTab) }
    LaunchedEffect(initialTab, tabs.size) {
        if (initialTab != lastInitialTab && tabs.isNotEmpty()) {
            pagerState.animateScrollToPage(initialTab.coerceIn(0, tabs.size - 1))
            lastInitialTab = initialTab
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(MaterialTheme.colorScheme.background),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (tabs.isNotEmpty()) {
                val safeSelectedIndex = remember(pagerState.currentPage, tabs.size) {
                    pagerState.currentPage.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
                }

                SecondaryScrollableTabRow(
                    selectedTabIndex = safeSelectedIndex,
                    modifier = Modifier.weight(1f).height(48.dp),
                    edgePadding = 12.dp,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicator = {},
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val selected = pagerState.currentPage == index
                        Tab(
                            selected = selected,
                            modifier = Modifier
                                .padding(horizontal = 3.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface
                                ),
                            selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = { 
                                val manualCountry = remember {
                                    val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                                    sp.getString("settings_manual_country_code", "SYSTEM") ?: "SYSTEM"
                                }
                                val title = when (tab) {
                                    is MainTab.Home -> stringResource(R.string.home_tab)
                                    is MainTab.Local -> {
                                        if (manualCountry.isNotEmpty() && manualCountry != "SYSTEM") {
                                            val flag = com.ounben.amaradio.utils.EmojiUtils.getFlagEmoji(manualCountry.uppercase()) ?: ""
                                            if (flag.isNotEmpty()) "$flag ${manualCountry.uppercase()}" else manualCountry.uppercase()
                                        } else {
                                            stringResource(R.string.action_local)
                                        }
                                    }
                                    is MainTab.Filter -> tab.label.ifBlank { "..." }
                                }
                                
                                Text(
                                    text = title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            if (filterState.tabs.size < 5) {
                IconButton(
                    onClick = { filterViewModel.addTab() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.accessibility_add_filter),
                        tint = AmaradioAmber
                    )
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0,
            verticalAlignment = Alignment.Top,
            key = { index ->
                val tab = tabs.getOrNull(index)
                when (tab) {
                    is MainTab.Home -> "home"
                    is MainTab.Local -> "local"
                    is MainTab.Filter -> "filter_${tab.id}"
                    null -> "empty_$index"
                }
            }
        ) { pageIndex ->
            if (pageIndex >= tabs.size) return@HorizontalPager

            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when (val tab = tabs[pageIndex]) {
                    is MainTab.Home -> {
                        HomeScreen(
                            viewModel = homeViewModel,
                            playerViewModel = playerViewModel,
                            onStationClick = onStationClick,
                            onFavoriteClick = onFavoriteClick
                        )
                    }
                    is MainTab.Local -> {
                        val stationsViewModel: StationsViewModel = viewModel(key = "local_stations")
                        StationsScreen(
                            viewModel = stationsViewModel,
                            url = "json/stations/bycountrycodeexact/$countryCode?order=clickcount&reverse=true",
                            onStationClick = onStationClick,
                            onFavoriteClick = { station ->
                                if (app.favouriteManager.has(station.StationUuid)) app.favouriteManager.remove(station.StationUuid)
                                else app.favouriteManager.add(station)
                            }
                        )
                    }
                    is MainTab.Filter -> {
                        FilterScreen(
                            viewModel = filterViewModel,
                            tabIndex = tab.index,
                            onStationClick = onStationClick,
                            onFavoriteClick = { station ->
                                if (app.favouriteManager.has(station.StationUuid)) app.favouriteManager.remove(station.StationUuid)
                                else app.favouriteManager.add(station)
                            }
                        )
                    }
                }
            }
        }
    }
}
