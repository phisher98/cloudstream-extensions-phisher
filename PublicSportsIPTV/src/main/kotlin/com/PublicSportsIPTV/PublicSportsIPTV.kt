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


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = app.get(mainUrl).parsedSafe<Root>()
        val home = data?.matches?.amap {
            it.toSearchResult()
        }
        return newHomePageResponse("Matches", home!!)
    }

    private fun Match.toSearchResult(): SearchResponse {
        val title = this.title
        val href = LoadURL(this.streamingCdn.primaryPlaybackUrl,this.streamingCdn.fancodeCdn,this.streamingCdn.daiGoogleCdn,this.streamingCdn.cloudfrontCdn)
        val posterUrl = this.image
        return newMovieSearchResponse(title, href.toJson(), TvType.Live) {
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
                    this.referer = ""
                    this.quality = Qualities.P1080.value
                }
            )
        }

        return true
    }
}
