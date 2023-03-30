package com.likdev256

//import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.jsoup.nodes.Element
import com.lagradost.nicehttp.NiceResponse
import okhttp3.FormBody

class MovieHUBProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://x265rips.us"
    override var name = "MovieHUB"
    override val hasMainPage = true
    override var lang = "ta"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override val mainPage = mainPageOf(
        "$mainUrl/movies/" to "Movies",
        "$mainUrl/series/" to "TV Shows",
        "$mainUrl/genre/action/" to "Action",
        "$mainUrl/genre/adventure/" to "Adventure",
        "$mainUrl/genre/animation/" to "Animation",
        "$mainUrl/genre/fantasy/" to "Fantasy",
        "$mainUrl/genre/horror/" to "Horror",
        "$mainUrl/genre/romance/" to "Romance",
        "$mainUrl/genre/science-fiction/" to "Sci-Fi"
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
        val home = if (request.data.contains("genre")) {
            document.select("div.items > article").mapNotNull {
                it.toSearchResult()
            }
        } else {
            document.select("#archive-content > article").mapNotNull {
                it.toSearchResult()
            }
        }

        return HomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        //Log.d("Got","got here")
        val title = this.selectFirst("div.animation-1 > div.title > h4")?.text()?.toString()?.trim() ?: return null
        //Log.d("title", title)
        val href = fixUrl(this.selectFirst("div.poster > a")?.attr("href").toString())
        //Log.d("href", href)
        val posterUrl = fixUrlNull(this.selectFirst("div.poster > img")?.attr("data-src"))
        //Log.d("posterUrl", posterUrl.toString())
        //Log.d("QualityN", qualityN)
        val quality = getQualityFromString(this.select("div.poster > div.mepo > span").text().toString())
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
            val title = it.selectFirst("article > div.details > div.title > a")?.text().toString().trim()
            //Log.d("title", titleS)
            val href = fixUrl(it.selectFirst("article > div.details > div.title > a")?.attr("href").toString())
            //Log.d("href", href)
            val posterUrl = fixUrlNull(it.selectFirst("article > div.image > div.thumbnail > a > img")?.attr("src"))
            //Log.d("posterUrl", posterUrl.toString())
            //Log.d("QualityN", qualityN)
            val quality = getQualityFromString(it.select("div.poster > div.mepo > span").text().toString())
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

    data class TrailerUrl (
        @JsonProperty("embed_url") var embedUrl : String?,
        @JsonProperty("type") var type : String?
    )

    /*data class embedUrl (
        @JsonProperty("embed_url") var embedUrl : String,
        @JsonProperty("type") var type : String?
    )*/

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        //Log.d("Doc", doc.toString())
        val titleL = doc.selectFirst("div.sheader > div.data > h1")?.text()?.toString()?.trim()
            ?: return null
        val titleRegex = Regex("(^.*\\)\\d*)")
        val title = titleRegex.find(titleL)?.groups?.get(1)?.value.toString()
        //Log.d("titleL", titleL)
        //Log.d("title", title)
        val poster = fixUrlNull(doc.selectFirst("div.poster > img")?.attr("src"))
        val bgposter = fixUrlNull(
            doc.selectFirst("div.g-item:nth-child(1) > a:nth-child(1) > img:nth-child(1)")
                ?.attr("data-src").toString()
        )
        //Log.d("bgposter", bgposter.toString())
        //Log.d("poster", poster.toString())
        val tags = doc.select("div.sgeneros > a").map { it.text() }
        val year =
            doc.selectFirst("span.date")?.text()?.toString()?.substringAfter(",")?.trim()?.toInt()
        //Log.d("year", year.toString())
        val description = doc.selectFirst("div.wp-content > p > span")?.text()?.trim()
        val type = if (url.contains("movies")) TvType.Movie else TvType.TvSeries
        //Log.d("desc", description.toString())
        val trailer = if (type == TvType.Movie)
            fixUrlNull(
                getEmbed(
                    doc.select("#report-video-button-field > input[name~=postid]").attr("value").toString(),
                    "trailer",
                    url
                ).parsed<TrailerUrl>().embedUrl
            )
        else fixUrlNull(doc.select("iframe.rptss").attr("src").toString())
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

        /*val episodes = ArrayList<Episode>()
        doc.select("section.container > div.border-b").forEach { me ->
            val seasonNum = me.select("button > span").text()
            me.select("div.season-list > a").forEach {
                episodes.add(
                    Episode(
                        data = mainUrl + it.attr("href").toString(),
                        name = it.ownText().toString().removePrefix("Episode ").substring(2),//.replaceFirst(epName.first().toString(), ""),
                        season = titRegex.find(seasonNum)?.value?.toInt(),
                        episode = titRegex.find(it.select("span.flex").text().toString())?.value?.toInt()
                    )
                )
            }
        }*/

        //return if (type == TvType.Movie) {
        return newMovieLoadResponse(title, url, TvType.Movie, url+","+doc.select("#report-video-button-field > input[name~=postid]").attr("value").toString()) {
                this.posterUrl = poster?.trim()
                this.backgroundPosterUrl = bgposter?.trim()
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.duration = duration
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        /*} else {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster?.trim()
                this.backgroundPosterUrl = bgposter?.trim()
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.duration = duration
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer?.toString())
            }
        }*/
    }

    data class Subs (
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
    )

    data class StreamData (
        @JsonProperty("file") val file: String,
        @JsonProperty("cdn_img") val cdnImg: String,
        @JsonProperty("hash") val hash: String,
        @JsonProperty("subs") val subs: ArrayList<Subs>? = arrayListOf(),
        @JsonProperty("length") val length: String,
        @JsonProperty("id") val id: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("backup") val backup: String,
    )

    data class Main (
        @JsonProperty("stream_data") val streamData: StreamData,
        @JsonProperty("status_code") val statusCode: Int,
    )

    data class getEmbed (
        @JsonProperty("status") var status: Boolean?,
        @JsonProperty("statusCode") var statusCode: Int?,
        @JsonProperty("statusText") var statusText: String?,
        @JsonProperty("data") var data: Data?
    )

    data class Data (
        @JsonProperty("filecode") var filecode : String?
    )

    private val hexArray = "0123456789ABCDEF".toCharArray()

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF

            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val link = data.substringBefore(",")
        //Log.d("embedlink", link)
        //val postid = data.substringAfter(",")
        //Log.d("embedlink", postid)
        /*val Embedlink = getEmbed(
            postid,
            "1",
            link
        ).parsed<embedUrl>().embedUrl*/

        val doc = app.get(link).document
        doc.select("div.wp-content > p > span > a[href*=\"filepress\"]").forEach {
            //Log.d("myitboy", it.toString())
            val urlid = it.attr("href").replace("https://filepress.online/file/", "")
            //Log.d("myurlid", urlid)
            val url = "https://gdpress.xyz/e/" + app.get(
                "https://api.filepress.online/api/file/video/streamSB/$urlid",
                referer = "https://filepress.online/"
            ).parsed<getEmbed>().data?.filecode.toString() + "/"

            //Log.d("myurl", url)
            val main = "https://gdpress.xyz"
            val regexID =
                Regex("(embed-[a-zA-Z0-9]{0,8}[a-zA-Z0-9_-]+|/e/[a-zA-Z0-9]{0,8}[a-zA-Z0-9_-]+)")
            val id = regexID.findAll(url).map { me ->
                me.value.replace(Regex("(embed-|/e/)"), "")
            }.first()
//        val master = "$main/sources16/6d6144797752744a454267617c7c${bytesToHex.lowercase()}7c7c4e61755a56456f34385243727c7c73747265616d7362/6b4a33767968506e4e71374f7c7c343837323439333133333462353935333633373836643638376337633462333634663539343137373761333635313533333835333763376333393636363133393635366136323733343435323332376137633763373337343732363536313664373336327c7c504d754478413835306633797c7c73747265616d7362"
            val master = "$main/sources16/" + bytesToHex("||$id||||streamsb".toByteArray()) + "/"
            val headers = mapOf(
                "watchsb" to "sbstream",
            )
            val mapped = app.get(
                master.lowercase(),
                headers = headers,
                referer = url,
            ).parsedSafe<Main>()
            // val urlmain = mapped.streamData.file.substringBefore("/hls/")
            M3u8Helper.generateM3u8(
                name,
                mapped?.streamData?.file.toString(),
                url,
                headers = headers
            ).forEach(callback)

            mapped?.streamData?.subs?.map {sub ->
                subtitleCallback.invoke(
                    SubtitleFile(
                        sub.label.toString(),
                        sub.file ?: return@map null,
                    )
                )
            }
        }
        return true
    }
}
