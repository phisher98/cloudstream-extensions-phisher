package com.PublicSportsIPTV

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class PublicSportsIPTV : MainAPI() {
    override var mainUrl: String = com.Phisher98.BuildConfig.FanCode_API
    override var name = "PublicSportsIPTV"
    override val hasMainPage = true
    override var lang = "en"
    override val supportedTypes = setOf(TvType.Live)


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = app.get(mainUrl).parsedSafe<Root>()
        val home = data?.matches?.amap {
            it.toSearchResult()
        }
        return newHomePageResponse("Matches", home!!)
    }

    private fun Match.toSearchResult(): SearchResponse {
        val title = this.matchName
        val href = this.streamLink
        val posterUrl = this.banner
        return newMovieSearchResponse(title, href, TvType.Live) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val title = "PublicSportsIPTV"
        val poster =
            "https://www.fancode.com/skillup-uploads/fc-web/home-page-new-arc/hero-image/v1/hero-image-dweb-v4.png"
        val description =
            "FanCode was founded in 2019 by Yannick Colaco and Prasana Krishnan. It is an over-the-top streaming service and sports e-commerce company in India. It is part of the Dream Sports group"

        return newMovieLoadResponse(title, url, TvType.Live, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        callback.invoke(
            ExtractorLink(
                name,
                name,
                data,
                "",
                getQualityFromName(""),
                type = INFER_TYPE,
            )
        )
        return true
    }
}
