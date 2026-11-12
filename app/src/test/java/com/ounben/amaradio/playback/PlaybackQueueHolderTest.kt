package com.ounben.amaradio.playback

import com.ounben.amaradio.station.DataRadioStation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlaybackQueueHolderTest {

    private fun station(id: String) = DataRadioStation(Name = id, StationUuid = id)

    private fun groups(): List<List<DataRadioStation>> = listOf(
        listOf(station("pin")),
        listOf(station("wh-1"), station("wh-2")),
        listOf(station("hb-1"), station("hb-2")),
        listOf(station("cn-1")),
        emptyList()
    )

    @Test
    fun nextWalksIntoTheFollowingGroup() {
        PlaybackQueueHolder.setGroupedStations(groups(), "wh-2", focusGroupIndex = 1)

        assertEquals("hb-1", PlaybackQueueHolder.getNextById("wh-2")?.StationUuid)
        assertEquals("hb-2", PlaybackQueueHolder.getNextById("hb-1")?.StationUuid)
        assertEquals("cn-1", PlaybackQueueHolder.getNextById("hb-2")?.StationUuid)
    }

    @Test
    fun nextWrapsFromTheLastGroupToTheFirst() {
        PlaybackQueueHolder.setGroupedStations(groups(), "cn-1", focusGroupIndex = 3)

        assertEquals("pin", PlaybackQueueHolder.getNextById("cn-1")?.StationUuid)
    }

    @Test
    fun previousStepsBackAcrossGroups() {
        PlaybackQueueHolder.setGroupedStations(groups(), "hb-1", focusGroupIndex = 2)

        assertEquals("wh-2", PlaybackQueueHolder.getPreviousById("hb-1")?.StationUuid)
        assertEquals("cn-1", PlaybackQueueHolder.getPreviousById("pin")?.StationUuid)
    }

    @Test
    fun duplicateIdStaysInTheGroupThatWasTapped() {
        val shared = station("shared")
        val grouped = listOf(
            listOf(shared, station("pin-2")),
            listOf(station("wh-1"), shared, station("wh-2"))
        )
        PlaybackQueueHolder.setGroupedStations(grouped, "shared", focusGroupIndex = 1)

        assertEquals("wh-2", PlaybackQueueHolder.getNextById("shared")?.StationUuid)
    }

    @Test
    fun flatListDoesNotLeaveItsOwnStations() {
        PlaybackQueueHolder.setStations(listOf(station("a"), station("b")))

        assertEquals("a", PlaybackQueueHolder.getNextById("b")?.StationUuid)
        assertNull(PlaybackQueueHolder.getNextById("missing"))
    }
}
