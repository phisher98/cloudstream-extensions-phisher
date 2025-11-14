package com.hdhub4u

import android.annotation.SuppressLint
import com.google.gson.annotations.SerializedName
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject


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


@SuppressLint("NewApi")
fun encode(value: String): String {
    return String(android.util.Base64.decode(value, android.util.Base64.DEFAULT))
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

suspend fun loadSourceNameExtractor(
    source: String,
    url: String,
    referer: String? = null,
    quality: Int? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
) {
    loadExtractor(url, referer, subtitleCallback) { link ->
        CoroutineScope(Dispatchers.IO).launch {
            callback.invoke(
                newExtractorLink(
                    "${link.source} $source",
                    "${link.source} $source",
                    link.url,
                ) {
                    this.quality = quality ?: link.quality
                    this.type = link.type
                    this.referer = link.referer
                    this.headers = link.headers
                    this.extractorData = link.extractorData
                }
            )
        }
    }
}

data class IMDB(
    @SerializedName("imdb_id")
    val imdbId: String? = null
)