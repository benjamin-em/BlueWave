package com.ounben.amaradio.playback

import com.ounben.amaradio.station.DataRadioStation

/**
 * Station order used by steering-wheel and in-app next/previous.
 *
 * History cannot be that order. It is sorted by last-played time, and every
 * play moves the current station to the front, so "next" would forever swap
 * the two most recent stations.
 *
 * An explicit list (favourites, history screen, search) is used only while
 * the station being skipped is actually in it. Otherwise next/previous walks
 * the home groups in display order: pinned, city, province, national, English.
 */
object PlaybackQueueHolder {

    private data class Snapshot(
        val stations: List<DataRadioStation> = emptyList(),
        /** Index of the copy last resolved. -1 when the id has not been seen yet. */
        val position: Int = -1
    )

    @Volatile
    private var explicit: Snapshot? = null

    @Volatile
    private var home: Snapshot = Snapshot()

    fun setStations(list: List<DataRadioStation>) {
        explicit = Snapshot(stations = list.toList())
    }

    /**
     * @param groups groups in display order; empty groups are skipped
     * @param focusGroupIndex index into [groups] of the group the user tapped
     */
    fun setGroupedStations(
        groups: List<List<DataRadioStation>>,
        focusUuid: String,
        focusGroupIndex: Int
    ) {
        val flat = flatten(groups)
        var found = -1
        var offset = 0
        groups.forEachIndexed { index, group ->
            if (group.isEmpty()) return@forEachIndexed
            if (index == focusGroupIndex) {
                val local = group.indexOfFirst { it.StationUuid == focusUuid }
                if (local >= 0) found = offset + local
            }
            offset += group.size
        }
        val position = if (found >= 0) found else flat.indexOfFirst { it.StationUuid == focusUuid }
        explicit = Snapshot(stations = flat, position = position)
    }

    /** Home groups in display order. Replaces the previous catalog without touching an explicit list. */
    fun setHomeCatalog(groups: List<List<DataRadioStation>>) {
        val flat = flatten(groups)
        val currentId = home.stations.getOrNull(home.position)?.StationUuid
        val position = if (currentId == null) -1 else flat.indexOfFirst { it.StationUuid == currentId }
        home = Snapshot(stations = flat, position = position)
    }

    fun clear() {
        explicit = null
        home = Snapshot()
    }

    fun getStations(): List<DataRadioStation> = explicit?.stations ?: home.stations

    fun getUuids(): List<String> = getStations().map { it.StationUuid }

    fun getById(uuid: String): DataRadioStation? =
        getStations().find { it.StationUuid == uuid }

    fun getNextById(uuid: String): DataRadioStation? = step(uuid, 1)

    fun getPreviousById(uuid: String): DataRadioStation? = step(uuid, -1)

    private fun flatten(groups: List<List<DataRadioStation>>): List<DataRadioStation> {
        val flat = ArrayList<DataRadioStation>()
        groups.forEach { group ->
            if (group.isNotEmpty()) flat.addAll(group)
        }
        return flat
    }

    private fun step(uuid: String, delta: Int): DataRadioStation? {
        val chosen = select(uuid) ?: return null
        val (snap, isExplicit) = chosen
        val list = snap.stations
        if (list.isEmpty()) return null
        val idx = if (snap.position in list.indices && list[snap.position].StationUuid == uuid) {
            snap.position
        } else {
            list.indexOfFirst { it.StationUuid == uuid }
        }
        val next = if (idx == -1) {
            if (delta > 0) 0 else list.lastIndex
        } else {
            Math.floorMod(idx + delta, list.size)
        }
        val updated = snap.copy(position = next)
        if (isExplicit) explicit = updated else home = updated
        return list[next]
    }

    /** Explicit list wins only when it actually contains this station. */
    private fun select(uuid: String): Pair<Snapshot, Boolean>? {
        val chosenExplicit = explicit
        if (chosenExplicit != null && chosenExplicit.stations.any { it.StationUuid == uuid }) {
            return chosenExplicit to true
        }
        val catalog = home
        if (catalog.stations.isNotEmpty()) return catalog to false
        return null
    }
}
