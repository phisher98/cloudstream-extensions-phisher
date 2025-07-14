package com.phisher98

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newDrmExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.io.InputStream
import java.util.UUID

class IPTVPlayer : MainAPI() {
    override var lang = "hi"
    override var mainUrl = "https://raw.githubusercontent.com/phisher98/TVVVV/main/15APR2024.m3u"
    override var name = "IPTV Player"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(
        TvType.Live,
    )

    override suspend fun getMainPage(
        page: Int,
        request : MainPageRequest
    ): HomePageResponse {
        val data = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        return newHomePageResponse(data.items.groupBy{it.attributes["group-title"]}.map { group ->
            val title = group.key ?: ""
            val show = group.value.map { channel ->
                val streamurl = channel.url.toString()
                val channelname = channel.title.toString()
                val posterurl = channel.attributes["tvg-logo"].toString()
                val nation = channel.attributes["group-title"].toString()
                val key=channel.attributes["key"].toString()
                val keyid=channel.attributes["keyid"].toString()
                newLiveSearchResponse(channelname, LoadData(streamurl, channelname, posterurl, nation, key, keyid).toJson(), TvType.Live)
                {
                    this.posterUrl=posterurl
                    this.apiName
                    this.lang=channel.attributes["group-title"]
                }
            }
            HomePageList(
                title,
                show,
                isHorizontalImages = true
            )
        })
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val data = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        return data.items.filter { it.title?.contains(query,ignoreCase = true) ?: false }.map { channel ->
                val streamurl = channel.url.toString()
                val channelname = channel.title.toString()
                val posterurl = channel.attributes["tvg-logo"].toString()
                val nation = channel.attributes["group-title"].toString()
                val key=channel.attributes["key"].toString()
                val keyid=channel.attributes["keyid"].toString()
            newLiveSearchResponse(channelname, LoadData(streamurl, channelname, posterurl, nation, key, keyid).toJson(), TvType.Live)
            {
                this.posterUrl=posterurl
                this.apiName
                this.lang=channel.attributes["group-title"]
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val data = parseJson<LoadData>(url)
        return newLiveStreamLoadResponse(data.title,data.url,url)
        {
            this.posterUrl=data.poster
            this.plot=data.nation
        }
    }
    data class LoadData(
        val url: String,
        val title: String,
        val poster: String,
        val nation: String,
        val key: String,
        val keyid: String,
    )
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<LoadData>(data)
        if (loadData.url.contains("mpd"))
        {
            callback.invoke(
                newDrmExtractorLink(
                    this.name,
                    this.name,
                    loadData.url,
                    INFER_TYPE,
                    UUID.randomUUID()
                )
                {
                    this.quality=Qualities.Unknown.value
                    this.key=loadData.key.trim()
                    this.kid=loadData.keyid.trim()
                }
            )
        }
            else
        if(loadData.url.contains("&e=.m3u"))
            {
                callback.invoke(
                    newExtractorLink(
                        this.name,
                        this.name,
                        url = loadData.url,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = ""
                        this.quality = Qualities.Unknown.value
                    }
                )

            }
        else
        {
            callback.invoke(
                newExtractorLink(
                    this.name,
                    loadData.title,
                    url = loadData.url,
                    INFER_TYPE
                ) {
                    this.referer = ""
                    this.quality = Qualities.Unknown.value
                }
            )

        }
        return true
    }
}


data class Playlist(
    val items: List<PlaylistItem> = emptyList(),
)

data class PlaylistItem(
    val title: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val url: String? = null,
    val userAgent: String? = null,
    val key: String? = null,
    val keyid: String? = null,
)


class IptvPlaylistParser {


    /**
     * Parse M3U8 string into [Playlist]
     *
     * @param content M3U8 content string.
     * @throws PlaylistParserException if an error occurs.
     */
    fun parseM3U(content: String): Playlist {
        return parseM3U(content.byteInputStream())
    }

    /**
     * Parse M3U8 content [InputStream] into [Playlist]
     *
     * @param input Stream of input data.
     * @throws PlaylistParserException if an error occurs.
     */
    @Throws(PlaylistParserException::class)
    fun parseM3U(input: InputStream): Playlist {
        val reader = input.bufferedReader()

        if (!reader.readLine().isExtendedM3u()) {
            throw PlaylistParserException.InvalidHeader()
        }

        val playlistItems = mutableListOf<PlaylistItem>()

        var currentTitle: String? = null
        var currentAttributes: Map<String, String> = emptyMap()
        var currentUserAgent: String? = null
        var currentReferrer: String? = null
        var currentHeaders: Map<String, String> = emptyMap()

        reader.forEachLine { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) return@forEachLine

            when {
                trimmedLine.startsWith(EXT_INF) -> {
                    currentTitle = trimmedLine.getTitle()
                    currentAttributes = trimmedLine.getAttributes()
                    // Reset fields in case the last item was incomplete
                    currentUserAgent = null
                    currentReferrer = null
                    currentHeaders = emptyMap()
                }

                trimmedLine.startsWith(EXT_VLC_OPT) -> {
                    val userAgent = trimmedLine.getTagValue("http-user-agent")
                    val referrer = trimmedLine.getTagValue("http-referrer")
                    currentUserAgent = userAgent ?: currentUserAgent
                    currentReferrer = referrer ?: currentReferrer
                    if (currentReferrer != null) {
                        currentHeaders = currentHeaders + mapOf("referrer" to currentReferrer!!)
                    }
                }

                !trimmedLine.startsWith("#") -> {
                    val url = trimmedLine.getUrl()
                    val uaParam = trimmedLine.getUrlParameter("user-agent")
                    val refParam = trimmedLine.getUrlParameter("referer")
                    val key = trimmedLine.getUrlParameter("key")
                    val keyid = trimmedLine.getUrlParameter("keyid")

                    val combinedUserAgent = uaParam ?: currentUserAgent
                    val ref = refParam ?: currentReferrer

                    val urlHeaders = if (ref != null) {
                        currentHeaders + mapOf("referrer" to ref)
                    } else currentHeaders

                    if (currentTitle != null) {
                        playlistItems.add(
                            PlaylistItem(
                                currentTitle ?: "",
                                attributes = currentAttributes,
                                url = url,
                                userAgent = combinedUserAgent,
                                headers = urlHeaders,
                                key = key,
                                keyid = keyid
                            )
                        )
                    }

                    // Reset state for next block
                    currentTitle = null
                    currentAttributes = emptyMap()
                    currentUserAgent = null
                    currentReferrer = null
                    currentHeaders = emptyMap()
                }
            }
        }

        return Playlist(playlistItems)
    }
    /**
     * Replace "" (quotes) from given string.
     */
    private fun String.replaceQuotesAndTrim(): String {
        return replace("\"", "").trim()
    }

    /**
     * Check if given content is valid M3U8 playlist.
     */
    private fun String.isExtendedM3u(): Boolean = startsWith(EXT_M3U)

    /**
     * Get title of media.
     *
     * Example:-
     *
     * Input:
     * ```
     * #EXTINF:-1 tvg-id="1234" group-title="Kids" tvg-logo="url/to/logo", Title
     * ```
     * Result: Title
     */
    private fun String.getTitle(): String? {
        return split(",").lastOrNull()?.replaceQuotesAndTrim()
    }

    /**
     * Get media url.
     *
     * Example:-
     *
     * Input:
     * ```
     * https://example.com/sample.m3u8|user-agent="Custom"
     * ```
     * Result: https://example.com/sample.m3u8
     */
    private fun String.getUrl(): String? {
        return split("|").firstOrNull()?.replaceQuotesAndTrim()
    }

    /**
     * Get url parameters.
     *
     * Example:-
     *
     * Input:
     * ```
     * http://192.54.104.122:8080/d/abcdef/video.mp4|User-Agent=Mozilla&Referer=CustomReferrer
     * ```
     * Result will be equivalent to kotlin map:
     * ```Kotlin
     * mapOf(
     *   "User-Agent" to "Mozilla",
     *   "Referer" to "CustomReferrer"
     * )
     * ```
     */
  /*  private fun String.getUrlParameters(): Map<String, String> {
        val urlRegex = Regex("^(.*)\\|", RegexOption.IGNORE_CASE)
        val headersString = replace(urlRegex, "").replaceQuotesAndTrim()
        return headersString.split("&").mapNotNull {
            val pair = it.split("=")
            if (pair.size == 2) pair.first() to pair.last() else null
        }.toMap()
    }

   */

    /**
     * Get url parameter with key.
     *
     * Example:-
     *
     * Input:
     * ```
     * http://192.54.104.122:8080/d/abcdef/video.mp4|User-Agent=Mozilla&Referer=CustomReferrer
     * ```
     * If given key is `user-agent`, then
     *
     * Result: Mozilla
     */
    private fun String.getUrlParameter(key: String): String? {
        val urlRegex = Regex("^(.*)\\|", RegexOption.IGNORE_CASE)
        val keyRegex = Regex("$key=(\\w[^&]*)", RegexOption.IGNORE_CASE)
        val paramsString = replace(urlRegex, "").replaceQuotesAndTrim()
        return keyRegex.find(paramsString)?.groups?.get(1)?.value
    }

    /**
     * Get attributes from `#EXTINF` tag as Map<String, String>.
     *
     * Example:-
     *
     * Input:
     * ```
     * #EXTINF:-1 tvg-id="1234" group-title="Kids" tvg-logo="url/to/logo", Title
     * ```
     * Result will be equivalent to kotlin map:
     * ```Kotlin
     * mapOf(
     *   "tvg-id" to "1234",
     *   "group-title" to "Kids",
     *   "tvg-logo" to "url/to/logo"
     *)
     * ```
     */
    private fun String.getAttributes(): Map<String, String> {
        val extInfRegex = Regex("(#EXTINF:.?[0-9]+)", RegexOption.IGNORE_CASE)
        val attributesString = replace(extInfRegex, "").replaceQuotesAndTrim().split(",").first()
        return attributesString.split(Regex("\\s")).mapNotNull {
            val pair = it.split("=")
            if (pair.size == 2) pair.first() to pair.last()
                .replaceQuotesAndTrim() else null
        }.toMap()
    }

    /**
     * Get value from a tag.
     *
     * Example:-
     *
     * Input:
     * ```
     * #EXTVLCOPT:http-referrer=http://example.com/
     * ```
     * Result: http://example.com/
     */
    private fun String.getTagValue(key: String): String? {
        val keyRegex = Regex("$key=(.*)", RegexOption.IGNORE_CASE)
        return keyRegex.find(this)?.groups?.get(1)?.value?.replaceQuotesAndTrim()
    }

    companion object {
        const val EXT_M3U = "#EXTM3U"
        const val EXT_INF = "#EXTINF"
        const val EXT_VLC_OPT = "#EXTVLCOPT"
    }

}

/**
 * Exception thrown when an error occurs while parsing playlist.
 */
sealed class PlaylistParserException(message: String) : Exception(message) {

    /**
     * Exception thrown if given file content is not valid.
     */
    class InvalidHeader :
        PlaylistParserException("Invalid file header. Header doesn't start with #EXTM3U")

}
