package com.ounben.amaradio.playback

import com.ounben.amaradio.station.DataRadioStation

/**
 * The station list the user is currently browsing/playing from.
 *
 * Steering-wheel buttons (BT AVRCP next/prev) route through
 * PlayerService.next()/previous(). The lightweight IPC copy of a station
 * loses its `queue` reference, so the service consults this in-process
 * holder first. Falls back to history/favourites when empty.
 *
 * A home queue is the home groups in display order (置顶 / 本市 / 本省 /
 * 中央台 / 英语精选). Next walks off the end of one group into the next,
 * and wraps from the last group back to the first. A flat queue (favourites,
 * history, a search result) stays inside that one list.
 *
 * UI and PlayerService run in the same process, so a singleton is safe.
 */
object PlaybackQueueHolder {

    private data class Snapshot(
        val stations: List<DataRadioStation> = emptyList(),
        /** Index of the copy last resolved. -1 when the list has no duplicate ids. */
        val position: Int = -1
    )

    @Volatile
    private var snapshot = Snapshot()

    fun setStations(list: List<DataRadioStation>) {
        snapshot = Snapshot(stations = list)
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
        val flat = ArrayList<DataRadioStation>()
        var found = -1
        groups.forEachIndexed { index, group ->
            if (group.isEmpty()) return@forEachIndexed
            if (index == focusGroupIndex) {
                val local = group.indexOfFirst { it.StationUuid == focusUuid }
                if (local >= 0) found = flat.size + local
            }
            flat.addAll(group)
        }
        val position = if (found >= 0) found else flat.indexOfFirst { it.StationUuid == focusUuid }
        snapshot = Snapshot(stations = flat, position = position)
    }

    fun getStations(): List<DataRadioStation> = snapshot.stations

    fun getUuids(): List<String> = snapshot.stations.map { it.StationUuid }

    fun getById(uuid: String): DataRadioStation? =
        snapshot.stations.find { it.StationUuid == uuid }

    fun getNextById(uuid: String): DataRadioStation? = step(uuid, 1)

    fun getPreviousById(uuid: String): DataRadioStation? = step(uuid, -1)

    private fun step(uuid: String, delta: Int): DataRadioStation? {
        val current = snapshot
        val list = current.stations
        if (list.isEmpty()) return null
        val idx = if (current.position in list.indices && list[current.position].StationUuid == uuid) {
            current.position
        } else {
            list.indexOfFirst { it.StationUuid == uuid }
        }
        if (idx == -1) return null
        val next = Math.floorMod(idx + delta, list.size)
        snapshot = current.copy(position = next)
        return list[next]
    }
}
