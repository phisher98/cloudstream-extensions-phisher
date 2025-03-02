package com.TorraStream

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.metaproviders.TraktProvider
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson

class TorraStream() : TraktProvider() {
    override var name = "TorraStream"
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama,TvType.Torrent)
    override var lang = "en"
    override val supportedSyncNames = setOf(SyncIdName.Trakt)
    override val hasMainPage = true
    override val hasQuickSearch = false

    companion object
    {
        const val TorrentioAPI="https://torrentio.strem.fun"
        const val TorrentgalaxyAPI="https://torrentgalaxy.to"
        const val TorrentmovieAPI="https://torrentmovie.net"
        const val OnethreethreesevenxAPI="https://1337x.to"
        const val TorBoxAPI="https://stremio.torbox.app"
        const val BitsearchApi="https://bitsearch.to"
        const val MediafusionApi="https://mediafusion.elfhosted.com"
        const val ThePirateBayApi="https://thepiratebay-plus.strem.fun"
        const val PeerflixApi="https://peerflix.mov"
        const val CometAPI = "https://comet.elfhosted.com"
        const val SubtitlesAPI="https://opensubtitles-v3.strem.io"
        const val AnimetoshoAPI= "https://feed.animetosho.org"
        const val TRACKER_LIST_URL="https://raw.githubusercontent.com/ngosang/trackerslist/refs/heads/master/trackers_all.txt"

    }

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
            //"$traktApiUrl/shows/trending?extended=cloud9&genres=anime,full&limit=25&extended=full" to "Trending Animes"
        )

    @Suppress("NAME_SHADOWING")
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val data=AppUtils.parseJson<LinkData>(data)
        val title=data.title
        val season =data.season
        val episode =data.episode
        val id =data.imdbId
        val year=data.year
        val anijson=app.get("https://api.ani.zip/mappings?imdb_id=$id").toString()
        val anidbEid = getAnidbEid(anijson, episode)
        argamap(
            {
                invokeTorrastream(
                    TorrentioAPI,
                    id,
                    season,
                    episode,
                    callback
            )
            },
            {
                invokeTorrentgalaxy(
                    TorrentgalaxyAPI,
                    id,
                    callback
                )
            },
            {
                invokeTorrentmovie(
                    TorrentmovieAPI,
                    title,
                    callback
                )
            },
            {
                invoke1337x(
                    OnethreethreesevenxAPI,
                    title,
                    year,
                    callback
                )
            },
            {
                invokeTorbox(
                    TorBoxAPI,
                    id,
                    season,
                    episode,
                    callback
                )

            },
            {
                invokeBitsearch(
                    BitsearchApi,
                    title,
                    season,
                    episode,
                    callback
                )

            },
            {
                invokeMediaFusion(
                    MediafusionApi,
                    id,
                    season,
                    episode,
                    callback
                )

            },
            {
                invokeThepiratebay(
                    ThePirateBayApi,
                    id,
                    season,
                    episode,
                    callback
                )

            },
            {
                invokePeerFlix(
                    PeerflixApi,
                    id,
                    season,
                    episode,
                    callback
                )
            },
            {
                invokeComet(
                    CometAPI,
                    id,
                    season,
                    episode,
                    callback
                )
            },
            {
                invokeAnimetosho(
                    anidbEid,
                    callback
                )
            },
            {
                invokeSubtitleAPI(
                    id,
                    season,
                    episode,
                    subtitleCallback,
                    callback
                )
            }
        )
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

suspend fun generateMagnetLink(url: String, hash: String?): String {
    // Fetch the content of the file from the provided URL
    val response = app.get(url)
    val trackerList = response.text.trim().split("\n") // Assuming each tracker is on a new line

    // Build the magnet link
    return buildString {
        append("magnet:?xt=urn:btih:$hash")
        trackerList.forEach { tracker ->
            if (tracker.isNotBlank()) {
                append("&tr=").append(tracker.trim())
            }
        }
    }
}
