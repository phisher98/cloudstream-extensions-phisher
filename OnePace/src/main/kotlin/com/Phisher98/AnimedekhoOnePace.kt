package com.Phisher98

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.jsoup.nodes.Element

open class OnepaceProvider : MainAPI() {
    override var mainUrl = "https://onepace.me"
    override var name = "OnePace AD"
    override val hasMainPage = true
    override var lang = "en"

    override val supportedTypes =
        setOf(
            TvType.Anime,
        )

    override val mainPage =
        mainPageOf(
            "/series/one-pace-english-sub/" to "One Pace English Sub",
            "/series/one-pace-english-dub/" to "One Pace English Dub",
        )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val link = "$mainUrl${request.data}"
        val document = app.get(link).document
        val home =
            document.select("div.seasons.aa-crd > div.seasons-bx").map {
                it.toSearchResult()
            }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): AnimeSearchResponse {
        val hreftitle= this.selectFirst("picture img")?.attr("alt")
        var href=""
        if (hreftitle!!.isNotEmpty()) {
            if (hreftitle.contains("Dub")) {
                href = "https://onepace.me/series/one-pace-english-dub"
            } else {
                href = "https://onepace.me/series/one-pace-english-sub"
            }
        }
        val title = this.selectFirst("p")?.text() ?:""
        val posterUrl = this.selectFirst("img")?.attr("src")
        val dubtype:Boolean
        val subtype:Boolean
        if (hreftitle.contains("Dub"))
        {
            dubtype = true
            subtype =false
        }
        else
        {
            dubtype = false
            subtype = true

        }
        return newAnimeSearchResponse(title, Media(href, posterUrl,title).toJson(), TvType.Anime, false) {
            this.posterUrl = posterUrl
            addDubStatus(dubExist = dubtype, subExist = subtype)
        }
    }

    override suspend fun search(query: String): List<AnimeSearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("ul[data-results] li article").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        Log.d("Phisher",url)
        val media = parseJson<Media>(url)
        val document = app.get(media.url).document
        val ArcINT=media.mediaType?.substringAfter("Arc ")
        val element= document.selectFirst("div.seasons.aa-crd > div.seasons-bx:contains($ArcINT)")
        val title = media.mediaType ?:"No Title"
        val poster = "https://images3.alphacoders.com/134/1342304.jpeg"
        val plot = document.selectFirst("div.entry-content p")?.text()?.trim()
            ?: document.selectFirst("meta[name=twitter:description]")?.attr("content")
        val year = (document.selectFirst("span.year")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:updated_time]")?.attr("content")
                ?.substringBefore("-"))?.toIntOrNull()
        val lst = element?.select("ul.seasons-lst.anm-a li")
        Log.d("Phisher","$ArcINT $element $title $lst")
        return if (lst!!.isEmpty()) {
            newMovieLoadResponse(title, url, TvType.Movie, Media(
                media.url,
                mediaType = 1.toString()
            ).toJson()) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        } else {
            @Suppress("NAME_SHADOWING") val episodes = element.select("ul.seasons-lst.anm-a li").mapNotNull {
                val name = it.selectFirst("h3.title")?.ownText() ?: "null"
                val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val poster= "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/OnePack.png"
                val seasonnumber = it.selectFirst("h3.title > span")?.text().toString().substringAfter("S").substringBefore("-")
                val season=seasonnumber.toIntOrNull()
                Episode(Media(href, mediaType = 2.toString()).toJson(), name, posterUrl = poster,season = season)
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
        for (i in 0..4) {
            val link = app.get("$mainUrl/?trdekho=$i&trid=$term&trtype=${media.mediaType}")
                .document.selectFirst("iframe")?.attr("src")
                ?: throw ErrorLoadingException("no iframe found")
            Log.d("Phisher",link)
            loadExtractor(link,subtitleCallback, callback)
        }
        return true
    }

    data class Media(val url: String, val poster: String? = null, val mediaType: String? = null)

}
