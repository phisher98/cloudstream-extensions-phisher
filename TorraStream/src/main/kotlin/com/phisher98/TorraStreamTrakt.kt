package com.phisher98

import android.content.SharedPreferences
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.metaproviders.TraktProvider
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

class TorraStreamTrakt(private val sharedPref: SharedPreferences) : TraktProvider() {
    override var name = "TorraStream"
    override var mainUrl = "https://torrentio.strem.fun"
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama, TvType.Torrent)
    override var lang = "en"
    override val supportedSyncNames = setOf(SyncIdName.Trakt)
    override val hasMainPage = true
    override val hasQuickSearch = false

    companion object {
        const val ThePirateBayApi = "https://thepiratebay-plus.strem.fun"
        const val TorrentioAnimeAPI = "https://torrentio.strem.fun/providers=nyaasi,tokyotosho,anidex%7Csort=seeders"
        const val TorboxAPI= "https://stremio.torbox.app"
        private const val Uindex = "https://uindex.org"
        private const val Knaben = "https://knaben.org"
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val provider = sharedPref.getString("debrid_provider", null)
        val key = sharedPref.getString("debrid_key", null)
        val dataObj = parseJson<LinkData>(data)
        val isAnime = dataObj.isAnime
        val title = dataObj.title
        val season = dataObj.season
        var episode = dataObj.episode
        val id = dataObj.imdbId
        val year = dataObj.year
        val anijson = app.get("https://api.ani.zip/mappings?imdb_id=$id").toString()
        val mappings = runCatching {
            val response = app.get("https://api.ani.zip/mappings?imdb_id=$id")
            JSONObject(response.text).optJSONObject("mappings")
        }.getOrNull()
        val kitsuId = mappings?.optInt("kitsu_id")
        val isMovie = mappings
            ?.optString("type", "")
            ?.contains("MOVIE", ignoreCase = true) == true

        episode = if (isMovie) 1 else episode
        val anidbEid = getAnidbEid(anijson, episode) ?: 0

        suspend fun runAllAsync(vararg tasks: suspend () -> Unit) {
            coroutineScope {
                tasks.map { async { it() } }.awaitAll()
            }
        }
        val filtered = filteredCallback(sharedPref, callback)
        val apiUrl = buildApiUrl(sharedPref, mainUrl)

        if (provider == "AIO Streams" && !key.isNullOrEmpty()) {
            runAllAsync(
                { invokeAIOStreamsDebian(key, id, season, episode, callback, filtered) }
            )
        }

        if (provider == "TorBox" && !key.isNullOrEmpty()) {
            runAllAsync(
                { invokeDebianTorbox(TorboxAPI, key, id, season, episode, callback, filtered) }
            )
        }

        if (!key.isNullOrEmpty()) {
            runAllAsync(
                { invokeTorrentioDebian(apiUrl, id, season, episode, callback, filtered) }
            )
        } else {
            runAllAsync(
                { invokeTorrentio(apiUrl, id, season, episode, callback, filtered) },
                { invokeThepiratebay(ThePirateBayApi, id, season, episode, callback) },
                { if (dataObj.isAnime) invokeAnimetosho(anidbEid, callback) },
                { if (dataObj.isAnime) invokeTorrentioAnime(TorrentioAnimeAPI, kitsuId, season, episode, filtered) },
                { invokeUindex(Uindex, title, year, season, episode, callback, filtered) },
                { invokeKnaben(Knaben, isAnime, title, year, season, episode, callback, filtered) },
                { invokeSubtitleAPI(id, season, episode, subtitleCallback) }
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
                    newSubtitleFile(
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