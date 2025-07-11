package com.phisher98

import com.phisher98.UltimaMediaProvidersUtils.invokeExtractors
import com.phisher98.UltimaUtils.Category
import com.phisher98.UltimaUtils.LinkData as UltimaLinkData
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.metaproviders.TraktProvider
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink

class Trakt(val plugin: UltimaPlugin) : TraktProvider() {
    override var name = "Trakt"
    override var mainUrl = "https://trakt.tv"
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)
    override var lang = "en"
    override val supportedSyncNames = setOf(SyncIdName.Trakt)
    override val hasMainPage = true
    override val hasQuickSearch = false

    private val traktClientId =
            base64Decode(
                    "N2YzODYwYWQzNGI4ZTZmOTdmN2I5MTA0ZWQzMzEwOGI0MmQ3MTdlMTM0MmM2NGMxMTg5NGE1MjUyYTQ3NjE3Zg=="
            )
    private val traktApiUrl = base64Decode("aHR0cHM6Ly9hcGl6LnRyYWt0LnR2")

    protected fun Any.toStringData(): String {
        return mapper.writeValueAsString(this)
    }

    override val mainPage =
            mainPageOf(
                    "$traktApiUrl/movies/trending?extended=cloud9,full&limit=25" to
                            "Trending Movies",
                    "$traktApiUrl/movies/popular?extended=cloud9,full&limit=25" to "Popular Movies",
                    "$traktApiUrl/shows/trending?extended=cloud9,full&limit=25" to "Trending Shows",
                    "$traktApiUrl/shows/popular?extended=cloud9,full&limit=25" to "Popular Shows",
                    "$traktApiUrl/shows/trending?extended=cloud9,full&limit=25&network_ids=53,1465" to
                            "Netflix",
                    "$traktApiUrl/shows/trending?extended=cloud9,full&limit=25&network_ids=47,2385" to
                            "Amazon Prime Video",
                    "$traktApiUrl/shows/trending?extended=cloud9,full&limit=25&network_ids=256" to
                            "Apple TV+",
                    "$traktApiUrl/shows/trending?extended=cloud9,full&limit=25&network_ids=41,2018,2566,2567,2597" to
                            "Disney+",
                    "$traktApiUrl/shows/trending?extended=cloud9,full&limit=25&network_ids=87" to
                            "Hulu",
                    "$traktApiUrl/shows/trending?extended=cloud9,full&limit=25&network_ids=1623" to
                            "Paramount+",
                    "$traktApiUrl/shows/trending?extended=cloud9,full&limit=25&network_ids=550,3027" to
                            "Peacock",
            )

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {
        val mediaData = AppUtils.parseJson<UltimaLinkData>(data)
        if (mediaData.isAnime)
                invokeExtractors(Category.ANIME, mediaData, subtitleCallback, callback)
        else invokeExtractors(Category.MEDIA, mediaData, subtitleCallback, callback)
        return true
    }
}
