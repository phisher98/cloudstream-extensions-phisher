package com.hdhub4u

import android.annotation.SuppressLint
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.annotations.SerializedName
import com.lagradost.api.Log
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Score
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

fun cleanTitle(raw: String): String {
    val name = raw.substringBefore("(").trim()
        .replace(Regex("""\s+"""), " ") // collapse extra spaces
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    val seasonRegex = Regex("""Season\s*\d+""", RegexOption.IGNORE_CASE)
    val yearRegex = Regex("""\b(19|20)\d{2}\b""")

    val season = seasonRegex.find(raw)?.value?.replaceFirstChar { it.uppercase() }
    val year = yearRegex.find(raw)?.value

    val parts = mutableListOf<String>()
    if (season != null) parts += season
    if (year != null) parts += year

    return if (parts.isEmpty()) {
        name
    } else {
        name + parts.joinToString("") { " ($it)" }
    }
}


data class ResponseDataLocal(val meta: MetaLocal?)

data class MetaLocal(
    val name: String? = null,
    val description: String? = null,
    val actorsData: List<ActorData>? = null,
    val year: String? = null,
    val background: String? = null,
    val genres: List<String>? = null,
    val videos: List<VideoLocal>? = null,
    val rating: Score?,
    val logo: String?
)
data class VideoLocal(
    val title: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val overview: String? = null,
    val thumbnail: String? = null,
    val released: String? = null,
    val rating: Score?
)

data class Search(
    @JsonProperty("facet_counts")
    val facetCounts: List<Any?>,
    val found: Long,
    val hits: List<Hit>,
    @JsonProperty("out_of")
    val outOf: Long,
    val page: Long,
    @JsonProperty("request_params")
    val requestParams: RequestParams,
    @JsonProperty("search_cutoff")
    val searchCutoff: Boolean,
    @JsonProperty("search_time_ms")
    val searchTimeMs: Long,
)

data class Hit(
    val document: Document,
    val highlight: Map<String, Any>,
    val highlights: List<Any?>,
    @JsonProperty("text_match")
    val textMatch: Long,
    @JsonProperty("text_match_info")
    val textMatchInfo: TextMatchInfo,
)

data class Document(
    val category: List<String>,
    val id: String,
    val permalink: String,
    @JsonProperty("post_date")
    val postDate: String,
    @JsonProperty("post_thumbnail")
    val postThumbnail: String,
    @JsonProperty("post_title")
    val postTitle: String,
    @JsonProperty("post_type")
    val postType: String,
    @JsonProperty("sort_by_date")
    val sortByDate: Long,
)

data class TextMatchInfo(
    @JsonProperty("best_field_score")
    val bestFieldScore: String,
    @JsonProperty("best_field_weight")
    val bestFieldWeight: Long,
    @JsonProperty("fields_matched")
    val fieldsMatched: Long,
    @JsonProperty("num_tokens_dropped")
    val numTokensDropped: Long,
    val score: String,
    @JsonProperty("tokens_matched")
    val tokensMatched: Long,
    @JsonProperty("typo_prefix_score")
    val typoPrefixScore: Long,
)

data class RequestParams(
    @JsonProperty("collection_name")
    val collectionName: String,
    @JsonProperty("first_q")
    val firstQ: String,
    @JsonProperty("per_page")
    val perPage: Long,
    val q: String,
)
