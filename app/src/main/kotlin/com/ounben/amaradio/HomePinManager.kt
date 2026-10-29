package com.ounben.amaradio

import android.content.Context
import com.ounben.amaradio.database.toHomePinEntity
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Stations pinned to the home screen's "我的置顶" group.
 * Mirrors the CustomStationManager pattern but keeps a dedicated table
 * so pinning never pollutes favorites/custom lists.
 */
class HomePinManager(ctx: Context) : StationSaveManager(ctx) {

    override fun getSaveId(): String = "homepin"

    override fun reorder(fromIndex: Int, toIndex: Int) {
        scope.launch(Dispatchers.IO) {
            val newList = listStations.toMutableList()
            if (fromIndex !in newList.indices || toIndex !in newList.indices) return@launch
            val item = newList.removeAt(fromIndex)
            newList.add(toIndex, item)
            persistOrder(newList)
        }
    }

    override fun persistOrder(stations: List<DataRadioStation>) {
        scope.launch(Dispatchers.IO) {
            val entities = stations.mapIndexed { index, station ->
                station.toHomePinEntity(index)
            }
            userDb.homePinDao().updateAll(entities)
        }
    }
}
