package com.Goojara

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.FormBody
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.util.regex.Pattern

class Goojara : MainAPI() {
    override var mainUrl              = "https://ww1.goojara.to"
    override var name                 = "Goojara"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasDownloadSupport   = true
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie,TvType.TvSeries)

    override val mainPage = mainPageOf(
        "watch-movies-recent" to "Recently Updated Movies",
        "watch-series-recent" to "Recently Updated Series",
        "watch-movies-popular" to "Popular Movies",
        "watch-series-popular" to "Popular Series",
    )

    companion object
    {
        const val TMDBIMAGEBASEURL = "https://image.tmdb.org/t/p/original"
        val headers = mapOf("Cookie" to "aGooz=dg18hh2eittp5e7s53u0e6bloh; 98ef5a07=747ffc60ea65eb361a495f; _997e=CC3E288A8E177D1A15AC79C049BCE3162D678A00; 3d4930c4=6239ad831b7cfd09950432; _2252=8A4FEB904DF45EB188E25A7A89432E0E489A5ADA; 12cd410d=77da7901426e0f0c27e062; _3553=3DB01E776983EE4DACE282E616C9B7B4FB2E2D3D")
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val res = app.get("$mainUrl/${request.data}?p=$page").document
        val home= res.select("#xbrd > div:nth-child(4) a").map {
            it.toSearchResult()
        }
        return newHomePageResponse(
            list    = HomePageList(
                name               = request.name,
                list               = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("span.mtl").text()
        val href = this.attr("href")
        val posterUrl = this.select("img").attr("data-src")
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }

    }

    //override suspend fun quickSearch(query: String): List<SearchResponse> = search(query,1).items

    private val DEFAULT_POSTER = "https://thumbs.dreamstime.com/b/cinema-poster-design-template-popcorn-box-disposable-cup-beverages-straw-film-strip-clapper-board-ticket-detailed-44098150.jpg"

    override suspend fun search(query: String, page: Int): SearchResponseList = coroutineScope {
        val url = "$mainUrl/xmre.php"

        val body = FormBody.Builder()
            .add("z", "Mwxxa3Vnaw")
            .add("x", "b3716e05ff")
            .add("q", query)
            .build()


        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0",
            "Accept" to "*/*",
            "Content-Type" to "application/x-www-form-urlencoded",
            "Referer" to mainUrl,
            "Cookie" to "aGooz=b2orla8fv69k6a7c31knrqkljg"
        )

        val results = app.post(
            url,
            requestBody = body,
            headers = headers
        ).document.select("li a")

        val concurrency = 10
        val sem = Semaphore(concurrency)

        val deferred = results.map { el ->
            async(Dispatchers.IO) {
                sem.withPermit {
                    try {
                        val href = el.attr("href").trim()
                        val title = el.text().trim()

                        val doc = app.get(fixUrl(href), headers = headers).document

                        val poster = doc.selectFirst("div.imrl img")?.attr("src")
                        val finalHref = doc.selectFirst("div.snfo h1 a")?.attr("href") ?: href


                        newMovieSearchResponse(
                            title,
                            fixUrl(finalHref),
                            TvType.Movie
                        ) {
                            this.posterUrl = poster ?: DEFAULT_POSTER
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }

        val responses = deferred.awaitAll().filterNotNull()
        return@coroutineScope responses.toNewSearchResponseList()
    }


    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).documentLarge
        val raw = document.selectFirst("div.marl h1")
            ?.text()
            ?.substringBefore("(")
            .orEmpty()

        val seasonRegex = Regex("""(Season\s*\d+|S\d+)""", RegexOption.IGNORE_CASE)
        val match = seasonRegex.find(raw)

        val title = if (match != null) {
            raw.take(match.range.first).trim()
        } else {
            raw.trim()
        }

        val poster = fixUrl(document.select("#poster img").attr("src"))
        val description = document.selectFirst("div.fimm p")?.text()
        val type = if (document.select("#sesh a.ste,#sesh button").isNotEmpty()) TvType.TvSeries else TvType.Movie
        val imdbid = document.selectFirst("#imdb")?.attr("data-ubv")?.substringBefore(",")
        val metatype = if (type == TvType.TvSeries) "series" else "movie"
        val tmdbmetatype = if (type == TvType.TvSeries) "tv" else "movie"

        val metares = app.get("https://aiometadata.elfhosted.com/stremio/b7cb164b-074b-41d5-b458-b3a834e197bb/meta/$metatype/$imdbid.json").textLarge
        val tmdbId: String? = imdbid?.let { id ->
            runCatching {
                val json = app.get(
                    "https://api.themoviedb.org/3/find/$id?api_key=1865f43a0549ca50d341dd9ab8b29f49&external_source=imdb_id"
                ).textLarge

                val obj = JSONObject(json)

                obj.getJSONArray("movie_results")
                    .optJSONObject(0)
                    ?.optInt("id")
                    ?.takeIf { it != 0 }
                    ?.toString()
                    ?: obj.getJSONArray("tv_results")
                        .optJSONObject(0)
                        ?.optInt("id")
                        ?.takeIf { it != 0 }
                        ?.toString()
            }.getOrNull()
        }

        val movieCreditsJsonText = tmdbId?.let { id ->
            runCatching {
                app.get("https://api.themoviedb.org/3/$tmdbmetatype/$id/credits?api_key=1865f43a0549ca50d341dd9ab8b29f49&language=en-US").textLarge
            }.getOrNull()
        }
        val castList = parseCredits(movieCreditsJsonText)

        val metaJson = try {
            JSONObject(metares).optJSONObject("meta")
        } catch (_: Exception) {
            null
        }

        val metaPoster = metaJson?.optString("poster").takeUnless { it.isNullOrBlank() } ?: poster
        val metaBackground = metaJson?.optString("background").takeUnless { it.isNullOrBlank() }
        val metaDescription = metaJson?.optString("description") ?: description
        val metaGenres = metaJson?.optJSONArray("genres")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { s -> s.isNotBlank() } }
        } ?: emptyList()

        val epMetaMap: Map<String, JSONObject> = metaJson?.optJSONArray("videos")?.let { vids ->
            val map = mutableMapOf<String, JSONObject>()
            for (i in 0 until vids.length()) {
                val v = vids.optJSONObject(i) ?: continue
                val s = v.optInt("season", -1)
                val e = v.optInt("episode", -1)
                if (s > 0 && e > 0) {
                    map["$s:$e"] = v
                }
            }
            map
        } ?: emptyMap()

        return if (type == TvType.TvSeries) {
            val href = document.select("#sesh a.ste").attr("href")
            val totalSeasons = href.substringAfter("?s=").toIntOrNull() ?: 1
            val episodes = mutableListOf<Episode>()

            for (seasonIndex in 1..totalSeasons) {
                val seasonHref = href.substringBefore("?s=") + "?s=$seasonIndex"
                val seasonDoc = app.get(seasonHref).document
                seasonDoc.select("div.seho")
                    .mapNotNull { input ->
                        val hrefEp = fixUrl(input.select("a").attr("href"))
                        val epnoText = input.select("span.sea").text().substringAfter("0").trim()
                        val epno = epnoText.toIntOrNull()

                        if (hrefEp.isBlank() || epno == null) return@mapNotNull null
                        val metaKey = "$seasonIndex:$epno"
                        val epMeta = epMetaMap[metaKey]
                        val epnameFromPage = input.select("a").text().takeIf { it.isNotBlank() }
                        val epTitle = epMeta?.optString("title")?.takeIf { it.isNotBlank() } ?: epnameFromPage
                        val epPosterFromMeta = epMeta?.optString("thumbnail")?.takeIf { it.isNotBlank() }?.let { fixUrl(it) }
                        val released = epMeta?.optString("released")?.takeIf { it.isNotBlank() }
                        val epOverview = epMeta?.optString("overview")?.takeIf { it.isNotBlank() }
                        episodes+=newEpisode(hrefEp) {
                            this.name = epTitle
                            this.season = seasonIndex
                            this.episode = epno
                            this.description = epOverview
                            if (epPosterFromMeta != null) this.posterUrl = epPosterFromMeta
                            addDate(released)
                        }
                    }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = metaPoster
                this.plot = metaDescription
                if (metaGenres.isNotEmpty()) this.tags = metaGenres
                addImdbId(imdbid)
                this.backgroundPosterUrl = metaBackground
                this.actors = castList
            }
        } else newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = metaPoster
            this.plot = metaDescription
            if (metaGenres.isNotEmpty()) this.tags = metaGenres
            addImdbId(imdbid)
            this.backgroundPosterUrl = metaBackground
            this.actors = castList

        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val initialResp = app.get(data, mapOf("Referer" to "https://www.goojara.to", "Cookie" to ""))
        val doc = initialResp.documentLarge
        val bodyHtml = doc.outerHtml()
        val setCookieHeader = initialResp.headers["Set-Cookie"] ?: initialResp.headers["set-cookie"] ?: ""
        val cookieMap = parseSetCookieHeaders(listOf(setCookieHeader))
        val randomPair = extract3chkPair(bodyHtml)
        val cookieHeader = buildCookieHeader(cookieMap["aGooz"], randomPair)
        doc.select("#drl a").forEach { element ->
            val href = element.attr("href")
            if (href.isEmpty()) return@forEach
            try {
                val redirectResp = app.get(href, mapOf("Referer" to "https://ww1.goojara.to", "Cookie" to cookieHeader), allowRedirects = false)
                val iframe = redirectResp.headers["location"] ?: redirectResp.headers["Location"] ?: return@forEach
                Log.d("Phisher", iframe)
                loadSourceNameExtractor("", iframe, "", Qualities.P720.value, subtitleCallback, callback)
            } catch (e: Exception) {
                Log.w("Phisher", "failed to fetch embed redirect: ${e.message}")
            }
        }
        return true
    }

    /** Parse multiple Set-Cookie header strings into a name->value map. */
    private fun parseSetCookieHeaders(headers: List<String>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val cookiePattern = Pattern.compile("^\\s*([^=;\\s]+)=([^;\\r\\n]*)")
        headers.filter { it.isNotBlank() }.forEach { h ->
            val matcher = cookiePattern.matcher(h)
            if (matcher.find()) {
                val name = matcher.group(1)
                val value = matcher.group(2)
                if (!name.isNullOrEmpty() && value != null) map[name] = value
            }
        }
        return map
    }

    /** Extract pair from _3chk('name','value') */
    private fun extract3chkPair(body: String): Pair<String, String>? {
        val p = Pattern.compile("_3chk\\(\\s*'([^']+)'\\s*,\\s*'([^']+)'")
        val m = p.matcher(body)
        return if (m.find()) Pair(m.group(1), m.group(2)) else null
    }

    /** Build Cookie header combining aGooz and the random cookie. Returns empty string if none. */
    private fun buildCookieHeader(aGooz: String?, random: Pair<String, String>?): String {
        val parts = mutableListOf<String>()
        if (!aGooz.isNullOrEmpty()) parts += "aGooz=$aGooz"
        if (random != null) parts += "${random.first}=${random.second}"
        return parts.joinToString("; ")
    }

    fun parseCredits(jsonText: String?): List<ActorData> {
        if (jsonText.isNullOrBlank()) return emptyList()
        val list = ArrayList<ActorData>()
        val root = JSONObject(jsonText)
        val castArr = root.optJSONArray("cast") ?: return list
        for (i in 0 until castArr.length()) {
            val c = castArr.optJSONObject(i) ?: continue
            val name = c.optString("name").takeIf { it.isNotBlank() } ?: c.optString("original_name").orEmpty()
            val profile = c.optString("profile_path").takeIf { it.isNotBlank() }?.let { "$TMDBIMAGEBASEURL$it" }
            val character = c.optString("character").takeIf { it.isNotBlank() }
            val actor = Actor(name, profile)
            list += ActorData(actor, roleString = character)
        }
        return list
    }

    suspend fun loadSourceNameExtractor(
        source: String,
        url: String,
        referer: String? = null,
        quality: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        loadExtractor(url, referer, subtitleCallback) { link ->
            CoroutineScope(Dispatchers.IO).launch {
                callback.invoke(
                    newExtractorLink(
                        "${link.source} $source",
                        "${link.source} $source",
                        link.url,
                    ) {
                        this.quality = quality ?: link.quality
                        this.type = link.type
                        this.referer = link.referer
                        this.headers = link.headers
                        this.extractorData = link.extractorData
                    }
                )
            }
        }
    }
}
