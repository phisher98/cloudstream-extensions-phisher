package com.fourKHDHub

import android.util.Base64
import com.fourKHDHub.FourKHDHub.Companion.TMDBAPI
import com.fourKHDHub.FourKHDHub.Companion.TMDBIMAGEBASEURL
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import org.json.JSONObject
import java.net.URLEncoder

suspend fun getRedirectLinks(url: String): String {
    val doc = app.get(url).toString()
    val regex = "s\\('o','([A-Za-z0-9+/=]+)'|ck\\('_wp_http_\\d+','([^']+)'".toRegex()
    val combinedString = buildString {
        regex.findAll(doc).forEach { matchResult ->
            val extractedValue = matchResult.groups[1]?.value ?: matchResult.groups[2]?.value
            if (!extractedValue.isNullOrEmpty()) append(extractedValue)
        }
    }
    return try {
        val decodedString = base64Decode(pen(base64Decode(base64Decode(combinedString))))
        val jsonObject = JSONObject(decodedString)
        val encodedurl = base64Decode(jsonObject.optString("o", "")).trim()
        val data = encode(jsonObject.optString("data", "")).trim()
        val wphttp1 = jsonObject.optString("blog_url", "").trim()
        val directlink = runCatching {
            app.get("$wphttp1?re=$data".trim()).documentLarge.select("body").text().trim()
        }.getOrDefault("").trim()

        encodedurl.ifEmpty { directlink }
    } catch (e: Exception) {
        Log.e("Error:", "Error processing links $e")
        "" // Return an empty string on failure
    }
}


fun encode(value: String): String {
    return String(Base64.decode(value, Base64.DEFAULT))
}

fun pen(value: String): String {
    return value.map {
        when (it) {
            in 'A'..'Z' -> ((it - 'A' + 13) % 26 + 'A'.code).toChar()
            in 'a'..'z' -> ((it - 'a' + 13) % 26 + 'a'.code).toChar()
            else -> it
        }
    }.joinToString("")
}

suspend fun fetchtmdb(title: String): Int? {
    val url =
        "$TMDBAPI/search/multi?api_key=98ae14df2b8d8f8f8136499daf79f0e0&query=" + URLEncoder.encode(
            title,
            "UTF-8"
        )
    val json = JSONObject(app.get(url).text)
    val results = json.optJSONArray("results") ?: return null
    val t = title.lowercase()
    for (i in 0 until results.length()) {
        val obj = results.optJSONObject(i) ?: continue
        val name = obj.optString(
            "name",
            obj.optString(
                "title",
                obj.optString(
                    "original_name",
                    obj.optString("original_title", "")
                )
            )
        ).lowercase().replace("-"," ")
        if (name.contains(t)) return obj.optInt("id")
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