package com.ounben.amaradio

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Car mode: big-button UI for in-car use.
 *
 * Preference "car_mode": auto | on | off (default auto).
 *  - auto: active whenever a Bluetooth audio device (A2DP/SCO) is the
 *    current output — i.e. connected to the car head unit.
 *  - on/off: manual override from the home-screen toggle button or settings.
 *
 * AudioDeviceCallback requires no Bluetooth permission (it reports audio
 * routing, not paired-device details), which keeps things simple on cars.
 */
object CarModeManager {
    private const val TAG = "CarModeManager"
    const val PREF_CAR_MODE = "car_mode"

    const val MODE_AUTO = "auto"
    const val MODE_ON = "on"
    const val MODE_OFF = "off"

    private val _isCarMode = MutableStateFlow(false)
    /** Whether car mode is currently ACTIVE (resolved from pref + BT state). */
    val isCarMode: StateFlow<Boolean> = _isCarMode.asStateFlow()

    private val _prefMode = MutableStateFlow(MODE_AUTO)
    /** The user's chosen mode (auto/on/off), for UI display. */
    val prefMode: StateFlow<String> = _prefMode.asStateFlow()

    private var audioManager: AudioManager? = null
    private var appContext: Context? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PREF_CAR_MODE) appContext?.let { update(it) }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            appContext?.let { update(it) }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            appContext?.let { update(it) }
        }
    }

    fun init(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.registerAudioDeviceCallback(deviceCallback, null)
        PreferenceManager.getDefaultSharedPreferences(ctx)
            .registerOnSharedPreferenceChangeListener(prefListener)
        update(ctx)
    }

    /** Recompute the active state from pref + current audio routing. */
    fun update(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val mode = prefs.getString(PREF_CAR_MODE, MODE_AUTO) ?: MODE_AUTO
        _prefMode.value = mode
        _isCarMode.value = when (mode) {
            MODE_ON -> true
            MODE_OFF -> false
            else -> isBluetoothAudioConnected()
        }
    }

    /** Home-screen toggle: flip between forced on and forced off. */
    fun toggle(context: Context) {
        val next = if (_isCarMode.value) MODE_OFF else MODE_ON
        setMode(context, next)
    }

    fun setMode(context: Context, mode: String) {
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putString(PREF_CAR_MODE, mode)
        }
        update(context)
    }

    private fun isBluetoothAudioConnected(): Boolean {
        return try {
            val devices = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) ?: return false
            devices.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
        } catch (e: Exception) {
            Log.w(TAG, "BT device check failed", e)
            false
        }
    }
}
