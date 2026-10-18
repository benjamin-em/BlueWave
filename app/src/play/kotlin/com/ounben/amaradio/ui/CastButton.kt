package com.ounben.amaradio.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.ContextThemeWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.ounben.amaradio.Utils
import com.ounben.amaradio.cast.CastManager

@Composable
fun CastButton(modifier: Modifier = Modifier) {
    CastButtonWithPermission(modifier = modifier)
}

@Suppress("DEPRECATION")
@Composable
fun CastButtonWithPermission(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    
    // 2. Determine required runtime permission based on Android API level
    val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.NEARBY_WIFI_DEVICES
    } else {
        Manifest.permission.ACCESS_FINE_LOCATION
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, requiredPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    // 4. Permission launcher using ActivityResultContracts.RequestPermission
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            CastManager.getInstance(context.applicationContext)?.ensureInitializedAsync(context.applicationContext)
        }
    }

    val systemDark = isSystemInDarkTheme()
    val isDark = when (Utils.getTheme(context)) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }

    val themeRes = if (isDark) {
        androidx.appcompat.R.style.Theme_AppCompat_NoActionBar
    } else {
        androidx.appcompat.R.style.Theme_AppCompat_Light_NoActionBar
    }

    Box(modifier = modifier.size(48.dp)) {
        // 1. Native AndroidX MediaRouteButton via AndroidView with CastButtonFactory
        AndroidView(
            factory = { ctx ->
                val themedContext = ContextThemeWrapper(ctx, themeRes)
                MediaRouteButton(themedContext).apply {
                    try {
                        CastButtonFactory.setUpMediaRouteButton(ctx, this)
                    } catch (_: Exception) {}
                    try {
                        setAlwaysVisible(true)
                    } catch (_: Exception) {}
                }
            },
            update = { button ->
                try {
                    button.setAlwaysVisible(true)
                } catch (_: Exception) {}
            },
            modifier = Modifier.matchParentSize()
        )

        // 3. & 5. Transparent click overlay active only while permission is missing
        if (!hasPermission) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        permissionLauncher.launch(requiredPermission)
                    }
            )
        }
    }
}
