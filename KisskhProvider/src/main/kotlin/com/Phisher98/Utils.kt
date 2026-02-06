package com.phisher98

import com.lagradost.cloudstream3.app
import org.json.JSONObject
import java.net.URLEncoder

const val TMDBAPI = "https://api.themoviedb.org/3"

suspend fun fetchtmdb(title: String?, isMovie: Boolean): Int? {
    if (title.isNullOrEmpty()) return 0
    val url = "${TMDBAPI}/search/multi?api_key=1865f43a0549ca50d341dd9ab8b29f49&query=" + URLEncoder.encode(title, "UTF-8")
    val json = JSONObject(app.get(url).text)
    val results = json.optJSONArray("results") ?: return null

    val targetType = if (isMovie) "movie" else "tv"

    for (i in 0 until results.length()) {
        val item = results.optJSONObject(i) ?: continue

        if (item.optString("media_type") != targetType) continue

        val resultTitle = if (isMovie)
            item.optString("title")
        else
            item.optString("name")

        if (resultTitle.equals(title, ignoreCase = true)) {
            return item.optInt("id")
        }
    }
    return null
}