package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.nicehttp.NiceResponse
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import org.jsoup.nodes.Element

class Telugumv : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl: String = runBlocking {
        TelugumvPlugin.getDomains()?.telugumv ?: "https://telugumv.fun"
    }
    override var name = "Telugumv"
    override val hasMainPage = true
    override var lang = "te"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AnimeMovie,
    )

    companion object {
        //val headers= mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:101.0) Gecko/20100101 Firefox/101.0", "X-Requested-With" to "XMLHttpRequest")
    }

    override val mainPage = mainPageOf(
        "$mainUrl/movies/" to "Movies",
        "$mainUrl/tvshows/" to "Tvshows",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = if (page == 1) {
            app.get(request.data).documentLarge
        } else {
            app.get(request.data + "page/$page/").documentLarge
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
        return newHomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.data > h3 > a")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("div.data > h3 > a")?.attr("href").toString())
        val posterUrl = fixUrlNull(this.selectFirst("div.poster > img")?.attr("src"))
        val quality = getQualityFromString(this.select("div.poster > div.mepo > span").text())
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
        val document = app.get("$mainUrl/?s=$query").documentLarge
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
            val quality =
                getQualityFromString(it.select("div.poster > div.mepo > span").text())
            val type = it.select("article > div.image > div.thumbnail > a > span").text()
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
        val doc = app.get(url).documentLarge
        val titleL = doc.selectFirst("div.sheader > div.data > h1")?.text()?.trim()
            ?: return null
        val titleRegex = Regex("(^.*\\)\\d*)")
        val titleClean = titleRegex.find(titleL)?.groups?.get(1)?.value.toString()
        val title = if (titleClean == "null") titleL else titleClean
        val poster = fixUrlNull(
            doc.select("#contenedor").toString().substringAfter("background-image:url(")
                .substringBefore(");")
        )
        val tags = doc.select("div.sgeneros > a").map { it.text() }
        val year =
            doc.selectFirst("span.date")?.text()?.substringAfter(",")?.trim()?.toInt()
        val description = doc.selectFirst("#info div.wp-content p")?.text()?.trim()
        val type = if (url.contains("tvshows")) TvType.TvSeries else TvType.Movie
        val trailerRegex = Regex("\"http.*\"")
        var trailer = if (type == TvType.Movie)
            fixUrlNull(
                getEmbed(
                    doc.select("#report-video-button-field > input[name~=postid]").attr("value"),
                    "trailer",
                    url
                ).parsed<TrailerUrl>().embedUrl
            )
        else fixUrlNull(doc.select("iframe.rptss").attr("src"))
        trailer = trailerRegex.find(trailer.toString())?.value.toString()
        val rating = doc.select("span.dt_rating_vgs").text()
        val duration =
            doc.selectFirst("span.runtime")?.text()?.removeSuffix(" Min.")?.trim()
                ?.toIntOrNull()
        val actors =
            doc.select("div.person").map {
                ActorData(
                    Actor(
                        it.select("div.data > div.name > a").text(),
                        it.select("div.img > a > img").attr("src")
                    ),
                    roleString = it.select("div.data > div.caracter").text(),
                )
            }
        val recommendations = doc.select("#dtw_content_related-2 article").mapNotNull {
            it.toSearchResult()
        }

        val episodes = ArrayList<Episode>()
        doc.select("#seasons ul.episodios").mapIndexed { seasonNum, me ->
            me.select("li").mapIndexed { epNum, it ->
                episodes.add(
                    newEpisode(it.select("div.episodiotitle > a").attr("href"))
                    {
                        this.name=it.select("div.episodiotitle > a").text()
                        this.season=seasonNum + 1
                        this.episode=epNum + 1
                        this.posterUrl=it.select("div.imagen > img").attr("src")
                    }
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
                this.score = Score.from10(rating)
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
                this.score = Score.from10(rating)
                this.duration = duration
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }

    @Suppress("NAME_SHADOWING")
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val req = app.get(data).documentLarge
        req.select("ul#playeroptionsul li").map {
                Triple(
                    it.attr("data-post"),
                    it.attr("data-nume"),
                    it.attr("data-type")
                )
            }.amap { (id, nume, type) ->
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
                Log.d("Phisher", source)
                val link = source.substringBeforeLast("1")
                when {
                    !link.contains("youtube") -> {
                        if (link.contains("gdmirrorbot.nl")) {
                            app.get(link).documentLarge.select("ul#videoLinks li").map {
                                @Suppress("NAME_SHADOWING") val link = it.attr("data-link")
                                loadExtractor(link, referer = mainUrl, subtitleCallback, callback)
                            }
                        } else
                            if (link.contains("autoembed.cc")) {
                                app.get(
                                    link,
                                    referer = mainUrl
                                ).documentLarge.select("div.dropdown-menu > button").map {
                                    val encoded = it.attr("data-server")
                                    val link = base64Decode(encoded)
                                    Log.d("Phisher", link)
                                    if (link.contains("duka.autoembed.cc")) {
                                        val type = link.substringAfter("/").substringBefore("/")
                                        val id = link.substringAfter("/").substringAfter("/")
                                        val trueurl =
                                            "https://duka.autoembed.cc/api/getVideoSource?type=$type&id=$id"
                                        val dukelink =
                                            app.get(trueurl).parsedSafe<Dukeresponse>()?.videoSource
                                                ?: ""
                                        Log.d("Phisher", dukelink)
                                    } else
                                        if (link.contains("hin.autoembed.cc")) {
                                            val linkdoc = app.get(link).documentLarge.toString()
                                            Regex("\"file\":\"(https?://[^\"]+)\"").find(linkdoc)?.groupValues?.get(
                                                1
                                            )?.let { link ->
                                                callback.invoke(
                                                    newExtractorLink(
                                                        this.name,
                                                        this.name,
                                                        url = link,
                                                        INFER_TYPE
                                                    ) {
                                                        this.referer = ""
                                                        this.quality = Qualities.Unknown.value
                                                    }
                                                )
                                            }
                                        } else {
                                            loadExtractor(link, subtitleCallback, callback)
                                        }
                                }
                            } else
                                loadExtractor(link, referer = mainUrl, subtitleCallback, callback)
                    }

                    else -> return@amap
                }
            }
        }
        return true
    }

    data class Dukeresponse(
        val videoSource: String,
        val subtitles: List<Any?>,
        val posterImageUrl: String,
    )

    data class ResponseHash(
        @JsonProperty("embed_url") val embed_url: String,
        @JsonProperty("key") val key: String? = null,
        @JsonProperty("type") val type: String? = null,
    )
}
