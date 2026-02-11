package com.tokuzilla

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URI
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.text.ifEmpty
import com.lagradost.api.Log

class TokuZilla : MainAPI() {
    override var mainUrl = "https://tokuzilla.net"
    override var name = "TokuZilla"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        "/" to "Home",
        "/categories/super-sentai" to "Super Sentai",
        "/power-ranger" to "Power Rangers"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if(page == 1) "$mainUrl${request.data}" else "$mainUrl${request.data}/page/$page/"

        val document = app.get(url).document

        val home = document.select("div.col-sm-4").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text()?.trim() ?: ""

        val posterElement = document.selectFirst("div.thumb img")
        val posterUrl = fixUrlNull(
            posterElement?.attr("data-src")?.ifEmpty { posterElement.attr("src") }
        )

        val yearText = document.select("div.top-detail div.right tr:has(th:contains(Year)) td span.meta").firstOrNull()?.text()?.trim()
        val year = yearText?.toIntOrNull()

        val plot = document.select("div.post-entry p").text().trim()

        val divText = document.select("div.top-detail").text()
        val isSeries = divText.contains("episode", ignoreCase = true)

        if (isSeries) {
            // 6. Episodes (Old Selector & Logic)
            val episodes = document.select("ul.pagination.post-tape li.page-item a.page-link").mapNotNull { linkElement ->
                val href = linkElement.attr("href")

                // Logic: Extract episode number via Regex from URL
                val number = Regex("[?&]ep=(\\d+)").find(href)?.groupValues?.get(1)?.toIntOrNull()
                val name = "Episode ${number ?: "?"}"

                if (number != null) {
                    newEpisode(href) {
                        this.name = name
                        this.episode = number
                    }
                } else {
                    null
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = posterUrl
                this.plot = plot
                this.year = year
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = posterUrl
                this.plot = plot
                this.year = year
            }
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleElement = this.selectFirst("h3 a")

        if( titleElement != null ) {
            val title = this.select("h3 a").text().trim()
            val href = fixUrl(this.select("h3 a").attr("href"))
            val posterUrl = this.select("a img").attr("src").ifEmpty { "" }

            return newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = posterUrl
            }
        } else {
            return null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        println("loadLinks data: $data")
        val document = app.get(data).document
        val href = document.select("div.player iframe").attr("src")
        loadExtractor(href, data, subtitleCallback, callback)
        return true
    }

    class P2pplay : VidStack() {
        override var mainUrl = "https://t1.p2pplay.pro"
        override val requiresReferer = true
    }

    open class VidStack : ExtractorApi() {
        override var name = "Vidstack"
        override var mainUrl = "https://vidstack.io"
        override val requiresReferer = true

        override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
        )
        {
            val headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:134.0) Gecko/20100101 Firefox/134.0")
            val hash = url.substringAfterLast("#").substringAfter("/")
            val baseurl = getBaseUrl(url)

            val encoded = app.get("$baseurl/api/v1/video?id=$hash", headers = headers).text.trim()

            val key = "kiemtienmua911ca"
            val ivList = listOf("1234567890oiuytr", "0123456789abcdef")

            val decryptedText = ivList.firstNotNullOfOrNull { iv ->
                AesHelper.decryptAES(encoded, key, iv)
            } ?: throw Exception("Failed to decrypt with all IVs")

            val m3u8 = Regex("\"source\":\"(.*?)\"").find(decryptedText)
                ?.groupValues?.get(1)
                ?.replace("\\/", "/") ?: ""
            val subtitlePattern = Regex("\"([^\"]+)\":\\s*\"([^\"]+)\"")
            val subtitleSection = Regex("\"subtitle\":\\{(.*?)\\}").find(decryptedText)?.groupValues?.get(1)

            subtitleSection?.let { section ->
                subtitlePattern.findAll(section).forEach { match ->
                    val lang = match.groupValues[1]
                    val rawPath = match.groupValues[2].split("#")[0]
                    if (rawPath.isNotEmpty()) {
                        val path = rawPath.replace("\\/", "/")
                        val subUrl = "$mainUrl$path"
                        subtitleCallback(newSubtitleFile(lang, fixUrl(subUrl)))
                    }
                }
            }

            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = m3u8.replace("https","http"),
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = url
                    this.headers = mapOf("referer" to url,"Origin" to url.substringAfterLast("/"))
                    this.quality = Qualities.Unknown.value
                }
            )
        }

        private fun getBaseUrl(url: String): String {
            return try {
                URI(url).let { "${it.scheme}://${it.host}" }
            } catch (e: Exception) {
                Log.e("Vidstack", "getBaseUrl fallback: ${e.message}")
                mainUrl
            }
        }
    }

    object AesHelper {
        private const val TRANSFORMATION = "AES/CBC/PKCS5PADDING"

        fun decryptAES(inputHex: String, key: String, iv: String): String {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES")
            val ivSpec = IvParameterSpec(iv.toByteArray(Charsets.UTF_8))

            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            val decryptedBytes = cipher.doFinal(inputHex.hexToByteArray())
            return String(decryptedBytes, Charsets.UTF_8)
        }

        private fun String.hexToByteArray(): ByteArray {
            check(length % 2 == 0) { "Hex string must have an even length" }
            return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
    }
}