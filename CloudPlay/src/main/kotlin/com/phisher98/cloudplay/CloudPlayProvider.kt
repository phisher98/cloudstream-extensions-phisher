package com.phisher98.cloudplay


import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.CLEARKEY_UUID
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.newDrmExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class CloudPlay : MainAPI() {
    override var lang = "en"
    override var mainUrl: String = base64Decode("aHR0cHM6Ly9ob3N0LmNsb3VkcGxheS5tZQ==")
    override var name = "CloudPlay"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.Live)

    private val apiHeaders = mapOf(
        "Connection" to "Keep-Alive",
        "User-Agent" to "okhttp/4.12.0",
        "X-Package" to base64Decode("Y29tLmNsb3VkcGxheS5hcHA=")
    )

    private fun generateSign(ts: Long): String {
        val key = base64Decode("amlvdHZwbHVz")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(ts.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun mainPhpUrl(): String {
        val ts = System.currentTimeMillis() / 1000L
        val sign = generateSign(ts)
        return "$mainUrl/main.php?ts=$ts&sign=$sign"
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val req = app.get(mainPhpUrl(), headers = apiHeaders)
        val res = req.parsedSafe<CloudPlayResponse>()
            ?: throw Error("Failed to parse main.php. Text: ${req.text}")

        val decryptedJson = decryptPayload(res.payload, res.iv, res.tag ?: "")
        val streams = parseJson<CloudPlayStreams>(decryptedJson).streams

        val homePageLists = mutableListOf<HomePageList>()
        streams.amap { stream ->
            val sections = fetchHomeSections(stream.name ?: "Unknown", stream.url, stream.logo)
            homePageLists.addAll(sections)
        }

        return newHomePageResponse(homePageLists)
    }


    private suspend fun fetchHomeSections(
        sectionName: String,
        url: String,
        fallbackLogo: String?
    ): List<HomePageList> {
        val isHost = url.contains(base64Decode("aG9zdC5jbG91ZHBsYXkubWU="))
        val headers = if (isHost) apiHeaders else emptyMap()

        val resText = app.get(url, headers = headers).text
        if (resText.isBlank()) return emptyList()

        // M3U playlist — direct channels, single section
        if (resText.startsWith("#EXTM3U")) {
            val channels = parseM3u(resText).map { channel ->
                val channelName = channel.name ?: "Unknown"
                val posterUrl = channel.logo?.takeIf { it.isNotEmpty() } ?: fallbackLogo ?: ""
                newLiveSearchResponse(channelName, channel.toJson(), TvType.Live) {
                    this.posterUrl = posterUrl
                }
            }
            return if (channels.isNotEmpty())
                listOf(HomePageList(sectionName, channels, isHorizontalImages = true))
            else emptyList()
        }

        // Try direct channel list (JSON array with m3u8_url / mpd_url)
        try {
            val channels = parseJson<List<CloudPlayChannel>>(resText)
            if (channels.isNotEmpty() && (channels[0].m3u8_url != null || channels[0].mpd_url != null)) {
                val shows = channels.map { channel ->
                    val channelName = channel.name ?: "Unknown"
                    val posterUrl = channel.logo ?: fallbackLogo ?: ""
                    newLiveSearchResponse(channelName, channel.toJson(), TvType.Live) {
                        this.posterUrl = posterUrl
                    }
                }
                return listOf(HomePageList(sectionName, shows, isHorizontalImages = true))
            }
        } catch (_: Exception) {}

        // Try sub-stream list — each sub-stream gets its OWN separate HomePageList
        try {
            val subStreams = parseJson<List<CloudPlayStream>>(resText)
            if (subStreams.isNotEmpty()) {
                val sections = mutableListOf<HomePageList>()
                subStreams.amap { subStream ->
                    val subSections = fetchHomeSections(
                        subStream.name ?: sectionName,
                        subStream.url,
                        subStream.logo ?: fallbackLogo
                    )
                    sections.addAll(subSections)
                }
                return sections
            }
        } catch (_: Exception) {}

        return emptyList()
    }

    private suspend fun fetchChannels(url: String, fallbackLogo: String?): List<SearchResponse> {
        val shows = mutableListOf<SearchResponse>()
        val isHost = url.contains(base64Decode("aG9zdC5jbG91ZHBsYXkubWU="))
        val headers = if (isHost) apiHeaders else emptyMap()

        val resText = app.get(url, headers = headers).text
        if (resText.isBlank()) return shows

        if (resText.startsWith("#EXTM3U")) {
            val m3uChannels = parseM3u(resText)
            return m3uChannels.map { channel ->
                val channelName = channel.name ?: "Unknown"
                val posterUrl = channel.logo?.takeIf { it.isNotEmpty() } ?: fallbackLogo ?: ""
                newLiveSearchResponse(channelName, channel.toJson(), TvType.Live) {
                    this.posterUrl = posterUrl
                }
            }
        }

        try {
            // First try parsing as a list of channels
            val channels = parseJson<List<CloudPlayChannel>>(resText)
            if (channels.isNotEmpty() && (channels[0].m3u8_url != null || channels[0].mpd_url != null)) {
                return channels.map { channel ->
                    val channelName = channel.name ?: "Unknown"
                    val posterUrl = channel.logo ?: fallbackLogo ?: ""
                    val data = channel.toJson()
                    newLiveSearchResponse(channelName, data, TvType.Live) {
                        this.posterUrl = posterUrl
                    }
                }
            }
        } catch (_: Exception) {
        }

        try {
            val subStreams = parseJson<List<CloudPlayStream>>(resText)
            if (subStreams.isNotEmpty()) {
                val allShows = subStreams.amap { subStream ->
                    fetchChannels(subStream.url, subStream.logo ?: fallbackLogo)
                }.flatten()
                shows.addAll(allShows)
            }
        } catch (_: Exception) {
        }

        return shows
    }

    private fun parseM3u(m3uText: String): List<CloudPlayChannel> {
        val lines = m3uText.split("\n")
        val channels = mutableListOf<CloudPlayChannel>()

        var currentName = ""
        var currentLogo = ""
        var currentGroup = ""
        var currentKey = ""
        var currentKeyId = ""
        var currentUserAgent = ""
        var currentReferer = ""

        for (line in lines) {
            val l = line.trim()
            if (l.startsWith("#EXTINF:")) {
                currentName = l.substringAfterLast(",").trim()
                currentLogo = Regex("""tvg-logo="([^"]+)"""").find(l)?.groupValues?.get(1) ?: ""
                currentGroup = Regex("""group-title="([^"]+)"""").find(l)?.groupValues?.get(1) ?: ""
                currentKey = Regex("""key="([^"]+)"""").find(l)?.groupValues?.get(1) ?: ""
                currentKeyId = Regex("""keyid="([^"]+)"""").find(l)?.groupValues?.get(1) ?: ""
            } else if (l.startsWith("#EXTVLCOPT:")) {
                val opt = l.substringAfter(":")
                if (opt.startsWith("http-user-agent=")) {
                    currentUserAgent = opt.substringAfter("=")
                } else if (opt.startsWith("http-referrer=")) {
                    currentReferer = opt.substringAfter("=")
                }
            } else if (!l.startsWith("#") && l.isNotEmpty()) {
                val urlParts = l.split("|")
                val rawUrl = urlParts[0]
                val params = if (urlParts.size > 1) urlParts[1] else ""

                val urlUserAgent = Regex("User-Agent=([^&]+)", RegexOption.IGNORE_CASE).find(params)?.groupValues?.get(1) ?: currentUserAgent
                val urlReferer = Regex("Referer=([^&]+)", RegexOption.IGNORE_CASE).find(params)?.groupValues?.get(1) ?: currentReferer

                val urlKey = Regex("key=([^&]+)", RegexOption.IGNORE_CASE).find(params)?.groupValues?.get(1) ?: currentKey
                val urlKeyId = Regex("keyid=([^&]+)", RegexOption.IGNORE_CASE).find(params)?.groupValues?.get(1) ?: currentKeyId

                val type = if (rawUrl.contains(".mpd")) "dash" else "hls"
                val licenseUrl = if (urlKey.isNotEmpty() && urlKeyId.isNotEmpty()) {
                    "https://dummy.com/?keyid=$urlKeyId&key=$urlKey"
                } else ""

                val headersMap = mutableMapOf<String, String>()
                if (urlUserAgent.isNotEmpty()) headersMap["User-Agent"] = urlUserAgent
                if (urlReferer.isNotEmpty()) headersMap["Referer"] = urlReferer

                channels.add(CloudPlayChannel(
                    type = type,
                    id = null,
                    name = currentName,
                    group = currentGroup,
                    logo = currentLogo,
                    user_agent = urlUserAgent,
                    m3u8_url = if (type == "hls") rawUrl else null,
                    mpd_url = if (type == "dash") rawUrl else null,
                    license_url = licenseUrl,
                    headers = headersMap
                ))

                // reset
                currentName = ""
                currentLogo = ""
                currentGroup = ""
                currentKey = ""
                currentKeyId = ""
                currentUserAgent = ""
                currentReferer = ""
            }
        }
        return channels
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val res = app.get(mainPhpUrl(), headers = apiHeaders)
            .parsedSafe<CloudPlayResponse>() ?: return emptyList()

        val decryptedJson = decryptPayload(res.payload, res.iv, res.tag ?: "")
        val streams = parseJson<CloudPlayStreams>(decryptedJson).streams

        val allChannels = streams.amap { stream ->
            fetchChannels(stream.url, stream.logo)
        }.flatten()

        return allChannels.filter { it.name.contains(query, ignoreCase = true) }
    }

    override suspend fun load(url: String): LoadResponse {
        val data = parseJson<CloudPlayChannel>(url)
        val title = data.name ?: "Unknown"
        val poster = data.logo ?: ""

        return newLiveStreamLoadResponse(title, url, url) {
            this.posterUrl = poster
            this.plot = data.group
        }
    }

    private fun String.hexToBase64Url(): String {
        val normalizedHex = trim().replace("-", "")
        if (normalizedHex.isEmpty() || normalizedHex.length % 2 != 0 || !normalizedHex.matches(Regex("^[0-9a-fA-F]+$"))) {
            return this
        }
        return try {
            val bytes = normalizedHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
        } catch (_: Exception) {
            this
        }
    }

    private suspend fun getMpdStream(url: String, customHeaders: Map<String, String>?): String {
        return app.get(url, headers = customHeaders ?: emptyMap()).text
    }

    private suspend fun getDRMKeysFromLicenseServer(url: String, kid: String): String {
        val userAgent = "Dalvik/2.1.0 (Linux; U; Android)"
        val responseString = app.post(
            url,
            headers = mapOf(
                "User-Agent" to userAgent,
                "Content-Type" to "application/json;charset=UTF-8"
            ),
            json = mapOf("kids" to listOf(kid), "type" to "temporary")
        ).text

        return try {
            val jsonResponse = parseJson<Map<String, Any>>(responseString)
            @Suppress("UNCHECKED_CAST")
            val keys = jsonResponse["keys"] as? List<Map<String, String>> ?: return ""
            keys.firstOrNull { it["kid"] == kid }?.get("k") ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val channel = parseJson<CloudPlayChannel>(data)

        if (channel.mpd_url != null) {
            val licenseUrl = channel.license_url ?: ""
            var keyStr = ""
            var kidStr = ""
            if (licenseUrl.contains("keyid=") && licenseUrl.contains("key=")) {
                kidStr = Regex("keyid=([^&]+)").find(licenseUrl)?.groupValues?.get(1)?.hexToBase64Url() ?: ""
                keyStr = Regex("key=([^&]+)").find(licenseUrl)?.groupValues?.get(1)?.hexToBase64Url() ?: ""
            } else if (licenseUrl.isNotEmpty()) {
                val mpdStr = getMpdStream(channel.mpd_url, channel.headers)
                val regex = Regex("""cenc:default_KID=["']([0-9a-fA-F\-]{36})["']""")
                val matchResult = regex.find(mpdStr)
                val drmKid = matchResult?.groupValues?.get(1) ?: UUID.randomUUID().toString()
                kidStr = drmKid.hexToBase64Url()
                keyStr = getDRMKeysFromLicenseServer(licenseUrl, kidStr)
            }

            callback.invoke(
                newDrmExtractorLink(
                    this.name,
                    channel.name ?: "DASH",
                    channel.mpd_url,
                    INFER_TYPE,
                    if (kidStr.isNotEmpty() && keyStr.isNotEmpty()) CLEARKEY_UUID else UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")
                ) {
                    val headersMap = mutableMapOf<String, String>()
                    if (channel.headers != null) {
                        headersMap.putAll(channel.headers)
                    }
                    if (!channel.user_agent.isNullOrEmpty()) {
                        headersMap["User-Agent"] = channel.user_agent
                    } else {
                        headersMap["User-Agent"] = base64Decode("aHR0cHM6Ly90Lm1lL2Nsb3VkcGx5IHx8IEBjbG91ZHBsYXk=")
                    }
                    if (headersMap.isNotEmpty()) {
                        this.headers = headersMap
                    }
                    if (kidStr.isNotEmpty() && keyStr.isNotEmpty()) {
                        this.kid = kidStr
                        this.key = keyStr
                    } else if (licenseUrl.isNotEmpty()) {
                        this.licenseUrl = licenseUrl
                    }
                }
            )
        } else if (channel.m3u8_url != null) {
            val isTs = channel.m3u8_url.contains(".ts", ignoreCase = true)
            callback.invoke(
                newExtractorLink(
                    this.name,
                    channel.name ?: "HLS",
                    channel.m3u8_url,
                    if (isTs) ExtractorLinkType.VIDEO else ExtractorLinkType.M3U8
                ) {
                    val headersMap = mutableMapOf<String, String>()
                    if (channel.headers != null) {
                        headersMap.putAll(channel.headers)
                    }
                    if (!channel.user_agent.isNullOrEmpty()) {
                        headersMap["User-Agent"] = channel.user_agent
                    } else {
                        headersMap["User-Agent"] = base64Decode("aHR0cHM6Ly90Lm1lL2Nsb3VkcGx5IHx8IEBjbG91ZHBsYXk=")
                    }
                    if (headersMap.isNotEmpty()) {
                        this.headers = headersMap
                    }
                    channel.headers?.amap { (key, value) ->
                        if (key.equals("referer", ignoreCase = true)) {
                            this.referer = value
                        }
                    }
                }
            )
        }

        return true
    }

    private fun decryptPayload(payloadBase64: String, ivBase64: String, tagBase64: String): String {
        val kString = base64Decode("amlvdHZwbHVz")
        val digest = MessageDigest.getInstance("SHA-256")
        val keyHash = digest.digest(kString.toByteArray(Charsets.UTF_8))

        val ivBytes = base64DecodeArray(ivBase64)

        // AES-GCM: doFinal expects ciphertext || tag concatenated
        val cipherBytes = base64DecodeArray(payloadBase64)
        val tagBytes = if (tagBase64.isNotEmpty()) base64DecodeArray(tagBase64) else byteArrayOf()
        val cipherWithTag = cipherBytes + tagBytes

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyHash, "AES"), GCMParameterSpec(128, ivBytes))

        val decrypted = cipher.doFinal(cipherWithTag)
        return String(decrypted, Charsets.UTF_8)
    }

    data class CloudPlayResponse(
        val payload: String,
        val iv: String,
        val tag: String?,
        val ts: Long?,
        val expires: Long?
    )

    data class CloudPlayStreams(
        val streams: List<CloudPlayStream>
    )

    data class CloudPlayStream(
        val name: String?,
        val url: String,
        val logo: String?
    )

    data class CloudPlayChannel(
        val type: String?,
        val id: String?,
        val name: String?,
        val group: String?,
        val logo: String?,
        val user_agent: String?,
        val m3u8_url: String?,
        val mpd_url: String?,
        val license_url: String?,
        val headers: Map<String, String>?
    )
}
