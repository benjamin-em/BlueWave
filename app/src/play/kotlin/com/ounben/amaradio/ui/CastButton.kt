package com.ounben.amaradio.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.ContextThemeWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils
import com.ounben.amaradio.cast.CastManager

private const val TAG = "ama_cast_castbutton"

@Composable
fun CastButton(modifier: Modifier = Modifier) {
    CastButtonWithPermission(modifier)
}

@Composable
fun CastButtonWithPermission(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Android 17 uses a distinct LAN permission. Older Android versions do not
    // require location or Wi-Fi permissions for ordinary Cast discovery.
    val requiredPermission = if (Build.VERSION.SDK_INT >= 37) {
        Manifest.permission.ACCESS_LOCAL_NETWORK
    } else null
    fun permissionGranted() = requiredPermission == null ||
        ContextCompat.checkSelfPermission(context, requiredPermission) == PackageManager.PERMISSION_GRANTED
    var hasPermission by remember { mutableStateOf(permissionGranted()) }
    var showExplanation by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        showSettings = !granted
        if (granted) {
            CastManager.getInstance(context.applicationContext)?.ensureInitializedAsync(context.applicationContext)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = permissionGranted()
        if (hasPermission) {
            showSettings = false
            CastManager.getInstance(context.applicationContext)?.ensureInitializedAsync(context.applicationContext)
        }
    }
    val systemDark = isSystemInDarkTheme()
    val isDark = when (Utils.getTheme(context)) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    val themeRes = if (isDark) androidx.appcompat.R.style.Theme_AppCompat_NoActionBar
        else androidx.appcompat.R.style.Theme_AppCompat_Light_NoActionBar

    Box(modifier = modifier.size(48.dp)) {
        key(themeRes, hasPermission) {
            AndroidView(
                factory = { ctx ->
                    MediaRouteButton(ContextThemeWrapper(ctx, themeRes)).apply {
                        setAlwaysVisible(true)
                        if (hasPermission) {
                            try {
                                CastButtonFactory.setUpMediaRouteButton(ctx, this)
                            } catch (e: Exception) {
                                android.util.Log.e(TAG, "Cast route button setup failed", e)
                            }
                        }
                    }
                },
                modifier = Modifier.matchParentSize()
            )
        }
        if (!hasPermission) {
            Box(Modifier.matchParentSize().clickable(
                onClickLabel = stringResource(R.string.cast_network_title)
            ) { showExplanation = true })
        }
    }
    if (showExplanation || showSettings) {
        AlertDialog(
            onDismissRequest = { showExplanation = false; showSettings = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.cast_network_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(stringResource(if (showSettings) R.string.cast_network_denied else R.string.cast_network_reason), color = MaterialTheme.colorScheme.onSurface) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val openSettings = showSettings
                        showExplanation = false
                        showSettings = false
                        if (openSettings) {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}")))
                        } else requiredPermission?.let { permissionLauncher.launch(it) }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = AmaradioAmber)
                ) {
                    Text(
                        text = stringResource(if (showSettings) R.string.cast_network_settings else R.string.action_ok),
                        color = AmaradioAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExplanation = false; showSettings = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}
