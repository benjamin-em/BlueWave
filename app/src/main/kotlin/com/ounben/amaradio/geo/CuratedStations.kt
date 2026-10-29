package com.ounben.amaradio.geo

/**
 * Built-in curated station lists for the home screen groups.
 *
 * Station UUIDs are radio-browser stable identifiers. Lists are resolved
 * against the local catalog DB first; entries missing from the DB are
 * fetched via the radio-browser API (getStationsByUuid).
 *
 * The English list contains only English-language stations from
 * GB/US/IE/AU/CA (top-voted, manually screened — no German/French).
 */
object CuratedStations {

    /** Central (national) Chinese stations — CNR network. */
    val centralCn: List<String> = listOf(
        "835070cd-bf45-4e48-88a4-e9f9a0d7dd9e", // CNR-1 中国之声
        "431eb0c9-c84c-11e8-a54a-52543be04c81", // CNR-2 经济之声
        "a09db942-832d-4932-8c05-494e17dc37e0", // CNR-3 音乐之声
        "274fd83d-69ca-4826-bd2a-2f98d63470c5", // CNR-4 经典音乐广播
        "95f1653b-4c31-459c-9fbe-560ea41020dc", // CNR-9 文艺之声
        "c3f90fc1-fca9-43c4-8e0e-f14cb372391c", // CNR-10 老年之声
        "11db0398-6c49-41f3-97c9-411cbc794dfa", // CNR-15 中国交通广播
        "a7418958-67ff-40ae-a298-8b6651086fdc"  // CNR-16 中国乡村之声
    )

    /** Curated English-language international stations. */
    val englishWorld: List<String> = listOf(
        "98adecf7-2683-4408-9be7-02d3f9098eb8", // BBC World Service (GB)
        "96063f25-0601-11e8-ae97-52543be04c81", // Classic FM UK (GB)
        "96200095-0601-11e8-ae97-52543be04c81", // Capital FM London (GB)
        "9614bbb6-0601-11e8-ae97-52543be04c81", // LBC Radio (GB)
        "c4077677-dc2f-11e9-a8ba-52543be04c81", // Heart 80s (GB)
        "962b27a3-0601-11e8-ae97-52543be04c81", // Smooth Radio (GB)
        "0a1e0bb0-dc37-11e9-a8ba-52543be04c81", // Gold (GB)
        "9617a958-0601-11e8-ae97-52543be04c81", // Radio Paradise Main Mix 320k (US)
        "d1a54d2e-623e-4970-ab11-35f7b56c5ec3", // Classic Vinyl HD (US)
        "d28420a4-eccf-47a2-ace1-088c7e7cb7e0", // 101 SMOOTH JAZZ (US)
        "9627ddc0-0601-11e8-ae97-52543be04c81", // 181.FM Old School HipHop/RnB (US)
        "d0a1da9f-46b3-11e9-aa55-52543be04c81", // Today FM (IE)
        "9615d27a-0601-11e8-ae97-52543be04c81", // ABC NewsRadio (AU)
        "06faec0e-52eb-11e8-a4d1-52543be04c81", // Triple J (AU)
        "963b8a47-0601-11e8-ae97-52543be04c81", // Calm Radio - Solo Piano (CA)
        "147a830e-fbc3-4260-9504-6dcc62530923"  // CBC Radio 1 Toronto (CA)
    )
}
