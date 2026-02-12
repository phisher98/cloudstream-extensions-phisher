package com.watch32

import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.app
import com.watch32.Watch32.Companion.TMDBAPI
import com.watch32.Watch32.Companion.TMDBIMAGEBASEURL
import org.json.JSONObject
import java.net.URLEncoder

suspend fun fetchtmdb(title: String, isMovie: Boolean): Int? {
    val url =
        "$TMDBAPI/search/multi?api_key=1865f43a0549ca50d341dd9ab8b29f49&query=" +
                URLEncoder.encode(title, "UTF-8")

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


fun parseCredits(jsonText: String?): List<ActorData> {
    if (jsonText.isNullOrBlank()) return emptyList()
    val list = ArrayList<ActorData>()
    val root = JSONObject(jsonText)
    val castArr = root.optJSONArray("cast") ?: return list
    for (i in 0 until castArr.length()) {
        val c = castArr.optJSONObject(i) ?: continue
        val name = c.optString("name").takeIf { it.isNotBlank() } ?: c.optString("original_name").orEmpty()
        val profile = c.optString("profile_path").takeIf { it.isNotBlank() }?.let { "$TMDBIMAGEBASEURL$it" }
        val character = c.optString("character").takeIf { it.isNotBlank() }
        val actor = Actor(name, profile)
        list += ActorData(actor, roleString = character)
    }
    return list
}