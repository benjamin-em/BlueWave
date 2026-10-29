package com.ounben.amaradio.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import com.ounben.amaradio.R
import com.ounben.amaradio.history.TrackHistoryEntry
import com.ounben.amaradio.history.TrackHistoryViewModel
import com.ounben.amaradio.players.PlayState
import com.ounben.amaradio.utils.EmojiUtils
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
    viewModel: PlayerViewModel,
    isHeaderRole: Boolean,
    onToggleBottomSheet: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    
    val station = uiState.currentStation
    val liveInfo = uiState.liveInfo
    val stationName = station?.Name ?: ""
    
    val songTitle = if (liveInfo.track.isNotEmpty()) liveInfo.track else if (liveInfo.title.isNotEmpty()) liveInfo.title else ""
    val artistName = if (liveInfo.track.isNotEmpty()) liveInfo.artist else ""
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable { onToggleBottomSheet() }
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StationIcon(
                stationName = stationName,
                stationUuid = station?.StationUuid ?: "",
                iconUrl = station?.IconUrl,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Always 3 text rows so the height stays constant whatever metadata exists.
            Column(
                modifier = Modifier.weight(1f).wrapContentHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                StationNameText(
                    name = stationName.ifEmpty { " " },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = listOf(songTitle, artistName).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { " " },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(modifier = Modifier.wrapContentHeight().padding(top = 2.dp), contentAlignment = Alignment.CenterStart) {
                    PlayerStatusRow(
                        playState = uiState.playState,
                        bandwidthFlow = viewModel.bandwidth
                    )
                }
            }

            val isActive = uiState.playState == PlayState.Playing || uiState.playState == PlayState.PrePlaying
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { viewModel.togglePlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FullPlayer(
    playerViewModel: PlayerViewModel,
    trackHistoryViewModel: TrackHistoryViewModel,
    onTrackClick: (TrackHistoryEntry) -> Unit
) {
    val uiState by playerViewModel.uiState.collectAsState()
    val tracks = trackHistoryViewModel.allHistoryPaged.collectAsLazyPagingItems()
    val context = LocalContext.current
    var trackWithOptions by remember { mutableStateOf<TrackHistoryEntry?>(null) }
    
    val station = uiState.currentStation
    val liveInfo = uiState.liveInfo
    
    val songTitle = if (liveInfo.track.isNotEmpty()) liveInfo.track else if (liveInfo.title.isNotEmpty()) liveInfo.title else ""
    val artistName = if (liveInfo.track.isNotEmpty()) liveInfo.artist else ""

    val isActive = uiState.playState == PlayState.Playing || uiState.playState == PlayState.PrePlaying
    val isCompactPlayer by com.ounben.amaradio.CarModeManager.isCarMode.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.background
                    ),
                    endY = 1400f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StationIcon(
                stationName = station?.Name ?: "",
                stationUuid = station?.StationUuid ?: "",
                iconUrl = station?.IconUrl,
                modifier = Modifier
                    .size(if (isCompactPlayer) 96.dp else 168.dp)
                    .clip(RoundedCornerShape(if (isCompactPlayer) 22.dp else 32.dp))
            )

            Spacer(modifier = Modifier.height(if (isCompactPlayer) 8.dp else 18.dp))

            Text(
                text = (station?.Name ?: "").ifEmpty { " " },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOf(songTitle, artistName).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { " " },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )

            Box(modifier = Modifier.padding(top = 6.dp), contentAlignment = Alignment.Center) {
                if (isActive || uiState.playState == PlayState.Error) {
                    PlayerStatusRow(
                        playState = uiState.playState,
                        bandwidthFlow = playerViewModel.bandwidth,
                        showText = true
                    )
                } else if (station != null) {
                    val flagEmoji = remember(station.CountryCode) { EmojiUtils.getFlagEmoji(station.CountryCode) ?: "" }
                    if (flagEmoji.isNotEmpty()) {
                        Text(
                            text = flagEmoji,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.tab_player_history),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TrackList(
                tracks = tracks,
                onTrackClick = onTrackClick,
                onTrackLongClick = { trackWithOptions = it },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Control bar
        Surface(
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.ounben.amaradio.recorder.RecordButtonWithTimer(
                    currentStation = station,
                    iconSize = 28.dp
                )

                IconButton(onClick = { playerViewModel.skipToPrevious() }) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = null,
                        modifier = Modifier.size(38.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { playerViewModel.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }

                IconButton(onClick = { playerViewModel.skipToNext() }) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = null,
                        modifier = Modifier.size(38.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(onClick = { playerViewModel.toggleFavorite() }) {
                    Icon(
                        imageVector = if (uiState.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = if (uiState.isFavorite) AmaradioAmber else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    trackWithOptions?.let { track ->
        TrackOptionsDialog(
            track = track,
            onDismiss = { trackWithOptions = null }
        )
    }
}


@Composable
fun PlayerStatusRow(
    playState: PlayState,
    bandwidthFlow: StateFlow<String>,
    showText: Boolean = false
) {
    val color = when (playState) {
        PlayState.PrePlaying -> AmaradioAmber
        PlayState.Playing -> Color(0xFF2FBF71)
        PlayState.Error -> Color(0xFFF44336) // Red
        else -> Color.Transparent
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.wrapContentHeight()
    ) {
        if (playState == PlayState.PrePlaying || playState == PlayState.Playing) {
            val bandwidth by bandwidthFlow.collectAsState()
            Text(
                text = bandwidth.ifEmpty { "0.0 kB/s" },
                color = color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            
            if (showText && playState == PlayState.PrePlaying) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.notify_pre_play),
                    color = color,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        } else if (playState == PlayState.Error) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.error_station_load),
                color = color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        } else {
            // Placeholder to keep the 4th row height stable during Idle/Paused
            Text(
                text = " ",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
