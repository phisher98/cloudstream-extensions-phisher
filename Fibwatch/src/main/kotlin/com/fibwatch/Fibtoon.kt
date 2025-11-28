package com.Fibwatch

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf

class Fibtoon : Fibwatch() {
    override var mainUrl = "https://fibtoon.top"
    override var name = "FibToon"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.Anime
    )

    override val mainPage = mainPageOf(
        "videos/top" to "Top Videos",
        "videos/latest" to "Latest Videos",
    )
}