package com.ounben.amaradio.playback

import com.ounben.amaradio.station.DataRadioStation

/**
 * The station list the user is currently browsing/playing from.
 *
 * Steering-wheel buttons (BT AVRCP next/prev) route through
 * PlayerService.next()/previous(). The lightweight IPC copy of a station
 * loses its `queue` reference, so the service consults this in-process
 * holder first: whatever list the UI last played from becomes the
 * next/previous context. Falls back to history/favourites when empty.
 *
 * UI and PlayerService run in the same process, so a singleton is safe.
 */
object PlaybackQueueHolder {

    @Volatile
    private var stations: List<DataRadioStation> = emptyList()

    fun setStations(list: List<DataRadioStation>) {
        stations = list
    }

    fun getStations(): List<DataRadioStation> = stations

    fun getUuids(): List<String> = stations.map { it.StationUuid }

    fun getById(uuid: String): DataRadioStation? =
        stations.find { it.StationUuid == uuid }

    fun getNextById(uuid: String): DataRadioStation? {
        if (stations.isEmpty()) return null
        val idx = stations.indexOfFirst { it.StationUuid == uuid }
        if (idx == -1) return null
        return stations[(idx + 1) % stations.size]
    }

    fun getPreviousById(uuid: String): DataRadioStation? {
        if (stations.isEmpty()) return null
        val idx = stations.indexOfFirst { it.StationUuid == uuid }
        if (idx == -1) return null
        return stations[(idx - 1 + stations.size) % stations.size]
    }
}
