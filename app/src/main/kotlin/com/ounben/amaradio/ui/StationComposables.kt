package com.ounben.amaradio.ui

import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material.icons.filled.DragHandle
import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.ReorderableItem
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.zIndex
import android.content.ClipDescription
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.time.Duration.Companion.milliseconds
import android.content.ClipData
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.ounben.amaradio.R
import com.ounben.amaradio.data.DataCategory
import com.ounben.amaradio.history.TrackHistoryEntry
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.utils.EmojiUtils
import com.ounben.amaradio.utils.StationIconProvider
import com.ounben.amaradio.utils.StationPlaceholderUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import android.util.Log
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private const val MARQUEE_START_DELAY_MS = 3500

/** Splits "name·slogan" / "name slogan" into a fixed head and a scrolling tail. */
internal fun splitStationName(name: String): Pair<String, String> {
    val trimmed = name.trim()
    val dot = trimmed.indexOfFirst { it == '·' || it == '｜' || it == '|' }
    val idx = when {
        dot > 0 -> dot
        else -> {
            val sp = trimmed.indexOf(' ')
            // Space split only when the head is a real name (>=4 chars, not a code like "CNR-1")
            if (sp >= 4 && trimmed.substring(0, sp).none { it.isDigit() }) sp else -1
        }
    }
    if (idx <= 0 || idx >= trimmed.length - 1) return trimmed to ""
    val head = trimmed.substring(0, idx).trimEnd()
    val tail = trimmed.substring(idx + 1).trim()
    if (head.isEmpty() || tail.isEmpty()) return trimmed to ""
    return head to tail
}

/**
 * Station name where the name part stays fixed and only the slogan after
 * "·" / space scrolls. The head keeps its natural width (it only scrolls when it
 * cannot fit while leaving a sliver for the slogan); a name without a separator scrolls as a whole.
 * Uses a plain Layout (no subcomposition) so it is safe under IntrinsicSize.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationNameText(
    name: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    scroll: Boolean = false
) {
    // Marquee only while this station is the active one; everything else is
    // static and simply ellipsised, so a list at rest holds still.
    val marquee: Modifier = if (scroll) {
        Modifier.basicMarquee(
            iterations = Int.MAX_VALUE,
            initialDelayMillis = MARQUEE_START_DELAY_MS
        )
    } else Modifier
    val overflowMode = if (scroll) TextOverflow.Clip else TextOverflow.Ellipsis

    val (head, tail) = remember(name) { splitStationName(name) }
    if (tail.isEmpty()) {
        Text(
            text = head.ifEmpty { " " },
            style = style,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = overflowMode,
            modifier = modifier.then(marquee)
        )
    } else {
        Layout(
            modifier = modifier.clipToBounds(),
            content = {
                Text(
                    text = head,
                    style = style,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                    overflow = overflowMode,
                    modifier = marquee
                )
                Text(
                    text = "·",
                    style = style,
                    fontWeight = FontWeight.SemiBold,
                    color = color.copy(alpha = 0.55f),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Text(
                    text = tail,
                    style = style,
                    fontWeight = FontWeight.Normal,
                    color = color.copy(alpha = 0.75f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = overflowMode,
                    modifier = marquee
                )
            }
        ) { measurables, constraints ->
            val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE / 2
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val dotP = measurables[1].measure(loose)
            val headNatural = measurables[0].maxIntrinsicWidth(Int.MAX_VALUE)
            val minTail = (maxW * 0.18f).toInt()
            val headW = minOf(headNatural, maxW - dotP.width - minTail).coerceAtLeast(0)
            val headP = measurables[0].measure(loose.copy(minWidth = headW, maxWidth = headW))
            val tailW = (maxW - headP.width - dotP.width).coerceAtLeast(0)
            val tailP = measurables[2].measure(loose.copy(minWidth = tailW, maxWidth = tailW))
            val h = maxOf(headP.height, dotP.height, tailP.height)
            layout(maxW.coerceAtMost(headP.width + dotP.width + tailP.width), h) {
                headP.placeRelative(0, (h - headP.height) / 2)
                dotP.placeRelative(headP.width, (h - dotP.height) / 2)
                tailP.placeRelative(headP.width + dotP.width, (h - tailP.height) / 2)
            }
        }
    }
}

@Composable
fun StationIcon(
    stationName: String,
    stationUuid: String,
    iconUrl: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val placeholder = remember(stationName) { StationPlaceholderUtils.extractPlaceholder(stationName) }
    val placeholderColor = remember(stationUuid) { Color(StationPlaceholderUtils.getPlaceholderColor(stationUuid)) }
    
    // Resolve to a real image when one exists, else to null so the Compose
    // placeholder below draws instead of the provider's generated bitmap.
    val finalIconUri = remember(stationUuid, iconUrl) {
        val iconDir = File(context.filesDir, "station_icons")
        val iconFile = File(iconDir, "$stationUuid.jpg")

        if (!iconUrl.isNullOrBlank() && iconUrl.startsWith("file:/")) {
            android.net.Uri.parse(iconUrl)
        } else if (iconFile.exists() && iconFile.length() > 2048) {
            android.net.Uri.fromFile(iconFile)
        } else if (!iconUrl.isNullOrBlank() && iconUrl != "null" && iconUrl.startsWith("http")) {
            android.net.Uri.parse(iconUrl)
        } else {
            null
        }
    }
    
    var isLoading by remember(finalIconUri) { mutableStateOf(finalIconUri != null) }
    var isError by remember(finalIconUri) { mutableStateOf(false) }

    val imageRequest = remember(finalIconUri, stationUuid) {
        if (finalIconUri == null) null else {
            // Only a real remote artwork may be cached. A generated placeholder must
            // never be written to station_icons/, or it freezes in place as if it
            // were the station's own logo.
            val isRemoteArtwork = finalIconUri.scheme == "http" || finalIconUri.scheme == "https"
            ImageRequest.Builder(context)
                .data(finalIconUri)
                .size(512, 512)
                .allowHardware(false)
                .crossfade(true)
                .listener(
                    onSuccess = { _, result ->
                        val iconDir = File(context.filesDir, "station_icons")
                        val iconFile = File(iconDir, "$stationUuid.jpg")
                        if (stationUuid.isNotEmpty() && isRemoteArtwork) {
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    if (!iconDir.exists()) iconDir.mkdirs()
                                    val sourceBitmap = result.drawable.toBitmap(512, 512, android.graphics.Bitmap.Config.ARGB_8888)
                                    val whiteBitmap = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.ARGB_8888)
                                    val canvas = android.graphics.Canvas(whiteBitmap)
                                    canvas.drawColor(android.graphics.Color.WHITE)
                                    canvas.drawBitmap(sourceBitmap, 0f, 0f, null)
                                    FileOutputStream(iconFile).use { out ->
                                        whiteBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                                    }
                                } catch (_: Exception) { }
                            }
                        }
                    }
                )
                .build()
        }
    }

    val showPlaceholder = imageRequest == null || isLoading || isError

    Box(
        modifier = modifier
            .background(if (showPlaceholder) placeholderColor else Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (showPlaceholder) {
            StationPlaceholderContent(placeholder)
        }

        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onState = { state ->
                    isLoading = state is AsyncImagePainter.State.Loading
                    isError = state is AsyncImagePainter.State.Error
                }
            )
        }
    }
}

/**
 * Draws the generated station placeholder. Type sizes are derived from the measured
 * box so the same composable works at 40dp in the mini player and 168dp in the
 * full player; measuring via onSizeChanged keeps it safe under IntrinsicSize.
 */
@Composable
private fun StationPlaceholderContent(placeholder: StationPlaceholderUtils.Placeholder) {
    var boxSize by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val sideDp = with(density) { boxSize.toDp() }
    // The box is sized in dp but text is sized in sp, so a large font-scale setting
    // would overflow it. Dividing it out keeps the glyphs locked to the box.
    val fontUnit = sideDp.value / density.fontScale

    Box(
        modifier = Modifier.fillMaxSize().onSizeChanged { boxSize = minOf(it.width, it.height) },
        contentAlignment = Alignment.Center
    ) {
        if (boxSize == 0) return@Box
        when (placeholder) {
            is StationPlaceholderUtils.Placeholder.Frequency -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = sideDp * 0.08f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = placeholder.band,
                        color = Color.White,
                        fontSize = (fontUnit * 0.24f).sp,
                        lineHeight = (fontUnit * 0.26f).sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = placeholder.value,
                        color = Color.White,
                        fontSize = (fontUnit * 0.30f).sp,
                        lineHeight = (fontUnit * 0.32f).sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
            is StationPlaceholderUtils.Placeholder.TwoLine -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        // The flag badge sits over the bottom-right corner, so the
                        // lower line is inset to stay clear of it.
                        .padding(start = sideDp * 0.04f, end = sideDp * 0.16f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Both lines shrink as they get longer, so 4 characters still fit
                    // inside a 56dp list icon instead of being clipped.
                    val topSize = when {
                        placeholder.bottom.isEmpty() -> 0.40f
                        placeholder.top.length >= 3 -> 0.26f
                        else -> 0.32f
                    }
                    val bottomSize = when (placeholder.bottom.length) {
                        0, 1, 2 -> 0.22f
                        3 -> 0.19f
                        else -> 0.155f
                    }
                    Text(
                        text = placeholder.top,
                        color = Color.White,
                        fontSize = (fontUnit * topSize).sp,
                        lineHeight = (fontUnit * (topSize + 0.02f)).sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (placeholder.bottom.isNotEmpty()) {
                        Text(
                            text = placeholder.bottom,
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = (fontUnit * bottomSize).sp,
                            lineHeight = (fontUnit * (bottomSize + 0.02f)).sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
            is StationPlaceholderUtils.Placeholder.Single -> {
                Text(
                    text = placeholder.text,
                    color = Color.White,
                    fontSize = (fontUnit * 0.36f).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationListTemplate(
    stations: List<DataRadioStation>,
    isGrid: Boolean,
    isLoading: Boolean = false,
    error: String? = null,
    emptyMessage: String = "No stations found",
    onRetry: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onStationClick: (DataRadioStation) -> Unit,
    onFavoriteClick: (DataRadioStation) -> Unit,
    isFavorite: (String) -> Boolean,
    onDeleteClick: ((DataRadioStation) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = { onRefresh?.invoke() },
        modifier = modifier.fillMaxSize()
    ) {
        // Steering-wheel next/prev context: the list being browsed right now
        val handleStationClick: (DataRadioStation) -> Unit = { station ->
            com.ounben.amaradio.playback.PlaybackQueueHolder.setStations(stations)
            onStationClick(station)
        }
        if (isLoading && stations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AmaradioAmber)
            }
        } else if (error != null) {
            Column(
                modifier = Modifier.align(Alignment.Center).background(MaterialTheme.colorScheme.background),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = error, color = MaterialTheme.colorScheme.error)
                if (onRetry != null) {
                    Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Retry")
                    }
                }
            }
        } else if (stations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                Text(
                    text = emptyMessage,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            StationList(
                stations = stations,
                isGrid = isGrid,
                onStationClick = handleStationClick,
                onFavoriteClick = onFavoriteClick,
                isFavorite = isFavorite,
                onDeleteClick = onDeleteClick
            )
        }
    }
}

@Composable
fun StationList(
    stations: List<DataRadioStation>,
    isGrid: Boolean,
    onStationClick: (DataRadioStation) -> Unit,
    onFavoriteClick: (DataRadioStation) -> Unit,
    isFavorite: (String) -> Boolean,
    onDeleteClick: ((DataRadioStation) -> Unit)? = null,
    modifier: Modifier = Modifier,
    playerViewModel: PlayerViewModel = viewModel()
) {
    val playerUiState by playerViewModel.uiState.collectAsState()
    val playingStationUuid = playerUiState.currentStation?.StationUuid
    var stationWithOptions by remember { mutableStateOf<DataRadioStation?>(null) }

    if (isGrid) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(8.dp)
        ) {
            itemsIndexed(
                items = stations, 
                key = { _, station -> station.StationUuid },
                contentType = { _, _ -> "station" }
            ) { _, station ->
                StationGridItem(
                    station = station,
                    isFavorite = isFavorite(station.StationUuid),
                    isPlaying = station.StationUuid == playingStationUuid,
                    onClick = { onStationClick(station) },
                    onFavoriteClick = { onFavoriteClick(station) },
                    onLongClick = { stationWithOptions = station }
                )
            }
        }
    } else {
        LazyColumn(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            itemsIndexed(
                items = stations, 
                key = { _, station -> station.StationUuid },
                contentType = { _, _ -> "station" }
            ) { _, station ->
                StationListItem(
                    station = station,
                    isFavorite = isFavorite(station.StationUuid),
                    isPlaying = station.StationUuid == playingStationUuid,
                    onClick = { onStationClick(station) },
                    onFavoriteClick = { onFavoriteClick(station) },
                    onLongClick = { stationWithOptions = station }
                )
            }
        }
    }

    stationWithOptions?.let { station ->
        StationOptionsDialog(
            station = station,
            isFavorite = isFavorite(station.StationUuid),
            onFavoriteClick = { onFavoriteClick(station) },
            onDeleteClick = onDeleteClick?.let { { it(station) } },
            onDismiss = { stationWithOptions = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackList(
    tracks: LazyPagingItems<TrackHistoryEntry>,
    onTrackClick: (TrackHistoryEntry) -> Unit,
    onTrackLongClick: (TrackHistoryEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val isRefreshing = tracks.loadState.refresh is androidx.paging.LoadState.Loading
    
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { tracks.refresh() },
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(
                count = tracks.itemCount,
                key = { index -> 
                    val track = tracks.peek(index)
                    if (track != null) "${track.uid}_$index" else "placeholder_$index"
                },
                contentType = { "track" }
            ) { index ->
                tracks[index]?.let { track ->
                    TrackListItem(
                        track = track, 
                        onClick = { onTrackClick(track) },
                        onLongClick = { onTrackLongClick(track) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackListItem(
    track: TrackHistoryEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 8.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            StationIcon(
                stationName = track.stationName.ifEmpty { track.stationUuid },
                stationUuid = track.stationUuid,
                iconUrl = track.stationIconUrl,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            val displayTitle = track.track.ifBlank { track.title }
            val displayArtist = if (track.track.isNotBlank()) track.artist else ""

            Text(
                text = displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (displayArtist.isNotBlank() && displayArtist != displayTitle) {
                Text(
                    text = displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = track.getFormattedTime(context),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationListItem(
    station: DataRadioStation,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    useInternalClickable: Boolean = true,
    dragHandle: (@Composable (Modifier) -> Unit)? = null,
    isPlaying: Boolean = false
) {
    val flagEmoji = remember(station.CountryCode) { EmojiUtils.getFlagEmoji(station.CountryCode) ?: "" }

    val accessibilityDesc = stringResource(
        R.string.accessibility_station_description,
        station.Name,
        station.Language.ifEmpty { stringResource(R.string.not_applicable) },
        station.TagsAll.ifEmpty { stringResource(R.string.not_applicable) }
    )
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityDesc
            }
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isPlaying) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .padding(vertical = 8.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(
                    if (useInternalClickable) {
                        Modifier.combinedClickable(
                            onClick = onClick,
                            onLongClick = onLongClick
                        )
                    } else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(56.dp)) {
                StationIcon(
                    stationName = station.Name,
                    stationUuid = station.StationUuid,
                    iconUrl = station.IconUrl,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                )
                if (flagEmoji.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 4.dp, y = 4.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPlaying) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = flagEmoji,
                            fontSize = 15.sp,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            StationNameText(
                name = station.Name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                scroll = isPlaying
            )
        }

        if (dragHandle != null) {
            dragHandle(Modifier.fillMaxHeight())
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationGridItem(
    station: DataRadioStation,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    useInternalClickable: Boolean = true,
    dragHandle: (@Composable (Modifier) -> Unit)? = null,
    isPlaying: Boolean = false
) {
    val accessibilityDesc = stringResource(
        R.string.accessibility_station_description,
        station.Name,
        station.Language.ifEmpty { stringResource(R.string.not_applicable) },
        station.TagsAll.ifEmpty { stringResource(R.string.not_applicable) }
    )

    Card(
        modifier = modifier
            .padding(4.dp)
            .height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityDesc
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (useInternalClickable) {
                            Modifier.combinedClickable(
                                onClick = onClick,
                                onLongClick = onLongClick
                            )
                        } else Modifier
                    )
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StationIcon(
                    stationName = station.Name,
                    stationUuid = station.StationUuid,
                    iconUrl = station.IconUrl,
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(18.dp))
                )
                Text(
                    text = station.Name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 32.dp),
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }

            if (dragHandle != null) {
                Box(modifier = Modifier.align(Alignment.TopStart)) {
                    dragHandle(Modifier)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReorderableStationList(
    stations: List<DataRadioStation>,
    isGrid: Boolean,
    onStationClick: (DataRadioStation) -> Unit,
    onFavoriteClick: (DataRadioStation) -> Unit,
    isFavorite: (String) -> Boolean,
    onReorder: (List<DataRadioStation>) -> Unit,
    onDeleteClick: ((DataRadioStation) -> Unit)? = null,
    onLongClick: ((DataRadioStation) -> Unit)? = null,
    modifier: Modifier = Modifier,
    playerViewModel: PlayerViewModel = viewModel()
) {
    val playerUiState by playerViewModel.uiState.collectAsState()
    val playingStationUuid = playerUiState.currentStation?.StationUuid
    val stationsLocal = remember { mutableStateListOf<DataRadioStation>() }
    var stationWithOptions by remember { mutableStateOf<DataRadioStation?>(null) }
    
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(stations) {
        if (stationsLocal.toList() != stations) {
            stationsLocal.clear()
            stationsLocal.addAll(stations)
        }
    }

    if (isGrid) {
        val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
            stationsLocal.add(to.index, stationsLocal.removeAt(from.index))
            onReorder(stationsLocal.toList())
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(140.dp),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp)
        ) {
            itemsIndexed(stationsLocal, key = { _, s -> s.StationUuid }) { index, station ->
                ReorderableItem(reorderableState, key = station.StationUuid) { isDragging ->
                    val itemScope = this
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(2.dp)
                            .graphicsLayer {
                                scaleX = if (isDragging) 1.05f else 1f
                                scaleY = if (isDragging) 1.05f else 1f
                                alpha = if (isDragging) 0.8f else 1f
                            }
                    ) {
                        StationGridItem(
                            station = station,
                            isFavorite = isFavorite(station.StationUuid),
                            isPlaying = station.StationUuid == playingStationUuid,
                            onClick = { onStationClick(station) },
                            onFavoriteClick = { onFavoriteClick(station) },
                            onLongClick = { 
                                if (onLongClick != null) onLongClick.invoke(station) 
                                else stationWithOptions = station 
                            },
                            useInternalClickable = true,
                            dragHandle = { dragModifier ->
                                with(itemScope) {
                                    Box(
                                        modifier = dragModifier
                                            .size(48.dp)
                                            .draggableHandle(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DragHandle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    } else {
        val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
            stationsLocal.add(to.index, stationsLocal.removeAt(from.index))
            onReorder(stationsLocal.toList())
        }

        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            itemsIndexed(stationsLocal, key = { _, s -> s.StationUuid }) { index, station ->
                ReorderableItem(reorderableState, key = station.StationUuid) { isDragging ->
                    val itemScope = this
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = if (isDragging) 1.03f else 1f
                                scaleY = if (isDragging) 1.03f else 1f
                            },
                        shadowElevation = elevation,
                        tonalElevation = elevation
                    ) {
                        Column {
                            StationListItem(
                                station = station,
                                isFavorite = isFavorite(station.StationUuid),
                                isPlaying = station.StationUuid == playingStationUuid,
                                onClick = { onStationClick(station) },
                                onFavoriteClick = { onFavoriteClick(station) },
                                onLongClick = { 
                                    if (onLongClick != null) onLongClick.invoke(station) 
                                    else stationWithOptions = station 
                                },
                                useInternalClickable = true,
                                dragHandle = { dragModifier ->
                                    with(itemScope) {
                                        Box(
                                            modifier = dragModifier
                                                .width(56.dp)
                                                .draggableHandle(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DragHandle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    stationWithOptions?.let { station ->
        StationOptionsDialog(
            station = station,
            isFavorite = isFavorite(station.StationUuid),
            onFavoriteClick = { onFavoriteClick(station) },
            onDeleteClick = onDeleteClick?.let { { it(station) } },
            onDismiss = { stationWithOptions = null }
        )
    }
}

@Composable
fun CategoryList(
    categories: List<DataCategory>,
    isGrid: Boolean,
    onCategoryClick: (DataCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isGrid) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(100.dp),
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(8.dp)
        ) {
            itemsIndexed(
                items = categories,
                key = { index, category -> "${category.Name}_$index" },
                contentType = { _, _ -> "category" }
            ) { _, category ->
                CategoryGridItem(category = category, onClick = { onCategoryClick(category) })
            }
        }
    } else {
        LazyColumn(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            itemsIndexed(
                items = categories,
                key = { index, category -> "${category.Name}_$index" },
                contentType = { _, _ -> "category" }
            ) { _, category ->
                CategoryListItem(category = category, onClick = { onCategoryClick(category) })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
fun CategoryListItem(
    category: DataCategory,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryIcon(category = category, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = category.Name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (category.UsedCount > 0) {
            Text(
                text = category.UsedCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CategoryGridItem(
    category: DataCategory,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CategoryIcon(category = category, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = category.Name,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CategoryIcon(category: DataCategory, modifier: Modifier = Modifier) {
    val emoji = remember(category.Name) {
        if (category.Name.length == 2) EmojiUtils.getFlagEmoji(category.Name) else null
    }
    
    if (emoji != null) {
        Text(text = emoji, modifier = modifier, textAlign = TextAlign.Center)
    } else {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
