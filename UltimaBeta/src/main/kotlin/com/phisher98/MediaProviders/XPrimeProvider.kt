package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink

class XPrimeProvider : MediaProvider() {
    override val name = "XPrimeProvider"
    override val domain = "https://xprime.tv"
    override val categories = listOf(UltimaUtils.Category.MEDIA)

    override suspend fun loadContent(
        url: String,
        data: UltimaUtils.LinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val backendAPI = getDomains()?.xprime ?: return
        val servers = app.get("$backendAPI/servers").parsedSafe<XprimeServers>() ?: return

        val objectMapper = ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        servers.servers.forEach { server ->
            if (server.status != "ok") return@forEach

            val baseUrl = "$backendAPI/${server.name}"
            val queryParams = buildString {
                append("?name=${data.title.orEmpty()}")
                when (server.name) {
                    "primebox" -> {
                        if (data.year != null) append("&fallback_year=${data.year}")
                        if (data.season != null && data.episode != null) append("&season=${data.season}&episode=${data.episode}")
                    }
                    else -> {
                        if (data.year != null) append("&year=${data.year}")
                        if (!data.imdbId.isNullOrBlank()) append("&id=${data.tmdbId}&imdb=${data.imdbId}")
                        if (data.season != null && data.episode != null) append("&season=${data.season}&episode=${data.episode}")
                    }
                }
            }

            val finalUrl = baseUrl + queryParams

            try {
                val response = app.get(finalUrl)
                val json = response.text
                val serverLabel = "Xprime ${server.name.replaceFirstChar { it.uppercaseChar() }}"

                if (server.name == "primebox") {
                    val stream = objectMapper.readValue<XprimeStream>(json)
                    val streamsJson = objectMapper.readTree(json).get("streams")

                    stream.qualities.forEach { quality ->
                        val href = streamsJson?.get(quality)?.textValue()
                        if (!href.isNullOrBlank()) {
                            callback(
                                newExtractorLink(
                                    source = serverLabel,
                                    name = serverLabel,
                                    url = href,
                                    type = ExtractorLinkType.VIDEO
                                ) {
                                    this.quality = getQualityFromName(quality)
                                    this.headers = mapOf("Origin" to domain)
                                    this.referer = domain
                                }
                            )
                        }
                    }

                    if (stream.hasSubtitles) {
                        stream.subtitles.forEach { subtitle ->
                            val subUrl = subtitle.file.orEmpty()
                            if (subUrl.isNotBlank()) {
                                subtitleCallback(
                                    SubtitleFile(
                                        lang = subtitle.label ?: "Unknown",
                                        url = subUrl
                                    )
                                )
                            }
                        }
                    }
                } else {
                    val href = objectMapper.readTree(json).get("url")?.textValue().orEmpty()
                    if (href.isNotBlank()) {
                        callback(
                            newExtractorLink(
                                source = serverLabel,
                                name = serverLabel,
                                url = href,
                                type = ExtractorLinkType.M3U8
                            ) {
                                this.headers = mapOf("Origin" to domain)
                                this.referer = domain
                                this.quality = Qualities.Unknown.value
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("XPrimeAPI", "Error on server ${server.name} $e")
            }
        }
    }
    data class XprimeServers(
        val servers: List<XprimeServer1>,
    )

    data class XprimeServer1(
        val name: String,
        val status: String,
        val language: String,
    )


    data class XprimeStream(
        @JsonProperty("available_qualities") val qualities: List<String>,
        @JsonProperty("status") val status: String,
        @JsonProperty("has_subtitles") val hasSubtitles: Boolean,
        @JsonProperty("subtitles") val subtitles: List<XprimePrimeSubs>
    )

    data class XprimePrimeSubs(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
    )
}