package com.Fibwatch

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import kotlinx.coroutines.runBlocking

class Fibwatchdrama : Fibwatch() {
    override var mainUrl: String = runBlocking {
        FibwatchPlugin.getDomains()?.fibdrama ?: "https://fibdrama.top"
    }
    override var name = "FibWatch Drama"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "videos/top" to "Top Videos",
        "videos/latest" to "Latest Videos",
    )
}