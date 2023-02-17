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
        "$mainUrl/index.php" to "TV Shows",
        "$mainUrl/epic/index.php" to "Action",
        "$mainUrl/sports.php" to "Adventure",
        "$mainUrl/sony.php" to "Animation"
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
        val home = document.select("div.col-6").mapNotNull {
                it.toSearchResult()
            }

        return HomePageResponse(arrayListOf(HomePageList(request.name, home, isHorizontalImages = true)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        //Log.d("Got","got here")
        val title = this.select("div.card > a > div.info > span").text().trim()
        //Log.d("title", title)
        val posterUrl = fixUrlNull(this.select("div.card > a > img").attr("src"))
        //Log.d("posterUrl", posterUrl.toString())
        val href = fixUrl(this.select("div.card > a ").attr("href"))
        //Log.d("href", href)
        val loadData = PrivateLinks(
                title,
                posterUrl,
                href
            ).toJson()

        return newLiveStreamSearchResponse(title, loadData, TvType.Live) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/").document
        //Log.d("document", document.toString())

        return doc.select("div.col-6").mapNotNull {
            it.toSearchResult()
        }.filter { it.name.contains(query, true) }
    }

    override suspend fun load(url: String): LoadResponse? {
        val data = parseJson<LiveStreamLinks>(url)
        val title =
        val poster =
        val link =

        return newLiveStreamLoadResponse(title, url, TvType.Live, link) {
                this.posterUrl = poster
            }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        if (data.contains(".m3u8", true)) {

        } else if (data.contains(".png", true)) {

        } else {

        }

        return true
    }
}
