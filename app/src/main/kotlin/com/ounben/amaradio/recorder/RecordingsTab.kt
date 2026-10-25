package com.ounben.amaradio.recorder

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ounben.amaradio.R
import com.ounben.amaradio.database.user.AMARadioUserDatabase
import com.ounben.amaradio.database.user.RecordEntity
import com.ounben.amaradio.ui.AmaradioAmber
import com.ounben.amaradio.ui.SolidRedError
import com.ounben.amaradio.ui.StationIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordingsTab() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AMARadioUserDatabase.getDatabase(context) }
    val recordings by db.recordDao().getAllRecordingsFlow().collectAsState(initial = emptyList())
    var recordingToDelete by remember { mutableStateOf<RecordEntity?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (recordings.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No recordings found.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(recordings, key = { it.id }) { record ->
                    Column {
                        RecordingListItem(
                            record = record,
                            onPlay = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(record.fileUri), "audio/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    android.widget.Toast.makeText(context, "Cannot open recording file", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDelete = { recordingToDelete = record }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }

    recordingToDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { recordingToDelete = null },
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
                    text = "Delete recording: ${record.stationName}?",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                context.contentResolver.delete(Uri.parse(record.fileUri), null, null)
                            } catch (_: Exception) {}
                            db.recordDao().deleteRecord(record.id)
                            withContext(Dispatchers.Main) { recordingToDelete = null }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = SolidRedError)
                ) {
                    Text(
                        text = stringResource(R.string.yes),
                        fontWeight = FontWeight.Bold,
                        color = SolidRedError
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { recordingToDelete = null }) {
                    Text(
                        text = stringResource(R.string.no),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

@Composable
fun RecordingListItem(
    record: RecordEntity,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AMARadioUserDatabase.getDatabase(context) }
    var expanded by remember { mutableStateOf(false) }
    val tracks by db.recordDao().getTracksForRecordingFlow(record.id).collectAsState(initial = emptyList())

    val dateStr = remember(record.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(record.timestamp))
    }
    val durationStr = remember(record.durationMs) { formatDuration(record.durationMs) }
    val sizeStr = remember(record.fileSizeBytes) { Formatter.formatShortFileSize(context, record.fileSizeBytes) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                StationIcon(
                    stationName = record.stationName,
                    stationUuid = record.stationUuid,
                    iconUrl = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.stationName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$dateStr • $durationStr • $sizeStr (${record.format.uppercase()})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                if (tracks.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .clickable { expanded = !expanded }
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tracks (${tracks.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmaradioAmber,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = AmaradioAmber,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPlay) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = AmaradioAmber,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = SolidRedError,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        AnimatedVisibility(visible = expanded && tracks.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 64.dp, top = 6.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tracks.forEach { track ->
                    Text(
                        text = "${formatDuration(track.offsetMs)} - ${track.trackTitle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    val hours = ms / (1000 * 60 * 60)
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
