package com.TorraStream

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.argamap
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.metaproviders.TraktProvider
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets


class TorraStreamDebian() : TraktProvider() {
    override var name = "TorraStream-Debian"
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama,TvType.Torrent)
    override var mainUrl = "https://torrentio.strem.fun"
    override var lang = "en"
    override val supportedSyncNames = setOf(SyncIdName.Trakt)
    override val hasMainPage: Boolean get() = mainUrl.contains("=")

    override val hasQuickSearch = false

    private val traktApiUrl = base64Decode("aHR0cHM6Ly9hcGl6LnRyYWt0LnR2")

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
        val metadata=AppUtils.parseJson<LinkData>(data)
        val season =metadata.season
        val episode =metadata.episode
        val id =metadata.imdbId
        val encodedUrl = withContext(Dispatchers.IO) {
            URLEncoder.encode(mainUrl, StandardCharsets.UTF_8.toString())
        }
        val api = if (mainUrl.contains("=")) "https://torrentio.strem.fun/$encodedUrl" else null
        if (api!=null) {
            runAllAsync(
                {
                    invokeTorrentioDebian(
                        api,
                        id,
                        season,
                        episode,
                        callback
                    )
                },

                //Subtitles
                {
                    invokeSubtitleAPI(
                        id,
                        season,
                        episode,
                        subtitleCallback,
                    )
                }
            )
        }
        val SubAPI="https://opensubtitles-v3.strem.io"
        val url = if(season == null) {
            "$SubAPI/subtitles/movie/$id.json"
        }
        else {
            "$SubAPI/subtitles/series/$id:$season:$episode.json"
        }
        val headers = mapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        )
        app.get(url, headers = headers, timeout = 100L).parsedSafe<Subtitles>()?.subtitles?.amap {
            val lan=getLanguage(it.lang) ?:it.lang
            val suburl=it.url
            subtitleCallback.invoke(
                SubtitleFile(
                    lan,  // Use label for the name
                    suburl     // Use extracted URL
                )
            )
        }
        return true
    }
}