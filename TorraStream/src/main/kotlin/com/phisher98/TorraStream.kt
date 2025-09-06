package com.phisher98

import android.content.SharedPreferences
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
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

class TorraStream(private val sharedPref: SharedPreferences) : TraktProvider() {
    override var name = "TorraStream"
    override var mainUrl = "https://torrentio.strem.fun"
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama, TvType.Torrent)
    override var lang = "en"
    override val supportedSyncNames = setOf(SyncIdName.Trakt)
    override val hasMainPage = true
    override val hasQuickSearch = false

    companion object {
        const val TorrentioAPI = "https://torrentio.strem.fun/sort=seeders%7Climit=60"
        const val OnethreethreesevenxAPI = "https://proxy.phisher2.workers.dev/?url=https://1337x.to"
        const val MediafusionApi = "https://mediafusion.elfhosted.com/D-_ru4-xVDOkpYNgdQZ-gA6whxWtMNeLLsnAyhb82mkks4eJf4QTlrAksSeBnwFAbIGWQLaokCGFxxsHupxSVxZO8xhhB2UYnyc5nnLeDnIqiLajtkmaGJMB_ZHqMqSYIU2wcGhrw0s4hlXeRAfnnbDywHCW8DLF_ZZfOXYUGPzWS-91cvu7kA2xPs0lJtcqZO"
        const val ThePirateBayApi = "https://thepiratebay-plus.strem.fun"
        const val AIOStreams = "https://aiostreams.elfhosted.com/E2-xLzptGhmwLnA9L%2FOUHyZJg%3D%3D-Io2cJBStOrbqlmGwGz2ZwBbMGBj5enyJFgN5XcslkuiUS5KSjJrv90yd4HHLj1fyq6hJm7QpnCxDiPqbeOwdGA2yySllUQh2T%2B5qPqgtPt2sWBN5zdeetbiFFLHvVqq0PZOhKGM7pv2LzCoMLAk%2BSo86mcrzWIeszmvHuRMoKX3zBO6hUDvH6oqK2hFfbUF7ZONMdm9jE7lHp0LuXKPzHSwKUvDZroJ9iRgBkvHIGjJL65oBv2PxfQK%2Fu4gYEuLVhH3dQ7Xu6i1AshdxycCPRQOO2LcDDZkBC84zLXoy3DDPkvDkWBv2icVZIs2dnQlwvtfu7fFiXaGxWJxtYvbBALIhey8SaaeCKts8xMEyuJvSZiKBbkiTblb0NbqfRyGoJz5rJkiCPzlnX6S%2BpNHKNXVYRj2QZmmvN47fdteAZfhvCuNRW1XBP%2FhTr5ufzCQ9tC8ao%2F4ZhoVXPje45mgPpeJy%2FqYGkX36%2BDgjUMGM1SIvm416pHFL1fVG9MQlIdTn2T4VaUHA0dZHXxznaSQDB%2F1GIkDCHOp2iWUl8zceINOE08AI%2BUwmWCnVXsvsXYaTbFnsE%2F0n1zQwN19ULRCnO4AN2KKLfWKHCz9q5YwQG6y9r%2BXTkjtAXoju764x1f2UlFZT8aavjX1oAcPiTC5vA%3D%3D"
        const val PeerflixApi = "https://peerflix.mov"
        const val CometAPI = "https://comet.elfhosted.com"
        const val SubtitlesAPI = "https://opensubtitles-v3.strem.io"
        const val AnimetoshoAPI = "https://feed.animetosho.org"
        const val TorrentioAnimeAPI = "https://torrentio.strem.fun/providers=nyaasi,tokyotosho,anidex%7Csort=seeders"
        const val TorboxAPI= "https://stremio.torbox.app"
        const val TRACKER_LIST_URL = "https://newtrackon.com/api/all"
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

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val provider = sharedPref.getString("debrid_provider", null)
        val key = sharedPref.getString("debrid_key", null)
        val dataObj = AppUtils.parseJson<LinkData>(data)
        val title = dataObj.title
        val season = dataObj.season
        val episode = dataObj.episode
        val id = dataObj.imdbId
        val year = dataObj.year
        Log.d("Phisher API 1","$provider $key")

        val anijson = app.get("https://api.ani.zip/mappings?imdb_id=$id").toString()
        val anidbEid = getAnidbEid(anijson, episode) ?: 0

        val apiUrl = buildApiUrl(sharedPref, mainUrl)
        if (provider == "AIO Streams" && !key.isNullOrEmpty()) {
            runAllAsync(
                suspend { invokeAIOStreamsDebian(key, id, season, episode, callback) }
            )
        }

        if (provider == "TorBox" && !key.isNullOrEmpty()) {
            runAllAsync(
                suspend { invokeDebianTorbox(TorboxAPI, key, id, season, episode, callback) }
            )
        }
        Log.d("Phisher API",apiUrl)

        if (!key.isNullOrEmpty()) {
            runAllAsync(
                suspend { invokeTorrentioDebian(apiUrl, id, season, episode, callback) }
            )
        } else {
            runAllAsync(
                suspend { invokeTorrentio(apiUrl, id, season, episode, callback) },
                suspend { invoke1337x(OnethreethreesevenxAPI, title, year, callback) },
                suspend { invokeMediaFusion(MediafusionApi, id, season, episode, callback) },
                suspend { invokeThepiratebay(ThePirateBayApi, id, season, episode, callback) },
                suspend { invokePeerFlix(PeerflixApi, id, season, episode, callback) },
                suspend { invokeComet(CometAPI, id, season, episode, callback) },
                suspend { if (dataObj.isAnime) invokeAnimetosho(anidbEid, callback) },
                suspend { if (dataObj.isAnime) invokeTorrentioAnime(TorrentioAnimeAPI, id, season, episode, callback) },
                suspend { invokeAIOStreams(AIOStreams, id, season, episode, callback) },
                suspend { invokeSubtitleAPI(id, season, episode, subtitleCallback) }
            )
        }


        // Subtitles
        val subApiUrl = "https://opensubtitles-v3.strem.io"
        val url = if (season == null) "$subApiUrl/subtitles/movie/$id.json"
        else "$subApiUrl/subtitles/series/$id:$season:$episode.json"

        val headers = mapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        )

        app.get(url, headers = headers, timeout = 100L)
            .parsedSafe<Subtitles>()?.subtitles?.amap {
                val lan = getLanguage(it.lang) ?: it.lang
                subtitleCallback(
                    SubtitleFile(
                        lan,
                        it.url
                    )
                )
            }

        return true
    }

    private fun buildApiUrl(sharedPref: SharedPreferences, mainUrl: String): String {
        val sort = sharedPref.getString("sort", "qualitysize")
        val languageOption = sharedPref.getString("language", "")
        val qualityFilter = sharedPref.getString("qualityfilter", "")
        val limit = sharedPref.getString("limit", "")
        val sizeFilter = sharedPref.getString("sizefilter", "")
        val debridProvider = sharedPref.getString("debrid_provider", "") // e.g., "easydebrid"
        val debridKey = sharedPref.getString("debrid_key", "") // e.g., "12345abc"

        val params = mutableListOf<String>()
        if (!sort.isNullOrEmpty()) params += "sort=$sort"
        if (!languageOption.isNullOrEmpty()) params += "language=${languageOption.lowercase()}"
        if (!qualityFilter.isNullOrEmpty()) params += "qualityfilter=$qualityFilter"
        if (!limit.isNullOrEmpty()) params += "limit=$limit"
        if (!sizeFilter.isNullOrEmpty()) params += "sizefilter=$sizeFilter"

        if (!debridProvider.isNullOrEmpty() && !debridKey.isNullOrEmpty()) {
            params += "$debridProvider=$debridKey"
        }

        val query = params.joinToString("%7C")
        return "$mainUrl/$query"
    }

}

suspend fun generateMagnetLink(url: String, hash: String?): String {
    val response = app.get(url)
    val trackerList = response.text.trim().split("\n")

    return buildString {
        append("magnet:?xt=urn:btih:$hash")
        trackerList.forEach { tracker ->
            if (tracker.isNotBlank()) {
                append("&tr=").append(tracker.trim())
            }
        }
    }
}
