package com.ounben.amaradio.ui

import android.content.res.Configuration
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.CarModeManager
import com.ounben.amaradio.R
import com.ounben.amaradio.playback.PlaybackQueueHolder
import com.ounben.amaradio.players.PlayState
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.delay
import java.util.Date

private const val PRESET_COUNT = 6
private const val DIM_BRIGHTNESS = 0.3f

// Fixed palette: the car screen is always dark so it never glares at night
// and never changes look with the phone theme.
private val CarBg = Color(0xFF050A14)
private val CarSurface = Color(0xFF111B2E)
private val CarSurfaceActive = Color(0xFF123A73)
private val CarText = Color(0xFFF2F6FF)
private val CarTextDim = Color(0xFF8FA0BF)
private val CarAccent = Color(0xFF3D9BFF)
private val CarWarn = Color(0xFFFFB454)

/**
 * Glanceable driving UI. One screen, nothing scrolls, nothing opens a sub-menu,
 * every action is a single tap on a large target.
 */
@Composable
fun CarModeScreen(playerViewModel: PlayerViewModel) {
    val context = LocalContext.current
    val app = context.applicationContext as AMARadioApp
    val uiState by playerViewModel.uiState.collectAsState()
    val favourites by app.favouriteManager.stationsFlow.collectAsState()
    val history by app.historyManager.stationsFlow.collectAsState()

    KeepScreenOnDimmed()
    BackHandler { CarModeManager.setMode(context, CarModeManager.MODE_OFF) }

    // Favourites first, topped up with recent stations, no duplicates. Stable order
    // so a given button always means the same station (muscle memory).
    val presets = remember(favourites, history) {
        (favourites + history)
            .distinctBy { it.StationUuid }
            .distinctBy { carName(it.Name) }
            .take(PRESET_COUNT)
    }

    val station = uiState.currentStation
    val playState = uiState.playState
    val isActive = playState == PlayState.Playing || playState == PlayState.PrePlaying

    val exit = { CarModeManager.setMode(context, CarModeManager.MODE_OFF) }
    val onPick: (DataRadioStation) -> Unit = { picked ->
        PlaybackQueueHolder.setStations(presets)
        playerViewModel.play(picked)
    }
    val playingUuid = station?.StationUuid.takeIf { isActive }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (landscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(CarBg)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TopStrip(onExit = exit)
                Hero(
                    station = station,
                    playState = playState,
                    trackLine = trackLine(uiState),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                TransportRow(
                    isActive = isActive,
                    onPrev = { playerViewModel.skipToPrevious() },
                    onToggle = { playerViewModel.togglePlayPause() },
                    onNext = { playerViewModel.skipToNext() },
                    modifier = Modifier.height(96.dp).fillMaxWidth()
                )
            }
            PresetGrid(
                presets = presets,
                playingUuid = playingUuid,
                onPick = onPick,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CarBg)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TopStrip(onExit = exit)
            Hero(
                station = station,
                playState = playState,
                trackLine = trackLine(uiState),
                modifier = Modifier.weight(1.1f).fillMaxWidth()
            )
            TransportRow(
                isActive = isActive,
                onPrev = { playerViewModel.skipToPrevious() },
                onToggle = { playerViewModel.togglePlayPause() },
                onNext = { playerViewModel.skipToNext() },
                modifier = Modifier.weight(0.9f).fillMaxWidth()
            )
            PresetGrid(
                presets = presets,
                playingUuid = playingUuid,
                onPick = onPick,
                modifier = Modifier.weight(1.3f).fillMaxWidth()
            )
        }
    }
}

/** Drivers need the name, not the slogan: "武汉经典音乐广播·岁月悠长" -> "武汉经典音乐广播". */
private fun carName(name: String): String = splitStationName(name).first

private fun trackLine(state: PlayerViewModel.PlayerUiState): String {
    val info = state.liveInfo
    val title = if (info.track.isNotEmpty()) info.track else info.title
    val artist = if (info.track.isNotEmpty()) info.artist else ""
    return listOf(title, artist).filter { it.isNotEmpty() }.joinToString(" · ")
}

@Composable
private fun TopStrip(onExit: () -> Unit) {
    val context = LocalContext.current
    val is24h = remember { DateFormat.is24HourFormat(context) }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(15_000)
        }
    }
    val clock = remember(now) {
        DateFormat.format(if (is24h) "HH:mm" else "h:mm", now).toString()
    }

    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = clock,
            color = CarTextDim,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(CarSurface)
                .clickable(onClick = onExit),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.car_exit),
                tint = CarTextDim,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun Hero(
    station: DataRadioStation?,
    playState: PlayState,
    trackLine: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = station?.Name?.let { carName(it) }?.ifBlank { null } ?: stringResource(R.string.car_no_station),
            color = CarText,
            fontSize = 40.sp,
            lineHeight = 46.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val (statusText, statusColor) = when (playState) {
            PlayState.PrePlaying -> stringResource(R.string.car_connecting) to CarAccent
            PlayState.Error -> stringResource(R.string.car_error) to CarWarn
            else -> trackLine to CarTextDim
        }
        if (statusText.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = statusText,
                color = statusColor,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TransportRow(
    isActive: Boolean,
    onPrev: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TransportButton(
            icon = Icons.Default.SkipPrevious,
            description = stringResource(R.string.car_prev),
            onClick = onPrev,
            container = CarSurface,
            tint = CarText,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
        TransportButton(
            icon = if (isActive) Icons.Default.Pause else Icons.Default.PlayArrow,
            description = stringResource(if (isActive) R.string.car_pause else R.string.car_play),
            onClick = onToggle,
            container = CarAccent,
            tint = CarBg,
            modifier = Modifier.weight(1.5f).fillMaxHeight()
        )
        TransportButton(
            icon = Icons.Default.SkipNext,
            description = stringResource(R.string.car_next),
            onClick = onNext,
            container = CarSurface,
            tint = CarText,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
    }
}

@Composable
private fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    container: Color,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(64.dp)
        )
    }
}

@Composable
private fun PresetGrid(
    presets: List<DataRadioStation>,
    playingUuid: String?,
    onPick: (DataRadioStation) -> Unit,
    modifier: Modifier = Modifier
) {
    if (presets.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.car_empty_hint),
                color = CarTextDim,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    // Always lay out PRESET_COUNT slots (2 columns x 3 rows) so positions never shift.
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in 0 until PRESET_COUNT / 2) {
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (col in 0 until 2) {
                    val station = presets.getOrNull(row * 2 + col)
                    if (station == null) {
                        Spacer(Modifier.weight(1f).fillMaxHeight())
                    } else {
                        PresetTile(
                            station = station,
                            selected = station.StationUuid == playingUuid,
                            onClick = { onPick(station) },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetTile(
    station: DataRadioStation,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) CarSurfaceActive else CarSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = carName(station.Name),
            color = if (selected) CarText else CarText.copy(alpha = 0.92f),
            fontSize = 22.sp,
            lineHeight = 26.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Keeps the screen awake while in car mode, dimmed so it does not glare; restores on exit. */
@Composable
private fun KeepScreenOnDimmed() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        val previous = window?.attributes?.screenBrightness
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window?.attributes = window?.attributes?.apply { screenBrightness = DIM_BRIGHTNESS }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.attributes = window?.attributes?.apply {
                screenBrightness = previous ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }
}
