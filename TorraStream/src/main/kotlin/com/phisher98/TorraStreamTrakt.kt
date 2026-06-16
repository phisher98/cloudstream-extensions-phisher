package com.phisher98

import android.content.SharedPreferences
import android.util.Base64
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.metaproviders.TraktProvider
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.phisher98.TorraStream.Companion.Knaben
import com.phisher98.TorraStream.Companion.Meteorfortheweebs
import com.phisher98.TorraStream.Companion.ThePirateBayApi
import com.phisher98.TorraStream.Companion.TorrentioAnimeAPI
import com.phisher98.TorraStream.Companion.TorrentsDB
import com.phisher98.TorraStream.Companion.Uindex
import org.json.JSONArray
import org.json.JSONObject
import com.lagradost.cloudstream3.amap

class TorraStreamTrakt(private val sharedPref: SharedPreferences) : TraktProvider() {
    override var name = "TorraStream"
    override var mainUrl = "https://torrentio.strem.fun"
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama, TvType.Torrent)
    override var lang = "en"
    override val supportedSyncNames = setOf(SyncIdName.Trakt)
    override val hasMainPage = true
    override val hasQuickSearch = false

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val provider = sharedPref.getString("debrid_provider", null)
        val key = sharedPref.getString("debrid_key", null)
        val dataObj = parseJson<LoadDataTrakt>(data)
        val isAnime = dataObj.is_anime
        val title = dataObj.title
        val season = dataObj.season
        var episode = dataObj.episode
        val id = dataObj.imdb_id
        val year = dataObj.year
        val aniResponse = runCatching { app.get("https://api.ani.zip/mappings?imdb_id=$id") }.getOrNull()
        val anijson = aniResponse?.text.orEmpty()
        val aniJson = runCatching { JSONObject(anijson) }.getOrNull()
        val mappings = aniJson?.optJSONObject("mappings")
        val kitsuId = mappings?.optInt("kitsu_id")

        val isMovie = mappings
            ?.optString("type", "")
            ?.contains("MOVIE", ignoreCase = true) == true

        episode = if (isMovie) 1 else episode
        val anidbEid = getAnidbEid(anijson, episode) ?: 0

        val torrentioapiUrl = buildTorrentioApiUrl(sharedPref, mainUrl)
        val meteorUrl = buildMeteorUrl(sharedPref, Meteorfortheweebs)
        val filtered = filteredCallback(sharedPref, callback)

        if (!key.isNullOrEmpty() && provider != "AIO Streams") {
            runAllAsync(
                { invokeTorrentioDebian(torrentioapiUrl, id, season, episode, filtered) },
                { invokeMeteorDebian(meteorUrl, id, season, episode, filtered) }
            )
        }

        if (!provider.isNullOrEmpty() && !key.isNullOrEmpty()) {
            when (provider) {
                "AIO Streams" -> {
                    runAllAsync(
                        { invokeAIOStreamsDebian(key, id, season, episode, filtered) }
                    )
                }
            }
        } else {
            runAllAsync(
                { invokeTorrentio(torrentioapiUrl, id, season, episode, filtered) },
                {
                    if (!dataObj.is_anime) invokeThepiratebay(
                        ThePirateBayApi,
                        id,
                        season,
                        episode,
                        callback
                    )
                },
                { if (dataObj.is_anime) invokeAnimetosho(anidbEid, callback) },
                { invokeTorrentioAnime(TorrentioAnimeAPI, kitsuId, season, episode, filtered) },
                {
                    if (!dataObj.is_anime) invokeUindex(
                        Uindex,
                        title,
                        year,
                        season,
                        episode,
                        filtered
                    )
                },
                { invokeTorrentsDB(TorrentsDB, id, season, episode, callback) },
                {
                    if (dataObj.is_anime) invokeTorrentsDBAnime(
                        TorrentsDB,
                        kitsuId,
                        season,
                        episode,
                        filtered
                    )
                },
                { invokeKnaben(Knaben, isAnime, title, year, season, episode, filtered) }
            )
        }
        invokeSubtitleAPI(id, season, episode, subtitleCallback)
        return true
    }

    private fun buildTorrentioApiUrl(sharedPref: SharedPreferences, mainUrl: String): String {
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

    suspend fun buildMeteorUrl(sharedPref: SharedPreferences, baseUrl: String): String {

        val debridProvider = sharedPref.getString("debrid_provider", "") ?: ""
        val debridKey = sharedPref.getString("debrid_key", "") ?: ""
        val languagesPref = sharedPref.getString("language", "") ?: ""
        val limit = sharedPref.getString("limit", "0") ?: "0"
        val sizeFilter = sharedPref.getString("sizefilter", "0") ?: "0"

        // preferred languages
        val preferredLanguages = JSONArray().apply {
            if (languagesPref.isNotEmpty()) {
                languagesPref.split(",").amap { put(it.lowercase()) }
            } else {
                put("en")
                put("multi")
            }
        }

        val languages = JSONObject().apply {
            put("preferred", preferredLanguages)
            put("required", JSONArray())
            put("exclude", JSONArray())
        }

        val json = JSONObject().apply {
            put("debridService", debridProvider.lowercase())
            put("debridApiKey", debridKey)
            put("cachedOnly", false)
            put("removeTrash", true)
            put("removeSamples", true)
            put("removeAdult", false)
            put("exclude3D", false)
            put("enableSeaDex", false)

            put("minSeeders", 0)
            put("maxResults", limit.toIntOrNull() ?: 0)
            put("maxResultsPerRes", 0)
            put("maxSize", sizeFilter.toIntOrNull() ?: 0)

            put("resolutions", JSONArray())
            put("languages", languages)

            put(
                "resultFormat",
                JSONArray().apply {
                    put("title")
                    put("quality")
                    put("size")
                    put("audio")
                }
            )

            put(
                "sortOrder",
                JSONArray().apply {
                    put("cached")
                    put("resolution")
                    put("quality")
                    put("seeders")
                    put("size")
                    put("pack")
                    put("language")
                    put("seadex")
                }
            )
        }

        val encoded = Base64.encodeToString(
            json.toString().toByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP
        )

        return "$baseUrl/$encoded"
    }

    data class LoadDataTrakt(
        val title: String? = null,
        val year: Int? = null,
        val is_anime: Boolean = false,
        val imdb_id: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
    )
}