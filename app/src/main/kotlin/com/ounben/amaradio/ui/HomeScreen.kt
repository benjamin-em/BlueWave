package com.ounben.amaradio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ounben.amaradio.R
import com.ounben.amaradio.station.DataRadioStation

/**
 * Home screen: vertically stacked groups (我的置顶 / 本市 / 本省 / 中央台 / 英语精选).
 * Each group shows a preview; "更多" expands it inline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    playerViewModel: PlayerViewModel,
    onStationClick: (DataRadioStation) -> Unit,
    onFavoriteClick: (DataRadioStation) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val playerUiState by playerViewModel.uiState.collectAsState()
    val playingUuid = playerUiState.currentStation?.StationUuid
    var stationWithOptions by remember { mutableStateOf<DataRadioStation?>(null) }

    PullToRefreshBox(
        isRefreshing = uiState.isLoading && uiState.groups.isEmpty(),
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        if (uiState.isLoading && uiState.groups.isEmpty()) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AmaradioAmber)
            }
        } else if (uiState.groups.isEmpty() || uiState.groups.all { it.stations.isEmpty() }) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.searchpreference_no_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.settings_home_location_desc),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                uiState.groups.forEach { group ->
                    if (group.stations.isEmpty()) return@forEach
                    val expanded = group.title in uiState.expandedGroups
                    val visible = if (expanded) group.stations else group.stations.take(HomeViewModel.GROUP_PREVIEW_SIZE)
                    val canExpand = group.stations.size > HomeViewModel.GROUP_PREVIEW_SIZE

                    item(key = "header_${group.title}") {
                        HomeGroupHeader(
                            title = group.title,
                            count = group.stations.size,
                            expanded = expanded,
                            canExpand = canExpand,
                            onToggle = { viewModel.toggleGroupExpanded(group.title) }
                        )
                    }
                    items(visible, key = { "${group.title}_${it.StationUuid}" }) { station ->
                        StationListItem(
                            station = station,
                            isFavorite = uiState.favoriteIds.contains(station.StationUuid),
                            isPlaying = station.StationUuid == playingUuid,
                            onClick = {
                                // Steering-wheel next/prev cycles this group
                                com.ounben.amaradio.playback.PlaybackQueueHolder.setStations(group.stations)
                                onStationClick(station)
                            },
                            onFavoriteClick = { onFavoriteClick(station) },
                            onLongClick = { stationWithOptions = station }
                        )
                    }
                    if (canExpand) {
                        item(key = "more_${group.title}") {
                            HomeGroupFooter(expanded = expanded, onToggle = { viewModel.toggleGroupExpanded(group.title) })
                        }
                    }
                }
            }
        }
    }

    stationWithOptions?.let { station ->
        StationOptionsDialog(
            station = station,
            isFavorite = uiState.favoriteIds.contains(station.StationUuid),
            onFavoriteClick = { onFavoriteClick(station) },
            onDismiss = { stationWithOptions = null }
        )
    }
}

@Composable
private fun HomeGroupHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    canExpand: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canExpand, onClick = onToggle)
            .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        if (canExpand) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp).size(20.dp)
            )
        }
    }
}

@Composable
private fun HomeGroupFooter(expanded: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(if (expanded) R.string.action_collapse else R.string.home_group_more),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
