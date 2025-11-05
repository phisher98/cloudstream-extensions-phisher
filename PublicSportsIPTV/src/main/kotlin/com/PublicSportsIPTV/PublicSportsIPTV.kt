package com.PublicSportsIPTV

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

class PublicSportsIPTV : MainAPI() {
    override var mainUrl: String = com.phisher98.BuildConfig.FanCode_API
    override var name = "PublicSportsIPTV"
    override val hasMainPage = true
    override var lang = "en"
    override val supportedTypes = setOf(TvType.Live)


    companion object
    {
        private const val User_Agent  = "ReactNativeVideo/8.0.0 (Linux;Android/13) AndroidXMedia3/1.1.1"
        val Referer  = base64Decode("aHR0cHM6Ly9mYW5jb2RlLmNvbS8=")
    }


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = app.get(mainUrl).parsed<Root>()
        val matches = data.matches

        val live = matches.filter  {
            it.status.trim().contains("LIVE", ignoreCase = true)
        }

        val upcoming = matches.filter {
            it.status.trim().contains("NOT_STARTED", ignoreCase = true)
        }

        val liveList = live.map { it.toSearchResult() }
        val upcomingList = upcoming.map { it.toSearchResult() }

        return newHomePageResponse(
            listOf(
                HomePageList("Live Now", liveList, isHorizontalImages = true),
                HomePageList("Upcoming", upcomingList, isHorizontalImages = true)
            ),
            hasNext = false
        )
    }

    private fun Match.toSearchResult(): SearchResponse {
        val title = this.title
        val href = LoadURL(this.streamingCdn.primaryPlaybackUrl,this.streamingCdn.fancodeCdn,this.streamingCdn.daiGoogleCdn,this.streamingCdn.cloudfrontCdn,this.title,this.tournament,this.image)
        val posterUrl = this.imageCdn.cloudfare ?: this.image
        return newLiveSearchResponse(title, href.toJson(), TvType.Live) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val parsed = tryParseJson<LoadURL>(url)
        val title = parsed?.title ?: "PublicSportsIPTV"
        val poster = parsed?.poster ?: "https://www.fancode.com/skillup-uploads/fc-web/home-page-new-arc/hero-image/v1/hero-image-dweb-v4.png"
        val description = parsed?.tournament ?:"FanCode was founded in 2019 by Yannick Colaco and Prasana Krishnan. It is an over-the-top streaming service and sports e-commerce company in India. It is part of the Dream Sports group"

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
        val parsed = tryParseJson<LoadURL>(data) ?: return false
        val urls = listOfNotNull(
            parsed.primaryPlaybackUrl,
            parsed.fancodeCdn,
            parsed.daiGoogleCdn,
            parsed.cloudfrontCdn
        ).distinct().toMutableList()

        if (urls.isEmpty()) return false

        urls.forEachIndexed { index, url ->
            if (url.startsWith("http"))
            callback.invoke(
                newExtractorLink(
                    name,
                    "$name ${index+1}",
                    url = url,
                    INFER_TYPE
                ) {
                    this.referer = Referer
                    this.quality = Qualities.P1080.value
                }
            )
        }

        return true
    }
}
