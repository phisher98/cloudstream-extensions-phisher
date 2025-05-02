package com.TorraStream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.metaproviders.TraktProvider
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.utils.*

class TorraStreamAIODebian() : TraktProvider() {
    override var name = "TorraStream AIO Debian"
    override var mainUrl =""
    override var supportedTypes =
        setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama, TvType.Torrent)
    override var lang = "en"
    override val supportedSyncNames = setOf(SyncIdName.Trakt)
    override val hasMainPage: Boolean get() = mainUrl.isNotBlank()
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

    @Suppress("NAME_SHADOWING")
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val data = AppUtils.parseJson<LinkData>(data)
        val season = data.season
        val episode = data.episode
        val id = data.imdbId
        val mainurl = if (season == null) {
            "$mainUrl/stream/movie/$id.json"
        } else {
            "$mainUrl/stream/series/$id:$season:$episode.json"
        }
        app.get(mainurl).parsedSafe<AIO>()?.streams?.map {
            val qualityRegex = Regex("""\b(4K|2160p|1080p|720p|WEB[-\s]?DL|BluRay|HDRip|DVDRip)\b""", RegexOption.IGNORE_CASE)
            val qualityMatch = qualityRegex.find(it.name)?.value ?: "Unknown"
            callback.invoke(
                newExtractorLink(
                    "Torrentio AIO Debian ${it.name}",
                    it.name,
                    it.url,
                    INFER_TYPE
                ) {
                    this.referer = ""
                    this.quality = getIndexQuality(qualityMatch)
                }
            )
        }
        val SubAPI = "https://opensubtitles-v3.strem.io"
        val url = if (season == null) {
            "$SubAPI/subtitles/movie/$id.json"
        } else {
            "$SubAPI/subtitles/series/$id:$season:$episode.json"
        }
        val headers = mapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        )
        app.get(url, headers = headers, timeout = 100L).parsedSafe<Subtitles>()?.subtitles?.amap {
            val lan = getLanguage(it.lang) ?: it.lang
            val suburl = it.url
            subtitleCallback.invoke(
                SubtitleFile(
                    lan,  // Use label for the name
                    suburl     // Use extracted URL
                )
            )
        }
        return true
    }

    data class AIO(
        val streams: List<Stream>,
    )

    data class Stream(
        val url: String,
        val name: String,
        val description: String,
        val behaviorHints: BehaviorHints,
    )

    data class BehaviorHints(
        val videoSize: Long,
        val filename: String,
        val bingeGroup: String,
    )

}
