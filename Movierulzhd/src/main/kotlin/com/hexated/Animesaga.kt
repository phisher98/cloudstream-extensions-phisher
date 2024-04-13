package com.hexated

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf

class Animesaga : Movierulzhd() {

    override var mainUrl = "https://anplay.in"
    override var name = "Anplay"
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    override val mainPage = mainPageOf(
        "movies" to "Movies",
        "tvshows" to "TV-Shows",
        "genre/hindi-dub" to "Hindi Dub",
        "genre/crunchyroll" to "Crunchyroll",
    )
}
