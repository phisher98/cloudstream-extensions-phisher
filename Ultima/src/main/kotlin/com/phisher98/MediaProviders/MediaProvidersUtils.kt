package com.phisher98

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import com.phisher98.UltimaUtils.Category
import com.phisher98.UltimaUtils.LinkData
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.Gson
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.Jeniusplay
import com.lagradost.cloudstream3.extractors.Mp4Upload
import com.lagradost.cloudstream3.extractors.Rabbitstream
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.extractors.Vidplay
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.phisher98.UltimaMediaProvidersUtils.ServerName.Hubcloud
import com.phisher98.UltimaMediaProvidersUtils.commonLinkLoader
import com.phisher98.UltimaMediaProvidersUtils.getIndexQuality
import okhttp3.FormBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

abstract class MediaProvider {
    abstract val name: String
    abstract val domain: String
    abstract val categories: List<Category>

    abstract suspend fun loadContent(
            url: String,
            data: LinkData,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    )

    companion object {
        var keys: Keys? = null
    }

    open suspend fun getKeys(): Keys {
        if (keys == null) {
            keys =
                    app.get("https://rowdy-avocado.github.io/multi-keys/").parsedSafe<Keys>()
                            ?: throw Exception("Unable to fetch keys")
        }
        return keys!!
    }

    data class Keys(
            @JsonProperty("chillx") val chillx: List<String>,
            @JsonProperty("aniwave") val aniwave: List<Step>,
            @JsonProperty("cinezone") val cinezone: List<Step>,
            @JsonProperty("vidplay") val vidplay: List<String>
    ) {
        data class Step(
                @JsonProperty("sequence") val sequence: Int,
                @JsonProperty("method") val method: String,
                @JsonProperty("keys") val keys: List<String>? = null
        )
    }
}

object UltimaMediaProvidersUtils {
    val mediaProviders =
            listOf(
                    NowTvMediaProvider(),
                    DahmerMoviesMediaProvider(),
                    NoverseMediaProvider(),
                    AllMovielandMediaProvider(),
                    TwoEmbedMediaProvider(),
                    MultiEmbededAPIProvider(),
                    MultiMoviesProvider(),
                    MoviesDriveProvider(),
                    HiAnimeMediaProvider(),
                    VidFlastProvider(),
                    ElevenmoviesProvider()
            )

    suspend fun invokeExtractors(
            category: Category,
            data: LinkData,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        UltimaStorageManager.currentMediaProviders.toList().amap {
            val provider = it.getProvider()
            if (provider.categories.contains(category) && it.enabled) {
                try {
                    provider.loadContent(it.getDomain(), data, subtitleCallback, callback)
                } catch (e: Exception) {}
            }
        }
    }

    enum class ServerName {
        MyCloud,
        Mp4upload,
        Streamtape,
        Vidplay,
        Filemoon,
        Jeniusplay,
        Uqload,
        StreamWish,
        Vidhide,
        DoodStream,
        Gogo,
        MDrive,
        Megacloud,
        Filelions,
        Zoro,
        Custom,
        Hubcloud,
        GDFlix,
        Gofile,
        NONE
    }

    fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }

    fun getEpisodeSlug(
            season: Int? = null,
            episode: Int? = null,
    ): Pair<String, String> {
        return if (season == null && episode == null) {
            "" to ""
        } else {
            (if (season!! < 10) "0$season" else "$season") to
                    (if (episode!! < 10) "0$episode" else "$episode")
        }
    }

    fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: Qualities.Unknown.value
    }

    fun getIndexQualityTags(str: String?, fullTag: Boolean = false): String {
        return if (fullTag)
                Regex("(?i)(.*)\\.(?:mkv|mp4|avi)").find(str ?: "")?.groupValues?.get(1)?.trim()
                        ?: str ?: ""
        else
                Regex("(?i)\\d{3,4}[pP]\\.?(.*?)\\.(mkv|mp4|avi)")
                        .find(str ?: "")
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.replace(".", " ")
                        ?.trim()
                        ?: str ?: ""
    }

    fun String.encodeUrl(): String {
        val url = URL(this)
        val uri = URI(url.protocol, url.userInfo, url.host, url.port, url.path, url.query, url.ref)
        return uri.toURL().toString()
    }

    fun String?.createSlug(): String? {
        return this?.filter { it.isWhitespace() || it.isLetterOrDigit() }
                ?.trim()
                ?.replace("\\s+".toRegex(), "-")
                ?.lowercase()
    }

    fun fixUrl(url: String, domain: String): String {
        if (url.startsWith("http")) {
            return url
        }
        if (url.isEmpty()) {
            return ""
        }

        val startsWithNoHttp = url.startsWith("//")
        if (startsWithNoHttp) {
            return "https:$url"
        } else {
            if (url.startsWith('/')) {
                return domain + url
            }
            return "$domain/$url"
        }
    }

    // #region - Main Link Handler
    @SuppressLint("NewApi")
    suspend fun commonLinkLoader(
        providerName: String?,
        serverName: ServerName?,
        url: String,
        referer: String?,
        dubStatus: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        quality: Int = Qualities.Unknown.value,
        tag: String? = null
    ) {
        try {
            val domain = referer ?: getBaseUrl(url)
            when (serverName) {
                ServerName.Vidplay ->
                        AnyVidplay(providerName, dubStatus, domain)
                                .getUrl(url, domain, subtitleCallback, callback)
                ServerName.MyCloud ->
                        AnyMyCloud(providerName, dubStatus, domain)
                                .getUrl(url, domain, subtitleCallback, callback)
                ServerName.Filemoon ->
                        AnyFileMoon(providerName, dubStatus, domain)
                                .getUrl(url, null, subtitleCallback, callback)
                ServerName.Mp4upload ->
                        AnyMp4Upload(providerName, dubStatus, domain)
                                .getUrl(url, domain, subtitleCallback, callback)
                ServerName.Jeniusplay ->
                        AnyJeniusplay(providerName, dubStatus, domain)
                                .getUrl(url, domain, subtitleCallback, callback)
                ServerName.Uqload ->
                        AnyUqload(providerName, dubStatus, domain)
                                .getUrl(url, domain, subtitleCallback, callback)
                ServerName.StreamWish ->
                        AnyStreamwish(providerName, dubStatus, domain)
                                .getUrl(url, domain, subtitleCallback, callback)
                ServerName.Vidhide ->
                        AnyVidhide(providerName, dubStatus, domain)
                                .getUrl(url, domain, subtitleCallback, callback)
                ServerName.DoodStream ->
                        AnyDoodExtractor(providerName, dubStatus, domain)
                                .getUrl(url, domain, subtitleCallback, callback)
                ServerName.Megacloud ->
                        AnyMegacloud(providerName, dubStatus, domain)
                                .getUrl(url, domain, subtitleCallback, callback)
                ServerName.Gofile ->
                    AnyGofile(providerName, dubStatus, domain)
                        .getUrl(url, domain, subtitleCallback, callback)
                Hubcloud ->
                    AnyHubCloud(providerName, dubStatus, domain)
                                .getUrl(url, domain, subtitleCallback, callback)
                ServerName.GDFlix ->
                    AnyGDFlix(providerName, dubStatus, domain)
                        .getUrl(url, domain, subtitleCallback, callback)
                ServerName.Filelions ->
                        AnyFilelions(providerName, dubStatus, domain)
                                .getUrl(url, domain, subtitleCallback, callback)
                ServerName.Zoro ->
                        ZoroExtractor(providerName, dubStatus, domain)
                                .getUrl(url, domain, subtitleCallback, callback)
                ServerName.Custom -> {
                    providerName?.let { it ->
                        newExtractorLink(
                            it,
                            tag?.let { "$providerName: $it" } ?: providerName,
                            url,
                            INFER_TYPE
                        )
                        {
                            this.quality = quality
                        }
                    }?.let {
                        callback.invoke(
                            it
                        )
                    }
                }
                else -> {
                    loadExtractor(url, subtitleCallback, callback)
                }
            }
        } catch (_: Exception) {}
    }
    // #endregion - Main Link Handler
}

// #region - Custom Extractors
class AnyFileMoon(provider: String?, dubType: String?, domain: String = "") : Filesim() {
    override val name =
            (if (provider != null) "$provider: " else "") +
                    "Filemoon" +
                    (if (dubType != null) ": $dubType" else "")
    override val mainUrl = domain
    override val requiresReferer = false
}

@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
class AnyMyCloud(provider: String?, dubType: String?, domain: String = "") : Vidplay() {
    override val name =
            (if (provider != null) "$provider: " else "") +
                    "MyCloud" +
                    (if (dubType != null) ": $dubType" else "")
    override val mainUrl = domain
    override val requiresReferer = false

    override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val encIFrameUrl = app.get(url).url.split("#").getOrNull(1) ?: return
        val fileLink = Base64.UrlSafe.decode(encIFrameUrl).toString(Charsets.UTF_8)
        callback.invoke(
            newExtractorLink(
                name,
                name,
                fileLink,
                INFER_TYPE
            )
            {
                quality = Qualities.Unknown.value
            }
        )
    }
}

class AnyVidplay(provider: String?, dubType: String?, domain: String = "") : Vidplay() {
    override val name =
            (if (provider != null) "$provider: " else "") +
                    "Vidplay" +
                    (if (dubType != null) ": $dubType" else "")
    override val mainUrl = domain
    override val requiresReferer = false

    override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val iFramePage = app.get(url, referer = referer).document
        val jsData = iFramePage.selectFirst("script:containsData(jwplayer)") ?: return
        val fileLink = Regex("""file": `(.*)`""").find(jsData.html())?.groupValues?.get(1) ?: return
        newExtractorLink(
            name,
            name,
            fileLink,
            INFER_TYPE
        )
        {
            quality = Qualities.Unknown.value
        }
    }
}

class AnyMp4Upload(provider: String?, dubType: String?, domain: String = "") : Mp4Upload() {
    override var name =
            (if (provider != null) "$provider: " else "") +
                    "Mp4Upload" +
                    (if (dubType != null) ": $dubType" else "")
    override var mainUrl = domain
    override val requiresReferer = false
}

class AnyJeniusplay(provider: String?, dubType: String?, domain: String = "") : Jeniusplay() {
    override var name =
            (if (provider != null) "$provider: " else "") +
                    "JeniusPlay" +
                    (if (dubType != null) ": $dubType" else "")
    override var mainUrl = domain
    override val requiresReferer = false
}

class AnyUqload(provider: String?, dubType: String?, domain: String = "") : Filesim() {
    override val name =
            (if (provider != null) "$provider: " else "") +
                    "Uqloads" +
                    (if (dubType != null) ": $dubType" else "")
    override val mainUrl = domain
    override val requiresReferer = false
}

class AnyStreamwish(provider: String?, dubType: String?, domain: String = "") :
        StreamWishExtractor() {
    override var name =
            (if (provider != null) "$provider: " else "") +
                    "SteamWish" +
                    (if (dubType != null) ": $dubType" else "")
    override var mainUrl = domain
    override val requiresReferer = false
}

class AnyVidhide(provider: String?, dubType: String?, domain: String = "") : VidhideExtractor() {
    override var name =
            (if (provider != null) "$provider: " else "") +
                    "Vidhide" +
                    (if (dubType != null) ": $dubType" else "")
    override var mainUrl = domain
    override val requiresReferer = false
}

class AnyDoodExtractor(provider: String?, dubType: String?, domain: String = "") :
        DoodLaExtractor() {
    override var name =
            (if (provider != null) "$provider: " else "") +
                    "DoodStream" +
                    (if (dubType != null) ": $dubType" else "")
    override var mainUrl = domain
    override val requiresReferer = false
}


class AnyHubCloud(provider: String?, dubType: String?, domain: String = "") : ExtractorApi() {
    override var name =
        (if (provider != null) "$provider: " else "") +
                "Hub-Cloud" +
                (if (dubType != null) ": $dubType" else "")
    override var mainUrl = domain
    override val requiresReferer = false

        override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
        ) {
            val realUrl = try {
                val originalUrl = URL(url)
                val parts = originalUrl.host.split(".").toMutableList()
                if (parts.size > 1) {
                    parts[parts.lastIndex] = "dad"
                    URL(originalUrl.protocol, parts.joinToString("."), originalUrl.port, originalUrl.file).toString()
                } else url
            } catch (e: Exception) {
                Log.e("HubCloud", "Invalid URL: ${e.message}")
                return
            }

            val href = if ("hubcloud.php" in realUrl) {
                realUrl
            } else {
                val scriptData = app.get(realUrl).document
                    .selectFirst("script:containsData(url)")?.toString().orEmpty()
                Regex("var url = '([^']*)'").find(scriptData)?.groupValues?.getOrNull(1).orEmpty()
            }

            if (href.isBlank()) {
                Log.w("HubCloud", "No valid href found")
                return
            }

            val document = app.get(href).document
            val size = document.selectFirst("i#size")?.text().orEmpty()
            val header = document.selectFirst("div.card-header")?.text().orEmpty()

            val headerDetails = cleanTitle(header)

            val labelExtras = buildString {
                if (headerDetails.isNotEmpty()) append("[$headerDetails]")
                if (size.isNotEmpty()) append("[$size]")
            }
            val quality = getIndexQuality(header)

            document.select("div.card-body h2 a.btn").amap { element ->
                val link = element.attr("href")
                val text = element.text()
                val baseUrl = getBaseUrl(link)

                when {
                    text.contains("FSL Server", ignoreCase = true) -> {
                        callback.invoke(
                            newExtractorLink(
                                "$name [FSL Server] $labelExtras",
                                "$name [FSL Server] $labelExtras",
                                link,
                            ) { this.quality = quality }
                        )
                    }

                    text.contains("Download File", ignoreCase = true) -> {
                        callback.invoke(
                            newExtractorLink(
                                "$name $labelExtras",
                                "$name $labelExtras",
                                link,
                            ) { this.quality = quality }
                        )
                    }

                    text.contains("BuzzServer", ignoreCase = true) -> {
                        val buzzResp = app.get("$link/download", referer = link, allowRedirects = false)
                        val dlink = buzzResp.headers["hx-redirect"].orEmpty()
                        if (dlink.isNotBlank()) {
                            callback.invoke(
                                newExtractorLink(
                                    "$name [BuzzServer] $labelExtras",
                                    "$name [BuzzServer] $labelExtras",
                                    baseUrl + dlink,
                                ) { this.quality = quality }
                            )
                        } else {
                            Log.w("HubCloud", "BuzzServer: No redirect")
                        }
                    }

                    "pixeldra" in link -> {
                        callback.invoke(
                            newExtractorLink(
                                "$name Pixeldrain $labelExtras",
                                "$name Pixeldrain $labelExtras",
                                link,
                            ) { this.quality = quality }
                        )
                    }

                    text.contains("S3 Server", ignoreCase = true) -> {
                        callback.invoke(
                            newExtractorLink(
                                "$name S3 Server $labelExtras",
                                "$name S3 Server $labelExtras",
                                link,
                            ) { this.quality = quality }
                        )
                    }

                    text.contains("10Gbps", ignoreCase = true) -> {
                        var currentLink = link
                        var redirectUrl: String?

                        while (true) {
                            val response = app.get(currentLink, allowRedirects = false)
                            redirectUrl = response.headers["location"]
                            if (redirectUrl == null) {
                                Log.e("HubCloud", "10Gbps: No redirect")
                                break
                            }
                            if ("id=" in redirectUrl) break
                            currentLink = redirectUrl
                        }

                        val finalLink = redirectUrl?.substringAfter("link=") ?: return@amap
                        callback.invoke(
                            newExtractorLink(
                                "$name [Download] $labelExtras",
                                "$name [Download] $labelExtras",
                                finalLink,
                            ) { this.quality = quality }
                        )
                    }

                    else -> {
                        loadExtractor(link, "", subtitleCallback, callback)
                    }
                }
            }
        }

        private fun getIndexQuality(str: String?): Int {
            return Regex("(\\d{3,4})[pP]").find(str.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: Qualities.P2160.value
        }

        private fun getBaseUrl(url: String): String {
            return try {
                URI(url).let { "${it.scheme}://${it.host}" }
            } catch (e: Exception) {
                ""
            }
        }

    private fun cleanTitle(title: String): String {
        val parts = title.split(".", "-", "_")

        val qualityTags = listOf(
            "WEBRip", "WEB-DL", "WEB", "BluRay", "HDRip", "DVDRip", "HDTV",
            "CAM", "TS", "R5", "DVDScr", "BRRip", "BDRip", "DVD", "PDTV",
            "HD"
        )

        val audioTags = listOf(
            "AAC", "AC3", "DTS", "MP3", "FLAC", "DD5", "EAC3", "Atmos"
        )

        val subTags = listOf(
            "ESub", "ESubs", "Subs", "MultiSub", "NoSub", "EnglishSub", "HindiSub"
        )

        val codecTags = listOf(
            "x264", "x265", "H264", "HEVC", "AVC"
        )

        val startIndex = parts.indexOfFirst { part ->
            qualityTags.any { tag -> part.contains(tag, ignoreCase = true) }
        }

        val endIndex = parts.indexOfLast { part ->
            subTags.any { tag -> part.contains(tag, ignoreCase = true) } ||
                    audioTags.any { tag -> part.contains(tag, ignoreCase = true) } ||
                    codecTags.any { tag -> part.contains(tag, ignoreCase = true) }
        }

        return if (startIndex != -1 && endIndex != -1 && endIndex >= startIndex) {
            parts.subList(startIndex, endIndex + 1).joinToString(".")
        } else if (startIndex != -1) {
            parts.subList(startIndex, parts.size).joinToString(".")
        } else {
            parts.takeLast(3).joinToString(".")
        }
    }

}



class AnyMegacloud(provider: String?, dubType: String?, domain: String = "") : Rabbitstream() {
    override var name =
            (if (provider != null) "$provider: " else "") +
                    "Megacloud" +
                    (if (dubType != null) ": $dubType" else "")
    override var mainUrl = domain

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = url.substringAfterLast("/").substringBefore("?")
        val apiUrl = "$mainUrl/embed-2/v2/e-1/getSources?id=$id"
        val gson = Gson()

        val response = try {
            val json = app.get(apiUrl, referer = url).text
            gson.fromJson(json, MegacloudResponse::class.java)
        } catch (e: Exception) {
            Log.e("Megacloud", "Failed to parse MegacloudResponse: ${e.message}")
            null
        } ?: return

        val encoded = response.sources
        val key = try {
            val keyJson = app.get("https://raw.githubusercontent.com/yogesh-hacker/MegacloudKeys/refs/heads/main/keys.json").text
            gson.fromJson(keyJson, Megakey::class.java)?.mega
        } catch (e: Exception) {
            Log.e("Megacloud", "Failed to parse Megakey: ${e.message}")
            null
        }

        val decoded = key?.let { decryptOpenSSL(encoded, it) }
        val m3u8 = decoded?.let {
            val sourceList = parseSourceJson(it)
            sourceList.firstOrNull()?.file
        }

        if (m3u8 != null) {
            val m3u8headers = mapOf(
                "Referer" to "https://megacloud.club/",
                "Origin" to "https://megacloud.club/"
            )

            try {
                M3u8Helper.generateM3u8(name, m3u8, mainUrl, headers = m3u8headers).forEach(callback)
            } catch (e: Exception) {
                Log.e("Megacloud", "Error generating M3U8: ${e.message}")
            }
        }

        response.tracks.forEach { track ->
            if (track.kind == "captions" || track.kind == "subtitles") {
                subtitleCallback(
                    SubtitleFile(
                        track.label,
                        track.file
                    )
                )
            }
        }
    }

    data class MegacloudResponse(
        val sources: String,
        val tracks: List<Track>,
        val encrypted: Boolean,
        val intro: Intro,
        val outro: Outro,
        val server: Long,
    )

    data class Track(
        val file: String,
        val label: String,
        val kind: String,
        val default: Boolean?,
    )

    data class Intro(
        val start: Long,
        val end: Long,
    )

    data class Outro(
        val start: Long,
        val end: Long,
    )


    data class Megakey(
        val rabbit: String,
        val mega: String,
    )


    data class Source2(
        val file: String,
        val type: String,
    )

    private fun parseSourceJson(json: String): List<Source2> {
        val list = mutableListOf<Source2>()
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val file = obj.getString("file")
                val type = obj.getString("type")
                list.add(Source2(file, type))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun opensslKeyIv(password: ByteArray, salt: ByteArray, keyLen: Int = 32, ivLen: Int = 16): Pair<ByteArray, ByteArray> {
        var d = ByteArray(0)
        var d_i = ByteArray(0)
        while (d.size < keyLen + ivLen) {
            val md = MessageDigest.getInstance("MD5")
            d_i = md.digest(d_i + password + salt)
            d += d_i
        }
        return Pair(d.copyOfRange(0, keyLen), d.copyOfRange(keyLen, keyLen + ivLen))
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun decryptOpenSSL(encBase64: String, password: String): String {
        try {
            val data = java.util.Base64.getDecoder().decode(encBase64)
            require(data.copyOfRange(0, 8).contentEquals("Salted__".toByteArray()))
            val salt = data.copyOfRange(8, 16)
            val (key, iv) = opensslKeyIv(password.toByteArray(), salt)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val secretKey = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

            val decrypted = cipher.doFinal(data.copyOfRange(16, data.size))
            return String(decrypted)
        } catch (e: Exception) {
            Log.e("DecryptOpenSSL", "Decryption failed: ${e.message}")
            return "Decryption Error"
        }
    }
}

class AnyFilelions(provider: String?, dubType: String?, domain: String = "") : VidhideExtractor() {
    override var name =
            (if (provider != null) "$provider: " else "") +
                    "Filelions" +
                    (if (dubType != null) ": $dubType" else "")
    override var mainUrl = domain
    override val requiresReferer = false
}

class ZoroExtractor(provider: String?, dubType: String?, domain: String = "") : ExtractorApi() {
    override var name =
            (if (provider != null) "$provider: " else "") +
                    "Zoro" +
                    (if (dubType != null) ": $dubType" else "")
    override var mainUrl = domain
    override val requiresReferer = false

    data class ZoroJson(
            @JsonProperty("tracks") val subtitles: List<Subtitle>,
            @JsonProperty("sources") val sources: List<Source>
    ) {
        data class Subtitle(
                @JsonProperty("file") val file: String,
                @JsonProperty("label") val lang: String? = null,
                @JsonProperty("kind") val kind: String,
        )

        data class Source(
                @JsonProperty("file") val file: String,
                @JsonProperty("type") val type: String
        )
    }

    override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val iFramePage = app.get(url, referer = referer).document
        val jsData =
                iFramePage
                        .selectFirst("script:containsData(JsonData)")
                        ?.html()
                        ?.split("=")
                        ?.getOrNull(1)
                        ?: return
        val jsonData = AppUtils.parseJson<ZoroJson>(jsData)
        jsonData.subtitles.amap { sub ->
            sub.lang?.let { subtitleCallback.invoke(SubtitleFile(it, sub.file)) }
        }
        jsonData.sources.amap { source ->
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    source.file,
                    INFER_TYPE
                )
                {
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }
}

class AnyGDFlix(provider: String?, dubType: String?, domain: String = "") : ExtractorApi() {
    override var name =
        (if (provider != null) "$provider: " else "") +
                "GDFlix" +
                (if (dubType != null) ": $dubType" else "")
    override var mainUrl = domain
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val newUrl = try {
            app.get(url)
                .document
                .selectFirst("meta[http-equiv=refresh]")
                ?.attr("content")
                ?.substringAfter("url=")
        } catch (e: Exception) {
            Log.e("Error", "Failed to fetch redirect: ${e.localizedMessage}")
            return
        } ?: url

        val document = app.get(newUrl).document
        val fileName = document.select("ul > li.list-group-item:contains(Name)").text()
            .substringAfter("Name : ")
        val fileSize = document.select("ul > li.list-group-item:contains(Size)").text()
            .substringAfter("Size : ")

        document.select("div.text-center a").amap { anchor ->
            val text = anchor.select("a").text()

            when {
                text.contains("DIRECT DL",ignoreCase = true) -> {
                    val link = anchor.attr("href")
                    callback.invoke(
                        newExtractorLink(name, "$name GDFlix[Direct] [$fileSize]", link) {
                            this.quality = getIndexQuality(fileName)
                        }
                    )
                }

                text.contains("Index Links",ignoreCase = true) -> {
                    try {
                        val link = anchor.attr("href")
                        app.get("https://new6.gdflix.dad$link").document
                            .select("a.btn.btn-outline-info").amap { btn ->
                                val serverUrl = "https://new6.gdflix.dad" + btn.attr("href")
                                app.get(serverUrl).document
                                    .select("div.mb-4 > a").amap { sourceAnchor ->
                                        val sourceurl = sourceAnchor.attr("href")
                                        callback.invoke(
                                            newExtractorLink(name, "$name GDFlix[Index] [$fileSize]", sourceurl) {
                                                this.quality = getIndexQuality(fileName)
                                            }
                                        )
                                    }
                            }
                    } catch (e: Exception) {
                        Log.d("Index Links", e.toString())
                    }
                }

                text.contains("DRIVEBOT",ignoreCase = true) -> {
                    try {
                        val driveLink = anchor.attr("href")
                        val id = driveLink.substringAfter("id=").substringBefore("&")
                        val doId = driveLink.substringAfter("do=").substringBefore("==")
                        val baseUrls = listOf("https://drivebot.sbs", "https://drivebot.cfd")

                        baseUrls.amap { baseUrl ->
                            val indexbotLink = "$baseUrl/download?id=$id&do=$doId"
                            val indexbotResponse = app.get(indexbotLink, timeout = 100L)

                            if (indexbotResponse.isSuccessful) {
                                val cookiesSSID = indexbotResponse.cookies["PHPSESSID"]
                                val indexbotDoc = indexbotResponse.document

                                val token = Regex("""formData\.append\('token', '([a-f0-9]+)'\)""")
                                    .find(indexbotDoc.toString())?.groupValues?.get(1).orEmpty()

                                val postId = Regex("""fetch\('/download\?id=([a-zA-Z0-9/+]+)'""")
                                    .find(indexbotDoc.toString())?.groupValues?.get(1).orEmpty()

                                val requestBody = FormBody.Builder()
                                    .add("token", token)
                                    .build()

                                val headers = mapOf("Referer" to indexbotLink)
                                val cookies = mapOf("PHPSESSID" to "$cookiesSSID")

                                val downloadLink = app.post(
                                    "$baseUrl/download?id=$postId",
                                    requestBody = requestBody,
                                    headers = headers,
                                    cookies = cookies,
                                    timeout = 100L
                                ).text.let {
                                    Regex("url\":\"(.*?)\"").find(it)?.groupValues?.get(1)?.replace("\\", "").orEmpty()
                                }

                                callback.invoke(
                                    newExtractorLink("$referer GDFlix[DriveBot]", "$referer GDFlix[DriveBot] [$fileSize]", downloadLink) {
                                        this.referer = baseUrl
                                        this.quality = getIndexQuality(fileName)
                                    }
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.d("DriveBot", e.toString())
                    }
                }

                text.contains("Instant DL",ignoreCase = true) -> {
                    try {
                        val instantLink = anchor.attr("href")
                        val link = app.get(instantLink, allowRedirects = false)
                            .headers["location"]?.substringAfter("url=").orEmpty()

                        callback.invoke(
                            newExtractorLink(name, "$name GDFlix[Instant Download] [$fileSize]", link) {
                                this.quality = getIndexQuality(fileName)
                            }
                        )
                    } catch (e: Exception) {
                        Log.d("Instant DL", e.toString())
                    }
                }

                text.contains("CLOUD DOWNLOAD",ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink(name, "$name GDFlix[CLOUD] [$fileSize]", anchor.attr("href")) {
                            this.quality = getIndexQuality(fileName)
                        }
                    )
                }

                text.contains("GoFile",ignoreCase = true) -> {
                    try {
                        app.get(anchor.attr("href")).document
                            .select(".row .row a").amap { gofileAnchor ->
                                val link = gofileAnchor.attr("href")
                                if (link.contains("gofile")) {
                                    commonLinkLoader(
                                        name,
                                        UltimaMediaProvidersUtils.ServerName.Gofile,
                                        link,
                                        null,
                                        null,
                                        subtitleCallback,
                                        callback
                                    )
                                }
                            }
                    } catch (e: Exception) {
                        Log.d("Gofile", e.toString())
                    }
                }

                text.contains("PixelDrain",ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink(
                            "Pixeldrain",
                            "$name GDFlix[Pixeldrain] [$fileSize]",
                            anchor.attr("href"),
                        ) { this.quality = quality }
                    )
                }

                else -> {
                    Log.d("Error", "No Server matched")
                }
            }
        }

        // Cloudflare backup links
        try {
            val types = listOf("type=1", "type=2")
            types.map { type ->
                val sourceurl = app.get("${newUrl.replace("file", "wfile")}?$type")
                    .document.select("a.btn-success").attr("href")

                if (referer?.isNotEmpty() == true) {
                    callback.invoke(
                        newExtractorLink(name, "$name GDFlix[CF] [$fileSize]", sourceurl) {
                            this.quality = getIndexQuality(fileName)
                        }
                    )
                }
            }
        } catch (e: Exception) {
            Log.d("CF", e.toString())
        }
    }
}

class AnyGofile(provider: String?, dubType: String?, domain: String = "") : ExtractorApi() {
    override var name =
        (if (provider != null) "$provider: " else "") +
                "Gofile" +
                (if (dubType != null) ": $dubType" else "")
    override var mainUrl = domain
    override val requiresReferer = false
    private val mainApi = "https://api.gofile.io"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        try {
            val id = Regex("/(?:\\?c=|d/)([\\da-zA-Z-]+)").find(url)?.groupValues?.get(1) ?: return
            val responseText = app.post("$mainApi/accounts").text
            val json = JSONObject(responseText)
            val token = json.getJSONObject("data").getString("token")

            val globalJs = app.get("$mainUrl/dist/js/global.js").text
            val wt = Regex("""appdata\.wt\s*=\s*["']([^"']+)["']""")
                .find(globalJs)?.groupValues?.getOrNull(1) ?: return

            val responseTextfile = app.get(
                "$mainApi/contents/$id?wt=$wt",
                headers = mapOf("Authorization" to "Bearer $token")
            ).text

            val fileDataJson = JSONObject(responseTextfile)

            val data = fileDataJson.getJSONObject("data")
            val children = data.getJSONObject("children")
            val firstFileId = children.keys().asSequence().first()
            val fileObj = children.getJSONObject(firstFileId)

            val link = fileObj.getString("link")
            val fileName = fileObj.getString("name")
            val fileSize = fileObj.getLong("size")

            val sizeFormatted = if (fileSize < 1024L * 1024 * 1024) {
                "%.2f MB".format(fileSize / 1024.0 / 1024)
            } else {
                "%.2f GB".format(fileSize / 1024.0 / 1024 / 1024)
            }

            callback.invoke(
                newExtractorLink(
                    "Gofile",
                    "$name Gofile [$sizeFormatted]",
                    link
                ) {
                    this.quality = getQuality(fileName)
                    this.headers = mapOf("Cookie" to "accountToken=$token")
                }
            )
        } catch (e: Exception) {
            Log.e("Gofile", "Error occurred: ${e.message}")
        }
    }

    private fun getQuality(fileName: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(fileName ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }
}

// #endregion - Custom Extractors
