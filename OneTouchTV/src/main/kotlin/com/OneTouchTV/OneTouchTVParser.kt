package com.OneTouchTV

import com.fasterxml.jackson.annotation.JsonProperty
import org.json.JSONArray
import org.json.JSONObject

data class OneTouchTVParser(
    val day: List<Day>? = emptyList(),
    val week: List<Week>? = emptyList(),
    val month: List<Month>? = emptyList()
) {
    data class Day(
        val _id: String? = null,
        val id: String? = null,
        val title: String? = null,
        val image: String? = null,
        val country: String? = null,
        val type: String? = null,
        val year: String? = null,
        val popularity: Int = 0,
        val status: String? = null,
        val releaseDate: String? = null,
        val isSub: Boolean = false
    )

    data class Week(
        val _id: String? = null,
        val id: String? = null,
        val title: String? = null,
        val image: String? = null,
        val country: String? = null,
        val type: String? = null,
        val year: String? = null,
        val popularity: Int = 0,
        val status: String? = null,
        val releaseDate: String? = null,
        val isSub: Boolean = false
    )

    data class Month(
        val _id: String? = null,
        val id: String? = null,
        val title: String? = null,
        val image: String? = null,
        val country: String? = null,
        val type: String? = null,
        val year: String? = null,
        val popularity: Int = 0,
        val status: String? = null,
        val releaseDate: String? = null,
        val isSub: Boolean = false
    )
}

data class SourceItem(
    val type: String?,
    val contentId: String?,
    val id: String?,
    val name: String?,
    val quality: String?,
    val url: String?,
    val headers: Map<String, String>
)

data class TrackItem(
    val file: String?,
    val name: String?,
    val isDefault: Boolean,
    val kind: String?,
    val format: String?
)

fun parseSourcesAndTracks(
    decryptedJson: String,
    subtitleCallback: (TrackItem) -> Unit = {},
    extractorCallback: (SourceItem) -> Unit = {}
): Pair<List<SourceItem>, List<TrackItem>> {
    val sourcesList = mutableListOf<SourceItem>()
    val tracksList = mutableListOf<TrackItem>()
    val root = JSONObject(decryptedJson)
    val result = if (root.has("result")) root.optJSONObject("result") else root
    val sourcesArray: JSONArray? = result?.optJSONArray("sources")
    if (sourcesArray != null) {
        for (i in 0 until sourcesArray.length()) {
            val s = sourcesArray.optJSONObject(i) ?: continue
            val headersMap = mutableMapOf<String, String>()
            val headersObj = s.optJSONObject("headers")
            if (headersObj != null) {
                val keys = headersObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = headersObj.optString(k, "")
                    headersMap[k] = v
                }
            }
            val sourceItem = SourceItem(
                type = s.optString("type", ""),
                contentId = s.optString("contentId", ""),
                id = s.optString("id", ""),
                name = s.optString("name", ""),
                quality = s.optString("quality", ""),
                url = s.optString("url", ""),
                headers = headersMap
            )
            sourcesList.add(sourceItem)
            extractorCallback(sourceItem)
        }
    }
    val tracksArray: JSONArray? = result?.optJSONArray("track") ?: result?.optJSONArray("tracks")
    if (tracksArray != null) {
        for (i in 0 until tracksArray.length()) {
            val t = tracksArray.optJSONObject(i) ?: continue
            val trackItem = TrackItem(
                file = t.optString("file", ""),
                name = t.optString("name", ""),
                isDefault = t.optBoolean("default", false),
                kind = t.optString("kind", ""),
                format = t.optString("format", "")
            )
            tracksList.add(trackItem)
            subtitleCallback(trackItem)
        }
    }
    return Pair(sourcesList, tracksList)
}


data class Search(
    val status: Long,
    val result: List<SearchResult>,
)

data class SearchResult(
    val id: String,
    val loklokContentId: String,
    val isSub: Boolean,
    val title: String,
    val image: String,
    val type: String,
    val year: String,
    val source: String,
    val status: String,
    val loklokCategory: Long,
    val episodes: List<Any?>,
    val description: String,
    val genres: List<String>,
    val otherTitles: List<String>,
)

data class MediaResult(
    val randomSlideShow: List<RandomSlideShow>?,
    val recents: List<Recent>?,
    val result: ResultWrapper?
)

data class ResultWrapper(
    val randomSlideShow: List<RandomSlideShow>?,
    val recents: List<Recent>?
)

data class RandomSlideShow(
    @JsonProperty("_id") val id: String?,
    @JsonProperty("id") val id2: String?,
    val title: String?,
    val image: String?,
    val country: String?,
    val type: String?,
    val year: String?,
    val popularity: Long?,
    val description: String?,
    val status: String?,
    val releaseDate: String?,
    val isSub: Boolean?
)

data class Recent(
    @JsonProperty("_id") val id: String?,
    @JsonProperty("id") val id2: String?,
    val title: String?,
    val image: String?,
    val country: String?,
    val type: String?,
    val year: String?,
    val popularity: Long?,
    val description: String?,
    val status: String?,
    val releaseDate: String?,
    val isSub: Boolean?
)

data class CleanMedia(
    val id: String?,
    val title: String?,
    val image: String?,
    val country: String?,
    val type: String?,
    val year: String?,
    val status: String?,
    val isSub: Boolean?
)