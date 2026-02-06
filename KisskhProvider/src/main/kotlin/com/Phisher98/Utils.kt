package com.phisher98

import com.lagradost.cloudstream3.app
import org.json.JSONObject
import java.net.URLEncoder

const val TMDBAPI = "https://api.themoviedb.org/3"

suspend fun fetchtmdb(title: String?, year: Int?, isMovie: Boolean): Int? {
    if (title.isNullOrBlank()) return null

    val encodedTitle = URLEncoder.encode(title, "UTF-8")
    val yearParam = when {
        year == null -> ""
        isMovie -> "&year=$year"
        else -> "&first_air_date_year=$year"
    }

    val url = "${TMDBAPI}/search/multi?api_key=1865f43a0549ca50d341dd9ab8b29f49&query=$encodedTitle$yearParam"
    val json = JSONObject(app.get(url).text)
    val results = json.optJSONArray("results") ?: return null

    val targetType = if (isMovie) "movie" else "tv"

    for (i in 0 until results.length()) {
        val item = results.optJSONObject(i) ?: continue
        if (item.optString("media_type") != targetType) continue

        val resultTitle = if (isMovie) item.optString("title") else item.optString("name")
        val dateStr = if (isMovie) item.optString("release_date") else item.optString("first_air_date")
        val resultYear = dateStr.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
        val titleMatches = resultTitle.equals(title, ignoreCase = true)
        val yearMatches = (year == null || resultYear == null || year == resultYear)
        if (titleMatches && yearMatches) {
            return item.optInt("id")
        }
    }
    return null
}
