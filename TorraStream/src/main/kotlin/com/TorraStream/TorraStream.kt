package com.TorraStream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.metaproviders.TraktProvider
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.utils.*

class TorraStream() : TraktProvider() {
    override var name = "TorraStream"
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama,TvType.Torrent)
    override var lang = "en"
    override val supportedSyncNames = setOf(SyncIdName.Trakt)
    override val hasMainPage = true
    override val hasQuickSearch = false

    companion object
    {
        const val TorrentioAPI="https://torrentio.strem.fun/sort=seeders%7Climit=60"
        const val OnethreethreesevenxAPI="https://proxy.phisher2.workers.dev/?url=https://1337x.to"
        const val MediafusionApi="https://mediafusion.elfhosted.com/D-_ru4-xVDOkpYNgdQZ-gA6whxWtMNeLLsnAyhb82mkks4eJf4QTlrAksSeBnwFAbIGWQLaokCGFxxsHupxSVxZO8xhhB2UYnyc5nnLeDnIqiLajtkmaGJMB_ZHqMqSYIU2wcGhrw0s4hlXeRAfnnbDywHCW8DLF_ZZfOXYUGPzWS-91cvu7kA2xPs0lJtcqZO"
        const val ThePirateBayApi="https://thepiratebay-plus.strem.fun"
        const val PeerflixApi="https://peerflix.mov"
        const val CometAPI = "https://comet.elfhosted.com"
        const val SubtitlesAPI="https://opensubtitles-v3.strem.io"
        const val AnimetoshoAPI= "https://feed.animetosho.org"
        const val TorrentioAnimeAPI="https://torrentio.strem.fun/providers=nyaasi,tokyotosho,anidex%7Csort=seeders"
        const val TRACKER_LIST_URL="https://newtrackon.com/api/all"

    }

    private val traktApiUrl = base64Decode("aHR0cHM6Ly9hcGl6LnRyYWt0LnR2")

    override val mainPage =
        mainPageOf(
            "$traktApiUrl/movies/trending?extended=cloud9,full&limit=25" to "Trending Movies",
            "$traktApiUrl/movies/popular?extended=cloud9,full&limit=25" to "Popular Movies",
            "$traktApiUrl/shows/trending?extended=cloud9,full&limit=25" to "Trending Shows",
            "$traktApiUrl/shows/popular?extended=cloud9,full&limit=25" to "Popular Shows",
            "$traktApiUrl/shows/trending?extended=cloud9,full&limit=25&network_ids=53,1465" to "Netflix",
            "$traktApiUrl/shows/trending?extended=cloud9,full&limit=25&network_ids=47,2385" to "Amazon Prime Video",
            "$traktApiUrl/shows/trending?extended=cloud9,full&limit=25&network_ids=256" to "Apple TV+",
            "$traktApiUrl/shows/trending?extended=cloud9,full&limit=25&network_ids=41,2018,2566,2567,2597" to "Disney+",
            "$traktApiUrl/shows/trending?extended=cloud9,full&limit=25&network_ids=87" to "Hulu",
            "$traktApiUrl/shows/trending?extended=cloud9,full&limit=25&network_ids=1623" to "Paramount+",
            "$traktApiUrl/shows/trending?extended=cloud9,full&limit=25&network_ids=550,3027" to "Peacock",
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
        val anidbEid = getAnidbEid(anijson, episode) ?: 0
        runAllAsync(
            {
                invokeTorrentio(
                    TorrentioAPI,
                    id,
                    season,
                    episode,
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
               if (data.isAnime) invokeAnimetosho(
                   anidbEid,
                   callback
               )
           },
           {
               if (data.isAnime) invokeTorrentioAnime(
                   TorrentioAnimeAPI,
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
