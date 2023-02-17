package com.likdev256

//import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.jsoup.nodes.Element

class MovieHUBProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://madstream.live"
    override var name = "MadStream"
    override val hasMainPage = true
    override var lang = "ta"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Live
    )

    override val mainPage = mainPageOf(
        "$mainUrl/" to "LiveStreams",
        "$mainUrl/index.php" to "JioTv",
        "$mainUrl/epic/index.php" to "Epic",
        "$mainUrl/sports.php" to "Sports",
        "$mainUrl/sony.php" to "Sony"
    )

    data class LiveStreamLinks (
        @JsonProperty("title")  val title: String,
        @JsonProperty("poster") val poster: String,
        @JsonProperty("link")   val link: String,
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data).document

        //Log.d("Document", request.data)
        val home = if (request.data.contains("epic")) {
            document.select("div.col-md-6").mapNotNull {
                it.toEpicSearchResult()
            }
        } else {
            document.select("div.col-6").mapNotNull {
                it.toSearchResult()
            }
        }

        return HomePageResponse(arrayListOf(HomePageList(request.name, home, isHorizontalImages = true)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse {
        //Log.d("Got","got here")
        val titleRaw = this.selectFirst("div.card > a > div.info > span")?.text()?.trim()
        val title = if (titleRaw.isNullOrBlank()) "Unknown LiveStream" else titleRaw.toString()
        //Log.d("title", title)
        val posterUrl = fixUrl(this.select("img").attr("src"))
        //Log.d("posterUrl", posterUrl)
        val href = fixUrl(this.selectFirst("a")?.attr("href").toString())
        //Log.d("mybadhref", href)
        val loadData = LiveStreamLinks(
                title,
                posterUrl,
                href
            ).toJson()

        return newMovieSearchResponse(title, loadData, TvType.Live) {
                this.posterUrl = posterUrl
            }
    }

    private fun Element.toEpicSearchResult(): SearchResponse {
        //Log.d("Got","got here")
        val titleRaw = this.selectFirst("div.livetv > h3 > span")?.text()?.trim()
        val title = if (titleRaw.isNullOrBlank()) "Unknown LiveStream" else titleRaw.toString()
        //Log.d("title", title)
        val posterUrl = fixUrl(this.select("div.livetc-right > img").attr("src"))
        //Log.d("posterUrl", posterUrl)
        val href = fixUrl(this.selectFirst("div.livetv > a")?.attr("href").toString())
        //Log.d("href", href)
        val loadData = LiveStreamLinks(
            title,
            posterUrl,
            href
        ).toJson()

        return newMovieSearchResponse(title, loadData, TvType.Live) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/").document
        //Log.d("document", document.toString())

        return doc.select("div.col-6").mapNotNull {
            it.toSearchResult()
        }.filter { it.name.contains(query, true) }
    }

    override suspend fun load(url: String): LoadResponse {
        val data = parseJson<LiveStreamLinks>(url)
        val title = data.title
        val poster = data.poster
        val link = data.link

        return newMovieLoadResponse(title, link, TvType.Live, link) {
                this.posterUrl = poster
            }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        //Log.d("mybadstreamData", data)
        safeApiCall {
            if (data.endsWith(".m3u8")) {
                callback.invoke(
                    ExtractorLink(
                        name,
                        name,
                        data,
                        data,
                        Qualities.Unknown.value,
                        true
                    )
                )
            } else if (data.contains(".m3u8", true)) {
                val stream = "https" + data.replace(Regex("https:\\/\\/.*\\.php\\?https"), "")
                //Log.d("mybadstreamDirect", stream)
                callback.invoke(
                    ExtractorLink(
                        name,
                        name,
                        stream,
                        "",
                        Qualities.Unknown.value,
                        true
                    )
                )
            } else if (data.contains(".png", true)) {
                val id = Regex("\\?id=(\\d*)\\&").find(data)?.groups?.get(1)?.value.toString()
                val stream = "https://livesportsclub.tech/getm3u8/$id/master.m3u8"
                //Log.d("mybadstreamTECH", stream)
                callback.invoke(
                    ExtractorLink(
                        "$name-livesportsclub.tech",
                        "$name-livesportsclub.tech",
                        stream,
                        data,
                        Qualities.Unknown.value,
                        true
                    )
                )
            } else {
                val streamRaw = app.get(data).document
                    .select("video source").attr("src")
                    .replace("livesportsclub.tk", "livesportsclub.me")
                val stream = if (streamRaw.contains("http", true)) streamRaw
                    else "http://livesportsclub.me/sports/$streamRaw"
                //Log.d("mybadstreamME", stream)
                callback.invoke(
                    ExtractorLink(
                        "$name-livesportsclub.me",
                        "$name-livesportsclub.me",
                        stream,
                        data.replace("livesportsclub.tk", "livesportsclub.me"),
                        Qualities.Unknown.value,
                        true
                    )
                )
            }
        }

        return true
    }
}
