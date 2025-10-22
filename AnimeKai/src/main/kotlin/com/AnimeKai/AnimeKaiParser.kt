package com.AnimeKai

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper

@JsonIgnoreProperties(ignoreUnknown = true)
data class Image(
    @JsonProperty("coverType") val coverType: String?,
    @JsonProperty("url") val url: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Episode(
    @JsonProperty("episode") val episode: String?,
    @JsonProperty("airdate") val airdate: String?,
    @JsonProperty("runtime") val runtime: Int?,     // Keeping only one field
    @JsonProperty("image") val image: String?,
    @JsonProperty("title") val title: Map<String, String>?,
    @JsonProperty("overview") val overview: String?,
    @JsonProperty("rating") val rating: String?,
    @JsonProperty("finaleType") val finaleType: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaAnimeData(
    @JsonProperty("titles") val titles: Map<String, String>?,
    @JsonProperty("images") val images: List<Image>?,
    @JsonProperty("episodes") val episodes: Map<String, Episode>?
)

fun parseAnimeData(jsonString: String): MetaAnimeData? {
    return try {
        val objectMapper = ObjectMapper()
        objectMapper.readValue(jsonString, MetaAnimeData::class.java)
    } catch (_: Exception) {
        null // Return null for invalid JSON instead of crashing
    }
}


