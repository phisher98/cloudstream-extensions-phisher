package com.ycngmn

import com.lagradost.cloudstream3.DubStatus
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
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import android.content.SharedPreferences
import com.lagradost.cloudstream3.newSubtitleFile
import org.json.JSONObject
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import com.lagradost.cloudstream3.amap


class AnizoneProvider(private val sharedPref: SharedPreferences? = null) : MainAPI() {

    override var mainUrl = "https://anizone.to"
    override var name = "AniZone"
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
    )

    override var lang = "en"

    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasDownloadSupport = true

    override val mainPage = mainPageOf(
        "2" to "Latest TV Series",
        "4" to "Latest Movies",
        "6" to "Latest Web"
    )


    private var cookies = mutableMapOf<String, String>()
    private var wireData = mutableMapOf(
        "wireSnapshot" to "",
        "token" to ""
    )


    init {
        val initReq = Jsoup.connect("$mainUrl/anime")
            .method(Connection.Method.GET).execute()
        this.cookies = initReq.cookies()
        val doc = initReq.parse()
        wireData["token"] = doc.select("script[data-csrf]").attr("data-csrf")
        wireData["wireSnapshot"] = getSnapshot(doc)
        sortAnimeLatest()
    }


    private fun sortAnimeLatest() {
       liveWireBuilder(mapOf("sort" to "release-desc"), mutableListOf(), this.cookies, this.wireData, true)
    }


    private fun getSnapshot(doc : Document) : String {
        return doc.select("main div[wire:snapshot]")
            .attr("wire:snapshot").replace("&quot;", "\"")
    }
    private fun getSnapshot(json : JSONObject) : String {
        return json.getJSONArray("components")
            .getJSONObject(0).getString("snapshot")
    }

    private  fun getHtmlFromWire(json: JSONObject): Document {
        return Jsoup.parse(json.getJSONArray("components")
            .getJSONObject(0).getJSONObject("effects")
            .getString("html"))
    }


    private fun liveWireBuilder (updates : Map<String,String>, calls: List<Map<String, Any>>,
                                 biscuit : MutableMap<String, String>,
                                 wireCreds : MutableMap<String,String>, 
                                 remember : Boolean): JSONObject {

        val payload = mapOf(
            "_token" to wireCreds["token"], "components" to listOf(
                mapOf("snapshot" to wireCreds["wireSnapshot"], "updates" to updates,
                    "calls" to calls
                )
            )
        )
        
        val req = Jsoup.connect("$mainUrl/livewire/update")
            .method(Connection.Method.POST)
            .header("Content-Type", "application/json")
            .cookies(biscuit)
            .ignoreContentType(true)
            .requestBody(payload.toJson())
            .execute()

        if (remember) {
            wireCreds["wireSnapshot"] = getSnapshot(JSONObject(req.body()))
            biscuit.putAll(req.cookies())
        }

        return JSONObject(req.body())
    }
    
    
    override suspend fun getMainPage(page: Int, request: MainPageRequest
    ): HomePageResponse {

        val doc = getHtmlFromWire(
            liveWireBuilder(
                mapOf("type" to request.data), mutableListOf(
                    mapOf("path" to "", "method" to "loadMore", "params" to listOf<String>())
                ), this.cookies, this.wireData, true
            )
        )

        var home : List<Element> = doc.select("div[wire:key]")

        if (page>1)
            home = home.takeLast(12)

        return newHomePageResponse(
            HomePageList(request.name, home.map { toResult(it)}, isHorizontalImages = false),
            hasNext = (doc.selectFirst(".h-12[x-intersect=\"\$wire.loadMore()\"]")!=null)
        )
    }


    private fun extractTitleFromXData(xdata: String): String {
        var title = ""
        val prefLanguage = sharedPref?.getString("anizone_title_language", "1") ?: "1"

        val jsonMatch = Regex("""JSON\.parse\(['"](.+?)['"]\)""").find(xdata)
        if (jsonMatch != null) {
            val cleanJson = jsonMatch.groupValues[1].replace("\\u0022", "\"").replace("\\\\", "\\")
            try {
                val json = JSONObject(cleanJson)
                if (prefLanguage != "0" && json.has(prefLanguage)) {
                    title = json.getString(prefLanguage)
                }
            } catch (_: Exception) {}
        }
        
        // Try to get title from window.getTitle fallback
        if (title.isBlank()) {
            val titleMatch = Regex("""window\.getTitle\(.*?, *['"](.+?)['"]\)""").find(xdata)
            if (titleMatch != null) {
                title = titleMatch.groupValues[1].replace("&quot;", "\"").replace("\\'", "'")
            }
        }
        
        // Fallback: parse the JSON string directly
        if (title.isBlank()) {
            if (jsonMatch != null) {
                val cleanJson = jsonMatch.groupValues[1].replace("\\u0022", "\"").replace("\\\\", "\\")
                try {
                    val json = JSONObject(cleanJson)
                    title = json.keys().asSequence().firstOrNull()?.let { json.getString(it) } ?: ""
                } catch (_: Exception) {}
            }
        }
        return title
    }

    private fun toResult(post: Element): SearchResponse {
        val xdata = post.attr("x-data")
        val title = extractTitleFromXData(xdata)
        val url = post.selectFirst("a")?.attr("href") ?: ""

        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = post.selectFirst("img")?.attr("src")
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = getHtmlFromWire(liveWireBuilder(mapOf("search" to query),mutableListOf(), this.cookies, this.wireData,false))
        return doc.select("div[wire:key]").mapNotNull { toResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {

        val r = Jsoup.connect(url).method(Connection.Method.GET).execute()
        var doc = Jsoup.parse(r.body())
        val cookie = r.cookies()
        val wireData = mutableMapOf(
            "wireSnapshot" to getSnapshot(doc=r.parse()),
            "token" to doc.select("script[data-csrf]").attr("data-csrf")
        )
        
        var title = doc.selectFirst("h1")?.text()?.trim() ?: ""
        if (title.isBlank()) {
            val xdata = doc.selectFirst("[x-data*=\"displayAnimeTitle\"]")?.attr("x-data") ?: ""
            title = extractTitleFromXData(xdata)
        }
        if (title.isBlank()) {
            throw NotImplementedError("Unable to find title")
        }

        val bgImage = doc.selectFirst("main img")?.attr("src")
        val synopsis = doc.selectFirst(".sr-only + div")?.text() ?: ""

        val rowLines = doc.select("span.inline-block").map { it.text() }
        val releasedYear = rowLines.find { it.toIntOrNull() != null }
        val status = if (rowLines.contains("Completed")) ShowStatus.Completed
        else if (rowLines.contains("Ongoing")) ShowStatus.Ongoing else null

        val genres = doc.select("a[wire:navigate][wire:key]").map { it.text() }

        while (doc.selectFirst(".h-12[x-intersect=\"\$wire.loadMore()\"]")!=null) {
            doc = getHtmlFromWire(liveWireBuilder(
                mutableMapOf(), mutableListOf(
                    mapOf("path" to "", "method" to "loadMore", "params" to listOf<String>())
                ), cookie, wireData, true
            )
            )
        }

        // doc returns whole episodes including the previous ones.
        // so we iterate over it to scrap all at once.
        val epiElms = doc.select("li[x-data]")

        val episodes = epiElms.map{ elt ->
             newEpisode(
            data = elt.selectFirst("a")?.attr("href") ?: "") {
                 this.name = elt.selectFirst("h3")?.text()
                     ?.substringAfter(":")?.trim()
                 this.season = 0
                 
                 val spans = elt.select("span.line-clamp-1").map { it.text() }
                 this.date = spans.find { it.matches(Regex("""\d{4}-\d{2}-\d{2}""")) }?.let {
                     SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it)?.time
                 } ?: 0
                 
                 this.episode = spans.lastOrNull()?.toIntOrNull()
                 this.posterUrl = elt.selectFirst("img")?.attr("src")
             }
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {


            this.posterUrl = bgImage
            this.plot = synopsis
            this.tags = genres
            this.year = releasedYear?.toIntOrNull()
            this.showStatus = status
            //addSeasonNames(seasonList)
            addEpisodes(DubStatus.None, episodes)

        }

    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {


        val web = app.get(data).document
        val sourceName = web.selectFirst("span.truncate")?.text() ?: ""
        val mediaPlayer = web.selectFirst("media-player")
        val m3U8 = mediaPlayer?.attr("src") ?: ""

        mediaPlayer?.select("track")?.amap {
            subtitleCallback.invoke(
                newSubtitleFile (
                    it.attr("label"),
                    it.attr("src")
                )
            )
        }

        callback.invoke(
            newExtractorLink(
                sourceName,
                name,
                m3U8,
                type = ExtractorLinkType.M3U8
            )
        )

        return true
    }

}