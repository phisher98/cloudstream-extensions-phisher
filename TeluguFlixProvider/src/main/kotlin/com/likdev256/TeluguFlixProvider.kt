package com.likdev256

//import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mvvm.safeApiCall
import org.jsoup.nodes.Element

class TeluguFlixProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://teluguflix.site"
    override var name = "TeluguFlix"
    override val hasMainPage = true
    override var lang = "te"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override val mainPage = mainPageOf(
        "$mainUrl/best-rating/" to "Top Rated",
        "$mainUrl/category/recently-added-movies/" to "Recently Added",
        "$mainUrl/category/hollywood-dubbed-movies/" to "Hollywood Dubbed",
        "$mainUrl/order-by-title/" to "Movies"
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
        val home = document.select("#gmr-main-load > article").mapNotNull {
                it.toSearchResult()
            }//.filter { it.name.contains("18+", true).not() }

        return HomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse {
        val titleRaw = this.select("div.gmr-box-content h2.entry-title > a").text().trim()
        val titleRegex = Regex("^.*\\d\\)")
        var title = titleRegex.find(titleRaw)?.value.toString()
        if (title.isBlank() || title == "null") {
            title = titleRaw
        }
        //Log.d("title", title)
        val href = fixUrl(this.select("div.gmr-box-content > div.content-thumbnail > a").attr("href"))
        //Log.d("href", href)
        val posterUrl = fixUrlNull(this.selectFirst("div.gmr-box-content > div.content-thumbnail > a > img")?.attr("src"))
        //Log.d("posterUrl", posterUrl.toString())
        //Log.d("QualityN", qualityN)
        val quality = getQualityFromString(titleRaw)
        //Log.d("Quality", quality.toString())
        return newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        //Log.d("document", document.toString())

        return document.select("#gmr-main-load > article").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        //Log.d("Doc", doc.toString())
        val titleRaw = doc.select("h1.entry-title").text().trim()
        val titleRegex = Regex("^.*\\d\\)")
        var title = titleRegex.find(titleRaw)?.value.toString()
        if (title.isBlank() || title == "null") {
            title = titleRaw
        }
        //Log.d("title", title)
        val poster = fixUrlNull(doc.selectFirst("figure.pull-left > img")?.attr("src"))
        //Log.d("bgposter", bgposter.toString())
        //Log.d("poster", poster.toString())
        val tags = doc.select("div.gmr-movie-innermeta:nth-child(2) > span.gmr-movie-genre > a").map { it.text() }
        val year = doc.select("div.gmr-movie-innermeta:nth-child(3) > span.gmr-movie-genre > a").text().trim().toIntOrNull()
        //Log.d("mybadyear", year.toString())
        val description = doc.selectFirst("div.entry-content > p[style~=text-align]")?.text()?.trim()
        //Log.d("desc", description.toString())
        val trailer = doc.selectFirst("ul.gmr-player-nav a.gmr-trailer-popup")?.attr("href")?.trim()
        //Log.d("trailer", trailer.toString())
        val rating = doc.select("div.gmr-meta-rating > span[itemprop=ratingValue]").text().toRatingInt()
        //Log.d("rating", rating.toString())
        val duration = Regex("\\d+").find(doc.selectFirst("span.gmr-movie-runtime")?.text()?.trim().toString())?.value?.toInt()
        //Log.d("dur", duration.toString())
        val actors =
            doc.select("div.gmr-moviedata > span[itemprop~=actors] > span[itemprop~=name] > a").map {
                ActorData(
                    Actor(it.text())
                )
            }
        val recommendations = doc.select("div.row > article.item").mapNotNull {
            it.toSearchResult()
        }

        val embedLink = doc.select("div.gmr-embed-responsive > iframe").attr("src")

        return newMovieLoadResponse(title, url, TvType.Movie, embedLink) {
                this.posterUrl = poster
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

    private suspend fun loadStreamSB(
        url: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit) {

        val regexID =
            Regex("(embed-[a-zA-Z0-9]{0,8}[a-zA-Z0-9_-]+|/e/[a-zA-Z0-9]{0,8}[a-zA-Z0-9_-]+)")
        val id = regexID.findAll(url).map {
            it.value.replace(Regex("(embed-|/e/)"), "")
        }.first()
        //val master = "$main/sources16/6d6144797752744a454267617c7c${bytesToHex.lowercase()}7c7c4e61755a56456f34385243727c7c73747265616d7362/6b4a33767968506e4e71374f7c7c343837323439333133333462353935333633373836643638376337633462333634663539343137373761333635313533333835333763376333393636363133393635366136323733343435323332376137633763373337343732363536313664373336327c7c504d754478413835306633797c7c73747265616d7362"
        val master = "https://sbchill.com/sources16/" + bytesToHex("||$id||||streamsb".toByteArray()) + "/"
        val headers = mapOf(
            "watchsb" to "sbstream",
        )
        val mapped = app.get(
            master.lowercase(),
            headers = headers,
            referer = url,
        ).parsedSafe<Main>()
        // val urlmain = mapped.streamData.file.substringBefore("/hls/")
        safeApiCall {
            callback.invoke(
                ExtractorLink(
                    name + "-StreamSb_MultiAudio",
                    name + "-StreamSb_MultiAudio",
                    mapped?.streamData?.file.toString(),
                    url,
                    Qualities.Unknown.value,
                    true,
                    headers
                )
            )
        }
        mapped?.streamData?.subs?.map {sub ->
            subtitleCallback.invoke(
                SubtitleFile(
                    sub.label.toString(),
                    sub.file ?: return@map null,
                )
            )
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.contains("dood", true)) {
            loadExtractor(data, subtitleCallback, callback)
        } else {
            loadStreamSB(data, subtitleCallback, callback)
        }

        return true
    }
}