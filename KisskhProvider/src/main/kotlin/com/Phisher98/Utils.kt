package com.phisher98

import com.lagradost.cloudstream3.app
import org.json.JSONObject
import java.net.URLEncoder

const val TMDBAPI = "https://api.themoviedb.org/3"

suspend fun fetchtmdb(title: String?, year: Int?, isMovie: Boolean): Int? {
    if (title.isNullOrBlank()) return null

    val encodedTitle = URLEncoder.encode(title, "UTF-8")
    val url = "${TMDBAPI}/search/multi?api_key=1865f43a0549ca50d341dd9ab8b29f49&query=$encodedTitle"
    val json = JSONObject(app.get(url).text)
    val results = json.optJSONArray("results") ?: return null

    fun matches(item: JSONObject, ignoreYear: Boolean): Boolean {

        val resultTitle = if (isMovie) item.optString("title") else item.optString("name")
        val dateStr = if (isMovie) item.optString("release_date") else item.optString("first_air_date")
        val resultYear = dateStr.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()

        val titleMatches =
            resultTitle.equals(title, ignoreCase = true) ||
                    resultTitle.contains(title, ignoreCase = true) ||
                    title.contains(resultTitle, ignoreCase = true)

        val yearMatches =
            ignoreYear || year == null || resultYear == null || year == resultYear

        return titleMatches && yearMatches
    }

    // Pass 1 — original behavior (with year)
    for (i in 0 until results.length()) {
        val item = results.optJSONObject(i) ?: continue
        if (matches(item, ignoreYear = false)) {
            return item.optInt("id")
        }
    }

    // Pass 2 — fallback (ignore year)
    for (i in 0 until results.length()) {
        val item = results.optJSONObject(i) ?: continue
        if (matches(item, ignoreYear = true)) {
            return item.optInt("id")
        }
    }

    return null
}

