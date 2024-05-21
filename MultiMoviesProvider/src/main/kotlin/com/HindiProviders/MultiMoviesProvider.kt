package com.HindiProviders

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.jsoup.nodes.Element
import com.lagradost.nicehttp.NiceResponse
import okhttp3.FormBody

class MultiMoviesProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = BuildConfig.MultiMovies_API
    override var name = "MultiMovies"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AnimeMovie,
    )

    override val mainPage = mainPageOf(
        "$mainUrl/trending/" to "Trending",
        "$mainUrl/genre/bollywood-movies/" to "Bollywood Movies",
        "$mainUrl/genre/hollywood/" to "Hollywood Movies",
        "$mainUrl/genre/south-indian/" to "South Indian Movies",
        "$mainUrl/genre/punjabi/" to "Punjabi Movies",
        "$mainUrl/genre/amazon-prime/" to "Amazon Prime",
        "$mainUrl/genre/disney-hotstar/" to "Disney Hotstar",
        "$mainUrl/genre/genre/jio-ott/" to "Jio OTT",
        "$mainUrl/genre/netflix/" to "Netfilx",
        "$mainUrl/genre/sony-liv/" to "Sony Live",
        "$mainUrl/genre/k-drama/" to "KDrama",
        "$mainUrl/genre/genre/zee-5/" to "Zee5",
        "$mainUrl/genre/anime-hindi/" to "Anime Series",
        "$mainUrl/genre/anime-movies/" to "Anime Movies",
        "$mainUrl/genre/cartoon-network/" to "Cartoon Network",
        "$mainUrl/genre/disney-channel/" to "Disney Channel",
        "$mainUrl/genre/hungama/" to "Hungama",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = if (page == 1) {
            app.get(request.data).document
        } else {
            app.get(request.data + "/page/$page/").document
        }

        //Log.d("Document", request.data)
        val home = if (request.data.contains("/movies")) {
            document.select("#archive-content > article").mapNotNull {
                it.toSearchResult()
            }
        } else {
            document.select("div.items > article").mapNotNull {
                it.toSearchResult()
            }
        }

        return HomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        //Log.d("Got","got here")
        val title = this.selectFirst("div.data > h3 > a")?.text()?.toString()?.trim() ?: return null
        //Log.d("title", title)
        val href = fixUrl(this.selectFirst("div.data > h3 > a")?.attr("href").toString())
        //Log.d("href", href)
        val posterUrl = fixUrlNull(this.selectFirst("div.poster > img")?.attr("src"))
        //Log.d("posterUrl", posterUrl.toString())
        //Log.d("QualityN", qualityN)
        val quality =
            getQualityFromString(this.select("div.poster > div.mepo > span").text().toString())
        //Log.d("Quality", quality.toString())
        return if (href.contains("Movie")) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        //Log.d("document", document.toString())

        return document.select("div.result-item").mapNotNull {
            val title =
                it.selectFirst("article > div.details > div.title > a")?.text().toString().trim()
            //Log.d("title", titleS)
            val href = fixUrl(
                it.selectFirst("article > div.details > div.title > a")?.attr("href").toString()
            )
            //Log.d("href", href)
            val posterUrl = fixUrlNull(
                it.selectFirst("article > div.image > div.thumbnail > a > img")?.attr("src")
            )
            //Log.d("posterUrl", posterUrl.toString())
            //Log.d("QualityN", qualityN)
            val quality =
                getQualityFromString(it.select("div.poster > div.mepo > span").text().toString())
            //Log.d("Quality", quality.toString())
            val type = it.select("article > div.image > div.thumbnail > a > span").text().toString()
            if (type.contains("Movie")) {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = posterUrl
                    this.quality = quality
                }
            } else {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = posterUrl
                    this.quality = quality
                }
            }
        }
    }

    private suspend fun getEmbed(postid: String?, nume: String, referUrl: String?): NiceResponse {
        val body = FormBody.Builder()
            .addEncoded("action", "doo_player_ajax")
            .addEncoded("post", postid.toString())
            .addEncoded("nume", nume)
            .addEncoded("type", "movie")
            .build()

        return app.post(
            "$mainUrl/wp-admin/admin-ajax.php",
            requestBody = body,
            referer = referUrl
        )
    }

    data class TrailerUrl(
        @JsonProperty("embed_url") var embedUrl: String?,
        @JsonProperty("type") var type: String?
    )

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        //Log.d("Doc", doc.toString())
        val titleL = doc.selectFirst("div.sheader > div.data > h1")?.text()?.toString()?.trim()
            ?: return null
        val titleRegex = Regex("(^.*\\)\\d*)")
        val titleClean = titleRegex.find(titleL)?.groups?.get(1)?.value.toString()
        val title = if (titleClean == "null") titleL else titleClean
        val poster = fixUrlNull(
            doc.select("#contenedor").toString().substringAfter("background-image:url(")
                .substringBefore(");")
        )
        //Log.d("poster", poster.toString())
        val tags = doc.select("div.sgeneros > a").map { it.text() }
        val year =
            doc.selectFirst("span.date")?.text()?.toString()?.substringAfter(",")?.trim()?.toInt()
        //Log.d("year", year.toString())
        val description = doc.selectFirst("#info div.wp-content p")?.text()?.trim()
        val type = if (url.contains("tvshows")) TvType.TvSeries else TvType.Movie
        //Log.d("desc", description.toString())
        val trailerRegex = Regex("\"http.*\"")
        var trailer = if (type == TvType.Movie)
            fixUrlNull(
                getEmbed(
                    doc.select("#report-video-button-field > input[name~=postid]").attr("value")
                        .toString(),
                    "trailer",
                    url
                ).parsed<TrailerUrl>().embedUrl
            )
        else fixUrlNull(doc.select("iframe.rptss").attr("src").toString())
        trailer = trailerRegex.find(trailer.toString())?.value.toString()
        //Log.d("trailer", trailer.toString())
        val rating = doc.select("span.dt_rating_vgs").text().toRatingInt()
        //Log.d("rating", rating.toString())
        val duration =
            doc.selectFirst("span.runtime")?.text()?.toString()?.removeSuffix(" Min.")?.trim()
                ?.toInt()
        //Log.d("dur", duration.toString())
        val actors =
            doc.select("div.person").map {
                ActorData(
                    Actor(
                        it.select("div.data > div.name > a").text().toString(),
                        it.select("div.img > a > img").attr("src").toString()
                    ),
                    roleString = it.select("div.data > div.caracter").text().toString(),
                )
            }
        val recommendations = doc.select("#dtw_content_related-2 article").mapNotNull {
            it.toSearchResult()
        }

        val episodes = ArrayList<Episode>()
        doc.select("#seasons ul.episodios").mapIndexed { seasonNum, me ->
            me.select("li").mapIndexed { epNum, it ->
                episodes.add(
                    Episode(
                        data = it.select("div.episodiotitle > a").attr("href"),
                        name = it.select("div.episodiotitle > a").text(),
                        season = seasonNum + 1,
                        episode = epNum + 1,
                        posterUrl = it.select("div.imagen > img").attr("src")
                    )
                )
            }
        }

        return if (type == TvType.Movie) {
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                url
            ) {
                this.posterUrl = poster?.trim()
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.duration = duration
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster?.trim()
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.duration = duration
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val req = app.get(data).document
        Log.d("Phisher Test Load url", data)
        req.select("ul#playeroptionsul li").map {
            Triple(
                it.attr("data-post"),
                it.attr("data-nume"),
                it.attr("data-type")
            )
        }.apmap { (id, nume, type) ->
            if (!nume.contains("trailer")) {
                val source = app.post(
                    url = "$mainUrl/wp-admin/admin-ajax.php",
                    data = mapOf(
                        "action" to "doo_player_ajax",
                        "post" to id,
                        "nume" to nume,
                        "type" to type
                    ),
                    referer = mainUrl,
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                ).parsed<ResponseHash>().embed_url
                val link = source.substringAfter("\"").substringBefore("\"")
                when {
                    !link.contains("youtube") -> {
                        Log.d("Phisher Test Load", link)
                        loadExtractor(link, referer = mainUrl, subtitleCallback, callback)
                    }

                    else -> return@apmap
                }
            }
        }
        return true
    }

    data class ResponseHash(
        @JsonProperty("embed_url") val embed_url: String,
        @JsonProperty("key") val key: String? = null,
        @JsonProperty("type") val type: String? = null,
    )
}
