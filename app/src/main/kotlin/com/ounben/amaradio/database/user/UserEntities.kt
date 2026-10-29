package com.ounben.amaradio.database.user

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "station_favourite")
data class FavoriteEntity(
    @PrimaryKey val StationUuid: String,
    val Name: String,
    val Url: String,
    val Homepage: String = "",
    val Favicon: String,
    val Country: String,
    val CountryCode: String,
    val Tags: String,
    val Language: String,
    val Votes: Int = 0,
    val Subcountry: String = "",
    val clickcount: Int = 0,
    val ClickTrend: Int = 0,
    val Codec: String,
    val Bitrate: Int,
    val LastChangeTime: String = "",
    val Creation: String = "",
    val ChangeUuid: String = "",
    val LastCheckOkTime: String = "",
    val addedAt: Date = Date(),
    val displayOrder: Int = 0
)

@Entity(tableName = "station_history")
data class HistoryEntity(
    @PrimaryKey val StationUuid: String,
    val Name: String,
    val Url: String,
    val Homepage: String = "",
    val Favicon: String,
    val Country: String,
    val CountryCode: String,
    val Tags: String,
    val Language: String,
    val Votes: Int = 0,
    val Subcountry: String = "",
    val clickcount: Int = 0,
    val ClickTrend: Int = 0,
    val Codec: String,
    val Bitrate: Int,
    val LastChangeTime: String = "",
    val Creation: String = "",
    val ChangeUuid: String = "",
    val LastCheckOkTime: String = "",
    val lastPlayedAt: Date = Date()
)

/**
 * Stations pinned by the user to the home screen "我的置顶" group.
 * Independent of favorites — pinning does not pollute the favourite list.
 */
@Entity(tableName = "station_home_pin")
data class HomePinEntity(
    @PrimaryKey val StationUuid: String,
    val Name: String,
    val Url: String,
    val Homepage: String = "",
    val Favicon: String = "",
    val Country: String = "",
    val CountryCode: String = "",
    val Tags: String = "",
    val Language: String = "",
    val Votes: Int = 0,
    val Subcountry: String = "",
    val clickcount: Int = 0,
    val ClickTrend: Int = 0,
    val Codec: String = "",
    val Bitrate: Int = 0,
    val LastChangeTime: String = "",
    val Creation: String = "",
    val ChangeUuid: String = "",
    val LastCheckOkTime: String = "",
    val addedAt: Date = Date(),
    val displayOrder: Int = 0
)

@Entity(tableName = "filter_tab")
data class FilterTabEntity(
    @PrimaryKey val id: String,
    val label: String,
    val name: String,
    val countryCode: String,
    val countryLabel: String,
    val countryEmoji: String,
    val languageCode: String,
    val languageLabel: String,
    val tag: String,
    val tag2: String = "",
    val tag3: String = "",
    val tagOperator: String = "AND",
    val codec: String = "",
    val bitrate: Int = 0,
    val bitrateOperator: String = ">=",
    val sortBy: String,
    val reverse: Boolean,
    val position: Int // To maintain user order
)
