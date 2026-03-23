package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.JsonParser
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidHidePro
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI
import java.security.MessageDigest

class MultimoviesAIO: StreamWishExtractor() {
    override var name = "Multimovies Cloud AIO"
    override var mainUrl = "https://allinonedownloader.fun"
    override var requiresReferer = true
}

class Techinmind: GDMirrorbot() {
    override var name = "Techinmind Cloud AIO"
    override var mainUrl = "https://stream.techinmind.space"
    override var requiresReferer = true
}

class Iqsmartgames: GDMirrorbot() {
    override var name = "Iqsmartgames"
    override var mainUrl = "https://streams.iqsmartgames.com"
    override var requiresReferer = true
}

class Dhcplay: VidHidePro() {
    override var name = "DHC Play"
    override var mainUrl = "https://dhcplay.com"
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

class server1 : VidStack() {
    override var name = "MultimoviesVidstack"
    override var mainUrl = "https://server1.uns.bio"
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

class CdnwishCom : StreamWishExtractor() {
    override val name = "Cdnwish"
    override val mainUrl = "https://cdnwish.com"
    override val requiresReferer = true
}
open class GDMirrorbot : ExtractorApi() {
    override var name = "GDMirrorbot"
    override var mainUrl = "https://gdmirrorbot.nl"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val (sids, host) = extractSidsAndHost(url) ?: return

        if (host.isNullOrBlank()) {
            Log.e("Error:", "Host is null, aborting")
            return
        }

        sids.forEach { sid ->
            try {
                Log.d("Phisher", "Processing SID: $sid")

                val responseText = app.post(
                    "$host/embedhelper.php",
                    data = mapOf("sid" to sid),
                    headers = mapOf(
                        "Referer" to host,
                        "X-Requested-With" to "XMLHttpRequest"
                    )
                ).text

                val root = JsonParser.parseString(responseText)
                    .takeIf { it.isJsonObject }
                    ?.asJsonObject ?: return@forEach

                val siteUrls = root["siteUrls"]?.asJsonObject ?: return@forEach

                // Ensure defaults
                if (!siteUrls.has("gofs")) {
                    siteUrls.addProperty("GoFile", "https://gofile.io/d/")
                }
                if (!siteUrls.has("buzzheavier")) {
                    siteUrls.addProperty("buzzheavier", "https://buzzheavier.com/")
                }

                val siteFriendlyNames = root["siteFriendlyNames"]?.asJsonObject

                val decodedMresult = when {
                    root["mresult"]?.isJsonObject == true ->
                        root["mresult"].asJsonObject

                    root["mresult"]?.isJsonPrimitive == true -> {
                        try {
                            base64Decode(root["mresult"].asString)
                                .let { JsonParser.parseString(it).asJsonObject }
                        } catch (e: Exception) {
                            Log.e("Phisher", "Decode failed: $e")
                            return@forEach
                        }
                    }

                    else -> return@forEach
                }

                siteUrls.keySet().intersect(decodedMresult.keySet()).forEach { key ->
                    val base = siteUrls[key]?.asString?.trimEnd('/') ?: return@forEach
                    val path = decodedMresult[key]?.asString?.trimStart('/') ?: return@forEach

                    val fullUrl = "$base/$path"
                    val friendlyName = siteFriendlyNames?.get(key)?.asString ?: key

                    try {
                        Log.d("Sites:", "$friendlyName → $fullUrl")

                        when (friendlyName) {
                            "StreamHG", "EarnVids" ->
                                VidHidePro().getUrl(fullUrl, referer, subtitleCallback, callback)

                            "RpmShare", "UpnShare", "StreamP2p" ->
                                VidStack().getUrl(fullUrl, referer, subtitleCallback, callback)

                            else ->
                                loadExtractor(fullUrl, referer ?: mainUrl, subtitleCallback, callback)
                        }

                    } catch (e: Exception) {
                        Log.e("Error:", "Extractor failed: $friendlyName $e")
                    }
                }

            } catch (e: Exception) {
                Log.e("Error:", "SID failed: $sid $e")
            }
        }
    }

    // ------------------ CORE EXTRACTION ------------------

    private suspend fun extractSidsAndHost(url: String): Pair<List<String>, String?>? {
        return if (!url.contains("key=")) {
            val sid = url.substringAfterLast("embed/")
            val host = getBaseUrl(app.get(url).url)
            Pair(listOf(sid), host)
        } else {
            var pageText = app.get(url).text

            val finalId = Regex("""FinalID\s*=\s*"([^"]+)"""")
                .find(pageText)?.groupValues?.get(1)

            val myKey = Regex("""myKey\s*=\s*"([^"]+)"""")
                .find(pageText)?.groupValues?.get(1)

            val idType = Regex("""idType\s*=\s*"([^"]+)"""")
                .find(pageText)?.groupValues?.get(1) ?: "imdbid"

            val baseUrl = Regex("""let\s+baseUrl\s*=\s*"([^"]+)"""")
                .find(pageText)?.groupValues?.get(1)
                ?.takeIf { it.startsWith("http") }
                ?: Regex("""player_base\s*=\s*["']([^"']+)["']""")
                    .find(pageText)?.groupValues?.get(1)

            val host = baseUrl?.let { getBaseUrl(it) }

            if (finalId != null && myKey != null) {
                val apiUrl = if (url.contains("/tv/")) {
                    val season = Regex("""/tv/\d+/(\d+)/""")
                        .find(url)?.groupValues?.get(1) ?: "1"

                    val episode = Regex("""/tv/\d+/\d+/(\d+)""")
                        .find(url)?.groupValues?.get(1) ?: "1"

                    "$mainUrl/myseriesapi?tmdbid=$finalId&season=$season&epname=$episode&key=$myKey"
                } else {
                    "$mainUrl/mymovieapi?$idType=$finalId&key=$myKey"
                }

                pageText = app.get(apiUrl, referer = apiUrl).text
            }

            val json = JsonParser.parseString(pageText)
                .takeIf { it.isJsonObject }
                ?.asJsonObject ?: return null

            val dataArray = json["data"]?.asJsonArray

            val sids = dataArray
                ?.mapNotNull {
                    it.asJsonObject["fileslug"]?.asString
                }
                ?.filter { it.isNotBlank() }
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(url.substringAfterLast("/")) // fallback

            Pair(sids, host)
        }
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }
}

class Streamcasthub : ExtractorApi() {
    override var name = "Streamcasthub"
    override var mainUrl = "https://multimovies.streamcasthub.store"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id=url.substringAfterLast("/#")
        val m3u8= "https://ss1.rackcloudservice.cyou/ic/$id/master.txt"
        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                m3u8,
                ExtractorLinkType.M3U8
            )
            {
                this.referer = url
            }
        )
    }
}



class Strwishcom : StreamWishExtractor() {
    override val name = "Strwish"
    override val mainUrl = "https://strwish.com"
    override val requiresReferer = true
}

open class Gofile : ExtractorApi() {
    override val name = "Gofile"
    override val mainUrl = "https://gofile.io"
    override val requiresReferer = false
    private val mainApi = "https://api.gofile.io"
    private val browserLanguage = "en-GB"
    private val secret = "gf2026x"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = Regex("/(?:\\?c=|d/)([\\da-zA-Z-]+)").find(url)?.groupValues?.get(1) ?: return

        val token = app.post(
            "$mainApi/accounts",
        ).parsedSafe<AccountResponse>()?.data?.token ?: return

        val currentTimeSeconds = System.currentTimeMillis() / 1000
        val interval = (currentTimeSeconds / 14400).toString()
        val message = listOf(USER_AGENT, browserLanguage, token, interval, secret).joinToString("::")
        val hashedToken = sha256(message)

        val headers = mapOf(
            "Referer" to "$mainUrl/",
            "User-Agent" to USER_AGENT,
            "Authorization" to "Bearer $token",
            "X-BL" to browserLanguage,
            "X-Website-Token" to hashedToken
        )

        val parsedResponse = app.get(
            "$mainApi/contents/$id?contentFilter=&page=1&pageSize=1000&sortField=name&sortDirection=1",
            headers = headers
        ).parsedSafe<GofileResponse>()

        val childrenMap = parsedResponse?.data?.children ?: return

        for ((_, file) in childrenMap) {
            if (file.link.isNullOrEmpty() || file.type != "file") continue
            val fileName = file.name ?: ""
            val size = file.size ?: 0L
            val formattedSize = formatBytes(size)

            callback.invoke(
                newExtractorLink(
                    "Gofile",
                    "[Gofile] $fileName [$formattedSize]",
                    file.link,
                    ExtractorLinkType.VIDEO
                ) {
                    this.quality = getQuality(fileName)
                    this.headers = mapOf("Cookie" to "accountToken=$token")
                }
            )
        }
    }

    private fun getQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024L * 1024 * 1024 -> "%.2f MB".format(bytes.toDouble() / (1024 * 1024))
            else -> "%.2f GB".format(bytes.toDouble() / (1024 * 1024 * 1024))
        }
    }

    data class AccountResponse(
        @JsonProperty("data") val data: AccountData? = null
    )

    data class AccountData(
        @JsonProperty("token") val token: String? = null
    )

    data class GofileResponse(
        @JsonProperty("data") val data: GofileData? = null
    )

    data class GofileData(
        @JsonProperty("children") val children: Map<String, GofileFile>? = null
    )

    data class GofileFile(
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("link") val link: String? = null,
        @JsonProperty("size") val size: Long? = 0L
    )
}