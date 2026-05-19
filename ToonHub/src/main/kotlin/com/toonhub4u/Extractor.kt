package com.toonhub4u

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidHidePro
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import java.net.URI

class MultimoviesAIO: StreamWishExtractor() {
    override var name = "Multimovies Cloud AIO"
    override var mainUrl = "https://allinonedownloader.fun"
    override var requiresReferer = true
}


class Dhtpre : VidhideExtractor() {
    override var name = "Animezia"
    override var mainUrl = "https://dhtpre.com"
    override var requiresReferer = true
}

class Multimovies: StreamWishExtractor() {
    override var name = "Multimovies Cloud"
    override var mainUrl = "https://multimovies.cloud"
    override var requiresReferer = true
}

class Animezia : VidhideExtractor() {
    override var name = "Animezia"
    override var mainUrl = "https://animezia.cloud"
    override var requiresReferer = true
}

class server2 : VidhideExtractor() {
    override var name = "Multimovies Vidhide"
    override var mainUrl = "https://server2.shop"
    override var requiresReferer = true
}

class Asnwish : StreamWishExtractor() {
    override val name = "Streanwish Asn"
    override val mainUrl = "https://asnwish.com"
    override val requiresReferer = true
}

class Multimoviesshg : Filesim() {
    override var name = "Multimoviesshg Vidhide"
    override var mainUrl = "https://multimoviesshg.com"
    override val requiresReferer = true
}

class CdnwishCom : StreamWishExtractor() {
    override val name = "Cdnwish"
    override val mainUrl = "https://cdnwish.com"
    override val requiresReferer = true
}

open class GDMirrorbot : ExtractorApi() {
    override var name = "GDMirrorbot"
    override var mainUrl = "https://gdmirrorbot.nl"
    override val requiresReferer = true

    data class EmbedResponse(
        @JsonProperty("data")
        val data: List<EmbedData> = emptyList()
    )

    data class EmbedData(
        @JsonProperty("fileslug")
        val fileslug: String? = null
    )

    data class HelperResponse(
        @JsonProperty("siteUrls")
        val siteUrls: Map<String, String> = emptyMap(),

        @JsonProperty("siteFriendlyNames")
        val siteFriendlyNames: Map<String, String> = emptyMap(),

        @JsonProperty("mresult")
        val mresult: JsonNode? = null
    )

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val (sid, host) = if (!url.contains("key=")) {
            Pair(
                url.substringAfterLast("embed/"),
                getBaseUrl(app.get(url).url)
            )
        } else {
            var pageText = app.get(url).text

            val finalId = Regex("""FinalID\s*=\s*"([^"]+)"""")
                .find(pageText)
                ?.groupValues?.get(1)

            val myKey = Regex("""myKey\s*=\s*"([^"]+)"""")
                .find(pageText)
                ?.groupValues?.get(1)

            val idType = Regex("""idType\s*=\s*"([^"]+)"""")
                .find(pageText)
                ?.groupValues?.get(1)
                ?: "imdbid"

            val baseUrl = Regex("""let\s+baseUrl\s*=\s*"([^"]+)"""")
                .find(pageText)
                ?.groupValues?.get(1)

            val hostUrl = baseUrl?.let(::getBaseUrl)

            if (finalId != null && myKey != null) {
                val apiUrl = if (url.contains("/tv/")) {
                    val season = Regex("""/tv/\d+/(\d+)/""")
                        .find(url)
                        ?.groupValues?.get(1)
                        ?: "1"

                    val episode = Regex("""/tv/\d+/\d+/(\d+)""")
                        .find(url)
                        ?.groupValues?.get(1)
                        ?: "1"

                    "$mainUrl/myseriesapi?tmdbid=$finalId&season=$season&epname=$episode&key=$myKey"
                } else {
                    "$mainUrl/mymovieapi?$idType=$finalId&key=$myKey"
                }

                pageText = app.get(apiUrl).text
            }

            val parsed = parseJson<EmbedResponse>(pageText)

            val embedId = url.substringAfterLast("/")

            val sidValue = parsed.data
                .firstOrNull()
                ?.fileslug
                ?.takeIf { it.isNotBlank() }
                ?: embedId

            Pair(sidValue, hostUrl)
        }

        val response = app.post(
            "$host/embedhelper.php",
            data = mapOf("sid" to sid)
        ).parsedSafe<HelperResponse>() ?: return

        val decodedMresult = when {
            response.mresult?.isObject == true -> {
                response.mresult
            }

            response.mresult?.isTextual == true -> {
                try {
                    parseJson<JsonNode>(
                        base64Decode(response.mresult.asText())
                    )
                } catch (e: Exception) {
                    Log.e("Phisher", "Failed to decode mresult: $e")
                    return
                }
            }

            else -> return
        }

        response.siteUrls.keys
            .intersect(decodedMresult.fieldNames().asSequence().toSet())
            .forEach { key ->

                val base = response.siteUrls[key]
                    ?.trimEnd('/')
                    ?: return@forEach

                val path = decodedMresult[key]
                    ?.asText()
                    ?.trimStart('/')
                    ?: return@forEach

                val fullUrl = "$base/$path"

                val friendlyName = response.siteFriendlyNames[key] ?: key

                try {
                    Log.d("Phisher", "$friendlyName $fullUrl")

                    when (friendlyName) {
                        "StreamHG", "EarnVids" -> {
                            VidHidePro().getUrl(
                                fullUrl,
                                referer,
                                subtitleCallback,
                                callback
                            )
                        }

                        "RpmShare", "UpnShare", "StreamP2p" -> {
                            VidStack().getUrl(
                                fullUrl,
                                referer,
                                subtitleCallback,
                                callback
                            )
                        }

                        else -> {
                            loadExtractor(
                                fullUrl,
                                referer ?: mainUrl,
                                subtitleCallback,
                                callback
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(
                        "Phisher",
                        "Failed to extract from $friendlyName at $fullUrl: $e"
                    )
                }
            }
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }
}