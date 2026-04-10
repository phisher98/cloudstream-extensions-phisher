package com.tokusatsu.ultimate

import android.util.Log
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.fixUrl
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class TokusatsuUltimate : MainAPI() {
    override var mainUrl = "https://toku555.com"
    override var name = "TokusatsuUltimate"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.TvSeries, TvType.Movie)

    companion object {
        private val mapper = jacksonObjectMapper()
        
        fun getType(t: String): TvType {
            return when {
                t.contains("movie", ignoreCase = true) -> TvType.Movie
                t.contains("tv", ignoreCase = true) || t.contains("series", ignoreCase = true) -> TvType.TvSeries
                else -> TvType.Anime
            }
        }

        fun getRating(score: String?): String? {
            if (score == null) return null
            return try {
                (score.toFloat() * 10).toString()
            } catch (e: Exception) {
                score
            }
        }

        fun getStatus(t: String?): ShowStatus {
            return when {
                t?.contains("Ongoing", ignoreCase = true) == true -> ShowStatus.Ongoing
                t?.contains("Completed", ignoreCase = true) == true -> ShowStatus.Completed
                else -> ShowStatus.Completed
            }
        }

        fun cleanTitle(title: String): String {
            // Remove common suffixes and clean up the title
            var cleaned = title
            val suffixes = listOf(" - Tokusatsu", " | Tokusatsu", " | Official")
            for (suffix in suffixes) {
                if (cleaned.endsWith(suffix)) {
                    cleaned = cleaned.substring(0, cleaned.length - suffix.length)
                }
            }
            return cleaned.trim()
        }
    }

    override val mainPage = mainPageOf(
        "kamen-rider" to "Kamen Rider Series",
        "super-sentai" to "Super Sentai Series",
        "tokusatsu-anime" to "Tokusatsu Anime",
        "metal-heroes" to "Metal Heroes",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${mainUrl}/${request.data}/page/$page/"
        
        val document = app.get(url).document
        val home = document.select("div.film-poster, .item, .series-item").mapNotNull { element ->
            val titleElement = element.selectFirst(".film-title a, .title a, h3 a") ?: element.selectFirst("a")
            
            if (titleElement != null) {
                val title = cleanTitle(titleElement.text().trim())
                val href = fixUrl(titleElement.attr("href"))
                val posterElement = element.selectFirst(".film-poster img, img")
                val posterUrl = fixUrlNull(posterElement?.attr("src") ?: posterElement?.attr("data-src"))

                newAnimeSearchResponse(title, href, TvType.Anime) {
                    this.posterUrl = posterUrl
                }
            } else {
                null
            }
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

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/search/${query}/"
        val document = app.get(searchUrl).document

        return document.select("div.film-poster, .item, .series-item").mapNotNull { element ->
            val titleElement = element.selectFirst(".film-title a, .title a, h3 a") ?: return@mapNotNull null
            val title = cleanTitle(titleElement.text().trim())
            val href = fixUrl(titleElement.attr("href"))
            val posterElement = element.selectFirst(".film-poster img, img")
            val posterUrl = fixUrlNull(posterElement?.attr("src") ?: posterElement?.attr("data-src"))

            newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val titleElement = document.selectFirst("h1.heading-title, .title, .film-name,div.row h1") ?:
            throw ErrorLoadingException("No title found")
        val title = cleanTitle(titleElement.text().trim())

        val posterElement = document.selectFirst(".film-poster img, .poster img, img[src*='image'],div.row >div > img")
        val posterUrl = fixUrlNull(posterElement?.attr("src") ?: posterElement?.attr("data-src"))

        val year = document.selectFirst(".year, .date, .released")?.text()?.trim()
            ?.toIntOrNull()

        val description = document.selectFirst("div.row.content > p:nth-child(1),div.row.content > p:nth-child(2)")?.text()?.trim()

        val tags = document.select(".genres a, .tags a, .category a").map { it.text().trim() }

        val episodes = mutableListOf<Episode>()
        
        // Look for episode list
        document.select("ul.pagination.post-tape li").amap { epElement ->
            val epTitle = epElement.select("a").text()
            val epHref = epElement.select("a").attr("href")
            val href= app.get(epHref).document.select("div.player iframe").attr("src")
            val epNum = epTitle.toIntOrNull()

            episodes.add(
                newEpisode(href)
                {
                    this.name = "Episode $epTitle"
                    this.episode = epNum
                }
            )
        }

        // If no episodes found, try to find them in a different structure
        if (episodes.isEmpty()) {
            val iframe=document.select("div.player iframe").attr("src")
            return newMovieLoadResponse(title, url, TvType.AnimeMovie,iframe) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = description
                this.tags = tags

            }
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = posterUrl
            this.year = year
            this.plot = description
            this.tags = tags
            addEpisodes(DubStatus.Subbed, episodes.reversed())
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        loadExtractor(data, data, subtitleCallback, callback)
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
                try {
                    AesHelper.decryptAES(encoded, key, iv)
                } catch (e: Exception) {
                    null
                }
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