package com.ounben.amaradio.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.ounben.amaradio.R
import com.ounben.amaradio.geo.ChinaCities
import com.ounben.amaradio.geo.ChinaProvinces
import com.ounben.amaradio.geo.HomeLocation
import com.ounben.amaradio.geo.HomeLocationResolver
import kotlinx.coroutines.launch

/**
 * "所在地区" settings row: shows the current resolution (manual/auto),
 * lets the user pick province+city manually (manual wins), or trigger a
 * one-off coarse-location read.
 */
@Composable
fun HomeLocationSettings(
    viewModel: SettingsViewModel,
    uiState: SettingsViewModel.SettingsUiState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showProvinceDialog by remember { mutableStateOf(false) }
    var showCityDialog by remember { mutableStateOf(false) }
    var autoResult by remember { mutableStateOf<HomeLocation?>(null) }
    var autoBusy by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            autoBusy = true
            scope.launch {
                autoResult = HomeLocationResolver.refreshAutoLocation(context)
                autoBusy = false
            }
        }
    }

    val current = HomeLocationResolver.resolve(context)
    val summary = when {
        current.province != null && current.isManual ->
            stringResource(R.string.settings_home_location_manual) + ": " +
                current.province + (current.city?.let { " · $it" } ?: "")
        current.province != null ->
            stringResource(R.string.settings_home_location_auto) + ": " +
                current.province + (current.city?.let { " · $it" } ?: "")
        else -> stringResource(R.string.settings_home_location_system)
    }

    SettingsClickable(
        title = stringResource(R.string.settings_home_location),
        summary = summary,
        icon = Icons.Default.LocationOn,
        onClick = { showProvinceDialog = true }
    )

    // Auto-detect button row
    SettingsClickable(
        title = stringResource(R.string.settings_home_location_auto),
        summary = if (autoBusy) "…" else autoResult?.let {
            "${it.province}${it.city?.let { c -> " · $c" } ?: ""}"
        } ?: stringResource(R.string.settings_home_location_desc),
        icon = Icons.Default.GpsFixed,
        onClick = {
            // Manual always wins — clear it so auto becomes visible
            HomeLocationResolver.setManual(context, null, null)
            viewModel.updateString(HomeLocationResolver.PREF_PROVINCE, "")
            viewModel.updateString(HomeLocationResolver.PREF_CITY, "")
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                autoBusy = true
                scope.launch {
                    autoResult = HomeLocationResolver.refreshAutoLocation(context)
                    autoBusy = false
                }
            } else {
                permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
    )

    if (showProvinceDialog) {
        val options = remember {
            ChinaProvinces.all.map { p ->
                FilterViewModel.CategoryItem(p.key, p.key, count = p.cities.size)
            }
        }
        SearchableSelectionDialog(
            title = stringResource(R.string.settings_home_province),
            options = options,
            showCount = false,
            onSelect = { item ->
                showProvinceDialog = false
                // Province picked manually; city defaults to empty (= capital)
                HomeLocationResolver.setManual(context, item.code, null)
                viewModel.updateString(HomeLocationResolver.PREF_PROVINCE, item.code)
                viewModel.updateString(HomeLocationResolver.PREF_CITY, "")
                showCityDialog = true
            },
            onDismiss = { showProvinceDialog = false }
        )
    }

    if (showCityDialog) {
        val province = uiState.homeProvince.ifEmpty { current.province.orEmpty() }
        val options = remember(province) {
            val cities = ChinaCities.cities.filter { it.province == province }
                .ifEmpty { ChinaProvinces.byKey[province]?.cities?.map { ChinaCities.City(it, province, 0.0, 0.0) } ?: emptyList() }
            cities.map { FilterViewModel.CategoryItem(it.name, it.name) }
        }
        SearchableSelectionDialog(
            title = stringResource(R.string.settings_home_city),
            options = options,
            showCount = false,
            onSelect = { item ->
                showCityDialog = false
                HomeLocationResolver.setManual(context, province, item.code)
                viewModel.updateString(HomeLocationResolver.PREF_PROVINCE, province)
                viewModel.updateString(HomeLocationResolver.PREF_CITY, item.code)
            },
            onDismiss = { showCityDialog = false }
        )
    }
}
