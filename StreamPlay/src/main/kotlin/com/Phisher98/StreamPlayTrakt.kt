package com.phisher98

import android.content.SharedPreferences
import android.util.Log
import com.lagradost.cloudstream3.metaproviders.TraktProvider
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app

class StreamPlayTrakt(private val sharedPref: SharedPreferences) : TraktProvider() {
    override var name = "StreamPlay"
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama, TvType.Anime)
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
        val jsonObj = runCatching { org.json.JSONObject(data) }.getOrNull()

        var title = jsonObj?.optString("title")?.takeIf { it.isNotBlank() }
            ?: jsonObj?.optString("name")?.takeIf { it.isNotBlank() }
        val year = jsonObj?.optInt("year")?.takeIf { it > 0 }
        val isAnime = jsonObj?.optBoolean("is_anime", false) ?: jsonObj?.optBoolean("isAnime", false) ?: false
        val imdbIdRaw = jsonObj?.optString("imdb_id")?.takeIf { it.isNotBlank() } 
            ?: jsonObj?.optString("imdbId")?.takeIf { it.isNotBlank() }
        val season = jsonObj?.optInt("season")?.takeIf { it > 0 }
        val episode = jsonObj?.optInt("episode")?.takeIf { it > 0 }
        val isMovie = season == null || episode == null
        
        var tmdbId: Int? = null
        var isAsian = false
        var isBollywood = false
        var jpTitle: String? = null
        var orgTitle: String? = null
        var type: String? = null

        if (imdbIdRaw != null) {
            runCatching {
                val findRes = app.get("https://api.themoviedb.org/3/find/$imdbIdRaw?api_key=98ae14df2b8d8f8f8136499daf79f0e0&external_source=imdb_id&language=en-US").text
                val json = org.json.JSONObject(findRes)
                val movieResults = json.optJSONArray("movie_results")
                val tvResults = json.optJSONArray("tv_results")
                
                var tmdbObj: org.json.JSONObject? = null
                if (movieResults != null && movieResults.length() > 0) {
                    tmdbObj = movieResults.getJSONObject(0)
                    type = "movie"
                } else if (tvResults != null && tvResults.length() > 0) {
                    tmdbObj = tvResults.getJSONObject(0)
                    type = "tv"
                }

                if (tmdbObj != null) {
                    tmdbId = tmdbObj.optInt("id")
                    title = tmdbObj.optString("title").takeIf { it.isNotBlank() } ?: tmdbObj.optString("name").takeIf { it.isNotBlank() } ?: title
                    orgTitle = tmdbObj.optString("original_title").takeIf { it.isNotBlank() } ?: tmdbObj.optString("original_name")
                    val originalLanguage = tmdbObj.optString("original_language")
                    isAsian = originalLanguage in listOf("ja", "ko", "zh", "th")
                    isBollywood = originalLanguage == "hi"
                    if (originalLanguage == "ja") {
                        jpTitle = orgTitle
                    }
                }
            }
        }

        val linkData = StreamPlay.LinkData(
            id = tmdbId,
            imdbId = imdbIdRaw,
            title = title,
            orgTitle = orgTitle,
            jpTitle = jpTitle,
            year = year,
            season = season,
            episode = episode,
            isAnime = isAnime,
            isAsian = isAsian,
            isBollywood = isBollywood,
            type = type,
            isMovie = isMovie
        )
        val tmdbData = linkData.toJson()
        return StreamPlay(sharedPref).loadLinks(tmdbData, isCasting, subtitleCallback, callback)
    }

}
