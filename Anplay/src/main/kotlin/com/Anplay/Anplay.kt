package com.Anplay

//import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URI

open class Anplay : MainAPI() {
    override var mainUrl = "https://anplay.in"
    override var name = "Anplay"
    override val hasMainPage = true
    override var lang = "hi"
    var directUrl = ""
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    override val mainPage = mainPageOf(
        "movies" to "Movies",
        "tvshows" to "TV-Shows",
        "genre/hindi-dub" to "Hindi Dub",
        "genre/crunchyroll" to "Crunchyroll",
    )
    
    companion object
    {
        val cookies = mapOf("cookie" to "starstruck_9c69a16370994b12750cec9b9594d3dd=5f54c7d4d56c84e5db8bb1f0c87adcdc; ays_show_popup_only_once_2=Access%20Authorisation%20for%20user; wssplashuid=9ba9ec7c3e6edab87a19e41d7c933ae890472793.1714380994.1; _subid=3u11m7a80ju; c3807=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJkYXRhIjoie1wic3RyZWFtc1wiOntcIjNcIjoxNzE0NDk2MDQ0fSxcImNhbXBhaWduc1wiOntcIjFcIjoxNzE0NDk2MDQ0fSxcInRpbWVcIjoxNzE0NDk2MDQ0fSJ9.HovdzGc23r6_CJ4f6Gcr88ddjK_KTp4VUOPZrSQ0PMU; PHPSESSID=6vqkgujr8vrug7va18r13micme")
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = if(page == 1) "$mainUrl/${request.data}/" else "$mainUrl/${request.data}/page/$page/"
        val document = app.get(url, cookies=cookies).document
        val home =
            document.select("div#archive-content article,div.items.full article").mapNotNull {
                it.toSearchResult()
            }
        return newHomePageResponse(request.name, home)
    }

    private fun getProperLink(uri: String): String {
        return when {
            uri.contains("/episodes/") -> {
                var title = uri.substringAfter("$mainUrl/episodes/")
                title = Regex("(.+?)-season").find(title)?.groupValues?.get(1).toString()
                "$mainUrl/tvshows/$title"
            }

            uri.contains("/seasons/") -> {
                var title = uri.substringAfter("$mainUrl/seasons/")
                title = Regex("(.+?)-season").find(title)?.groupValues?.get(1).toString()
                "$mainUrl/tvshows/$title"
            }

            else -> {
                uri
            }
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3 > a")?.text() ?: return null
        val href = getProperLink(fixUrl(this.selectFirst("h3 > a")!!.attr("href")))
        val posterUrl = fixUrlNull(this.select("div.poster img").last()?.getImageAttr())
        val quality = getQualityFromString(this.select("span.quality").text())
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = quality
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search/$query").document
        return document.select("div.result-item").map {
            val title =
                it.selectFirst("div.title > a")!!.text().replace(Regex("\\(\\d{4}\\)"), "").trim()
            val href = getProperLink(it.selectFirst("div.title > a")!!.attr("href"))
            val posterlink=app.get(href).document.select("div.poster img").attr("src")
            val posterUrl = posterlink
                //it.selectFirst("img")!!.attr("src").toString()
            newMovieSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val request = app.get(url, cookies=cookies)
        val document = request.document
        directUrl = getBaseUrl(request.url)
        val title =
            document.selectFirst("div.data > h1")?.text()?.trim().toString()
        val poster = document.select("div#contenedor > style").html().substringAfter("(").substringBefore(")")
        val tags = document.select("div.sgeneros > a").map { it.text() }

        val year = Regex(",\\s?(\\d+)").find(
            document.select("span.date").text().trim()
        )?.groupValues?.get(1).toString().toIntOrNull()
        val tvType = if (document.select("ul#section > li:nth-child(1)").text()
                .contains("Episodes") || document.select("ul#playeroptionsul li span.title")
                .text().contains(
                    Regex("Episode\\s+\\d+|EP\\d+|PE\\d+")
                )
        ) TvType.TvSeries else TvType.Movie
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        val trailer = document.selectFirst("div.embed iframe")?.attr("src")
        val rating =
            document.selectFirst("span.dt_rating_vgs")?.text()?.toRatingInt()
        val actors = document.select("div.persons > div[itemprop=actor]").map {
            Actor(
                it.select("meta[itemprop=name]").attr("content"),
                it.select("img:last-child").attr("src")
            )
        }

        val recommendations = document.select("div.owl-item").map {
            val recName =
                it.selectFirst("a")!!.attr("href").toString().removeSuffix("/").split("/").last()
            val recHref = it.selectFirst("a")!!.attr("href")
            val recPosterUrl = it.selectFirst("img")?.getImageAttr()
            newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                this.posterUrl = recPosterUrl
            }
        }

        return if (tvType == TvType.TvSeries) {
            val episodes =
                document.select("ul.episodios > li").map {
                    val href = it.select("a").attr("href")
                    val name = fixTitle(it.select("div.episodiotitle > a").text().trim())
                    val image = it.selectFirst("div.imagen > img")?.getImageAttr()
                    val episode =
                        it.select("div.numerando").text().replace(" ", "").split("-").last()
                            .toIntOrNull()
                    val season =
                        it.select("div.numerando").text().replace(" ", "").split("-").first()
                            .toIntOrNull()
                    Episode(
                        href,
                        name,
                        season,
                        episode,
                        image
                    )
                }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("Phisher",data)
        val document = app.get(data, cookies=cookies).document
        document.select("ul#playeroptionsul > li").map {
            Triple(
                it.attr("data-post"),
                it.attr("data-nume"),
                it.attr("data-type")
            )
        }.apmap { (id, nume, type) ->
            val source = app.post(
                url = "$directUrl/wp-admin/admin-ajax.php",
                data = mapOf(
                    "action" to "doo_player_ajax",
                    "post" to id,
                    "nume" to nume,
                    "type" to type
                ),
                referer = data,
                headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                cookies = cookies
            ).parsed<ResponseHash>().embed_url
            Log.d(" Phisher source",source)
            when {
                !source.contains("youtube") -> {
                    Log.d("Phisher",source)
                    loadExtractor(source, subtitleCallback, callback)
                }
                else -> return@apmap
            }
        }
        return true
    }

    private fun Element.getImageAttr(): String? {
        return when {
            this.hasAttr("data-src") -> this.attr("abs:data-src")
            this.hasAttr("data-lazy-src") -> this.attr("abs:data-lazy-src")
            this.hasAttr("srcset") -> this.attr("abs:srcset").substringBefore(" ")
            else -> this.attr("abs:src")
        }
    }

    data class ResponseHash(
        @JsonProperty("embed_url") val embed_url: String,
        @JsonProperty("type") val type: String?,
    )
}
