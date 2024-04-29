package com.anon

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.helper.AesHelper.cryptoAESHandler
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.jsoup.nodes.Element

class AnimeDekho2Provider : MainAPI() {
    override var mainUrl = "https://animedekho.com"
    override var name = "Anime Dekho"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    private val serverUrl = "https://vidxstream.xyz"

    override val supportedTypes =
        setOf(
            TvType.Cartoon,
            TvType.Anime,
            TvType.AnimeMovie,
            TvType.Movie,
        )

    override val mainPage =
        mainPageOf(
            "/series/" to "Series",
            "/movie/" to "Movies",
            "/category/anime/" to "Anime",
            "/category/cartoon/" to "Cartoon",
        )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val link = "$mainUrl${request.data}"
        val document = app.get(link).document
        val home =
            document.select("article").mapNotNull {
                it.toSearchResult()
            }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val href = this.selectFirst("a.lnk-blk")?.attr("href") ?: return null
        val title = this.selectFirst("header h2")?.text() ?: "null"
        val posterUrl = this.selectFirst("div figure img")?.attr("src")

        return newAnimeSearchResponse(title, Media(href, posterUrl).toJson(), TvType.Anime, false) {
            this.posterUrl = posterUrl
            addDubStatus(dubExist = true, subExist = true)
        }
    }

    override suspend fun search(query: String): List<AnimeSearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("ul[data-results] li article").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {

        val media = parseJson<AnimeDekhoProvider.Media>(url)
        val document = app.get(media.url).document

        val title = document.selectFirst("h1.entry-title")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:image:alt]")?.attr("content") ?: "No Title"
        val poster = fixUrlNull(document.selectFirst("div.post-thumbnail figure img")?.attr("src") ?: media.poster)
        val plot = document.selectFirst("div.entry-content p")?.text()?.trim()
            ?: document.selectFirst("meta[name=twitter:description]")?.attr("content")
        val year = (document.selectFirst("span.year")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:updated_time]")?.attr("content")
                ?.substringBefore("-"))?.toIntOrNull()
        val lst = document.select("ul.seasons-lst li")

        return if (lst.isEmpty()) {
            newMovieLoadResponse(title, url, TvType.Movie, AnimeDekhoProvider.Media(
                media.url,
                mediaType = 1
            ).toJson()) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        } else {
            @Suppress("NAME_SHADOWING") val episodes = document.select("ul.seasons-lst li").mapNotNull {
                val name = it.selectFirst("h3.title")?.text() ?: "null"
                val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val poster=it.selectFirst("div > div > figure > img")?.attr("src")
                val seasonnumber = it.selectFirst("h3.title > span")?.text().toString().substringAfter("S").substringBefore("-")
                val season=seasonnumber.toIntOrNull()
                Episode(AnimeDekhoProvider.Media(href, mediaType = 2).toJson(), name, posterUrl = poster,season = season)
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val media = parseJson<Media>(data)
        val body = app.get(media.url).document.selectFirst("body")?.attr("class") ?: return false
        val term = Regex("""(?:term|postid)-(\d+)""").find(body)?.groupValues?.get(1) ?: throw ErrorLoadingException("no id found")
        val vidLink = app.get("$mainUrl/?trembed=0&trid=$term&trtype=${media.mediaType}")
            .document.selectFirst("iframe")?.attr("src")
            ?: throw ErrorLoadingException("no iframe found")

        val doc = app.get(vidLink).text
        val master = Regex("""JScript[\w+]?\s*=\s*'([^']+)""").find(doc)!!.groupValues[1]
        val key = app.get("https://raw.githubusercontent.com/rushi-chavan/multi-keys/keys/keys.json").parsedSafe<Keys>()?.key?.get(0) ?: throw ErrorLoadingException("Unable to get key")
        val decrypt = cryptoAESHandler(master, key.toByteArray(), false)?.replace("\\", "")
            ?: throw ErrorLoadingException("error decrypting")
        val vidFinal = Regex("""file:\s*"(https:[^"]+)"""").find(decrypt)!!.groupValues[1]

        val headers =
            mapOf(
                "accept" to "*/*",
                "accept-language" to "en-US,en;q=0.5",
                "Origin" to serverUrl,
                "Accept-Encoding" to "gzip, deflate, br",
                "Connection" to "keep-alive",
                // "Referer" to "https://vidxstream.xyz/",
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "cross-site",
                "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/116.0",
            )

        callback.invoke(
            ExtractorLink(
                source = "Toon",
                name = "Toon",
                url = vidFinal,
                referer = "$serverUrl/",
                quality = Qualities.Unknown.value,
                isM3u8 = true,
                headers = headers,
            ),
        )
        return true
    }

    data class Media(val url: String, val poster: String? = null, val mediaType: Int? = null)
    data class Keys(
        @JsonProperty("chillx") val key: List<String>
    )

}