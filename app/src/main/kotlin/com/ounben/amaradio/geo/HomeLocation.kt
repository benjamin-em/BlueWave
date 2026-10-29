package com.ounben.amaradio.geo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves the user's home province/city for the home screen groups.
 *
 * Priority (manual first — car head units often deny or lack GPS):
 *  1. Manual setting: pref "settings_home_province" (+ optional "settings_home_city")
 *  2. Last coarse-location fix cached in prefs ("auto_loc_lat"/"auto_loc_lon")
 *  3. A fresh coarse-location read (requires ACCESS_COARSE_LOCATION; caller
 *     handles the runtime permission request)
 *  4. System locale / country fallback (no CN grouping if country != CN)
 */
data class HomeLocation(
    val province: String?,   // canonical province key, e.g. "湖北"; null if unresolved/foreign
    val city: String?,       // display city, e.g. "武汉"; may be null (province capital used)
    val isManual: Boolean,
    val isChina: Boolean
)

object HomeLocationResolver {
    const val PREF_PROVINCE = "settings_home_province"
    const val PREF_CITY = "settings_home_city"
    const val PREF_AUTO_LAT = "auto_loc_lat"
    const val PREF_AUTO_LON = "auto_loc_lon"

    private const val TAG = "HomeLocation"

    // Personal car-radio fork defaults: Wuhan, Hubei. Overridable in Settings.
    const val DEFAULT_PROVINCE = "湖北"
    const val DEFAULT_CITY = "武汉"

    fun resolve(context: Context): HomeLocation {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        // 1. Manual selection wins
        val manualProvince = prefs.getString(PREF_PROVINCE, "")?.takeIf { it.isNotBlank() }
        if (manualProvince != null) {
            val manualCity = prefs.getString(PREF_CITY, "")?.takeIf { it.isNotBlank() }
            return HomeLocation(manualProvince, manualCity, isManual = true, isChina = true)
        }

        // 2. Cached auto fix
        val hasCached = prefs.contains(PREF_AUTO_LAT) && prefs.contains(PREF_AUTO_LON)
        if (hasCached) {
            val lat = prefs.getFloat(PREF_AUTO_LAT, 0f).toDouble()
            val lon = prefs.getFloat(PREF_AUTO_LON, 0f).toDouble()
            val city = ChinaCities.nearestCity(lat, lon)
            if (city != null) {
                return HomeLocation(city.province, city.name, isManual = false, isChina = true)
            }
        }

        // 3. Fork default: this build is for personal use in Wuhan.
        // CN grouping always makes sense here regardless of system locale.
        return HomeLocation(DEFAULT_PROVINCE, DEFAULT_CITY, isManual = false, isChina = true)
    }

    /**
     * Try a fresh coarse-location read and cache it. Returns the resolved
     * location, or null if permission is missing / no fix available.
     * Never blocks longer than a cached-provider read.
     */
    suspend fun refreshAutoLocation(context: Context): HomeLocation? = withContext(Dispatchers.IO) {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) return@withContext null

            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return@withContext null
            val fix = getLastFix(lm) ?: return@withContext null

            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.edit()
                .putFloat(PREF_AUTO_LAT, fix.latitude.toFloat())
                .putFloat(PREF_AUTO_LON, fix.longitude.toFloat())
                .apply()

            val city = ChinaCities.nearestCity(fix.latitude, fix.longitude) ?: return@withContext null
            HomeLocation(city.province, city.name, isManual = false, isChina = true)
        } catch (e: SecurityException) {
            Log.w(TAG, "No location permission", e)
            null
        } catch (e: Exception) {
            Log.w(TAG, "Auto location failed", e)
            null
        }
    }

    private fun getLastFix(lm: LocationManager): Location? {
        var best: Location? = null
        for (provider in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            try {
                val loc = lm.getLastKnownLocation(provider) ?: continue
                // Ignore fixes older than ~7 days — still fine for province-level grouping
                if (System.currentTimeMillis() - loc.time > 7L * 24 * 3600 * 1000) continue
                if (best == null || loc.time > best.time) best = loc
            } catch (_: SecurityException) {
            } catch (_: IllegalArgumentException) {
            }
        }
        return best
    }

    fun setManual(context: Context, province: String?, city: String?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val ed = prefs.edit()
        if (province.isNullOrBlank()) {
            ed.remove(PREF_PROVINCE).remove(PREF_CITY)
        } else {
            ed.putString(PREF_PROVINCE, province)
            if (city.isNullOrBlank()) ed.remove(PREF_CITY) else ed.putString(PREF_CITY, city)
        }
        ed.apply()
    }

    fun clearAutoCache(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .remove(PREF_AUTO_LAT).remove(PREF_AUTO_LON)
            .apply()
    }
}
