package com.Phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mvvm.suspendSafeApiCall
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.jsoup.Jsoup
import kotlin.math.pow

class AnimePaheProvider : MainAPI() {
    companion object {
        const val MAIN_URL = "https://animepahe.ru"
        val headers = mapOf("Cookie" to "__ddg2_=1234567890")
        var cookies: Map<String, String> = mapOf()
        private fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Special")) TvType.OVA
            else if (t.contains("Movie")) TvType.AnimeMovie
            else TvType.Anime
        }

        val YTSM = Regex("ysmm = '([^']+)")

        val KWIK_PARAMS_RE = Regex("""\("(\w+)",\d+,"(\w+)",(\d+),(\d+),\d+\)""")
        val KWIK_D_URL = Regex("action=\"([^\"]+)\"")
        val KWIK_D_TOKEN = Regex("value=\"([^\"]+)\"")
        val YOUTUBE_VIDEO_LINK =
            Regex("""(^(?:https?:)?(?://)?(?:www\.)?(?:youtu\.be/|youtube(?:-nocookie)?\.(?:[A-Za-z]{2,4}|[A-Za-z]{2,3}\.[A-Za-z]{2})/)(?:watch|embed/|vi?/)*(?:\?[\w=&]*vi?=)?[^#&?/]{11}.*${'$'})""")
    }

    override var mainUrl = MAIN_URL
    override var name = "AnimePahe"
    override val hasQuickSearch = false
    override val hasMainPage = true

    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.Anime,
        TvType.OVA
    )

    override val mainPage =
        listOf(MainPageData("Latest Releases", "$mainUrl/api?m=airing&page=", true))

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        data class Data(
//            @JsonProperty("id") val id: Int,
//            @JsonProperty("anime_id") val animeId: Int,
            @JsonProperty("anime_title") val animeTitle: String,
//            @JsonProperty("anime_slug") val animeSlug: String,
            @JsonProperty("episode") val episode: Int?,
            @JsonProperty("snapshot") val snapshot: String?,
            @JsonProperty("created_at") val createdAt: String?,
            @JsonProperty("anime_session") val animeSession: String,
        )

        data class AnimePaheLatestReleases(
            @JsonProperty("total") val total: Int,
            @JsonProperty("data") val data: List<Data>
        )
        val response = app.get(request.data + page, headers = headers).text
        val episodes = parseJson<AnimePaheLatestReleases>(response).data.map {
            newAnimeSearchResponse(
                it.animeTitle,
                LoadData(it.animeSession, unixTime, it.animeTitle).toJson(),
                fix = false
            ) {
                this.posterUrl = it.snapshot
                addDubStatus(DubStatus.Subbed, it.episode)
            }
        }

        return HomePageResponse(listOf(HomePageList(request.name, episodes, request.horizontalImages)), episodes.isNotEmpty())
    }

    data class AnimePaheSearchData(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("slug") val slug: String?,
        @JsonProperty("title") val title: String,
        @JsonProperty("type") val type: String?,
        @JsonProperty("episodes") val episodes: Int?,
        @JsonProperty("status") val status: String?,
        @JsonProperty("season") val season: String?,
        @JsonProperty("year") val year: Int?,
        @JsonProperty("score") val score: Double?,
        @JsonProperty("poster") val poster: String?,
        @JsonProperty("session") val session: String,
        @JsonProperty("relevance") val relevance: String?
    )

    data class AnimePaheSearch(
        @JsonProperty("total") val total: Int,
        @JsonProperty("data") val data: List<AnimePaheSearchData>
    )


    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/api?m=search&l=8&q=$query"
        val headers = mapOf("referer" to "$mainUrl/","Cookie" to "__ddg2_=1234567890")

        val req = app.get(url, headers = headers).text
        val data = parseJson<AnimePaheSearch>(req)

        return data.data.map {
            newAnimeSearchResponse(
                it.title,
                LoadData(it.session, unixTime, it.title).toJson(),
                fix = false
            ) {
                this.posterUrl = it.poster
                addDubStatus(DubStatus.Subbed, it.episodes)
            }
        }
    }

    private data class AnimeData(
        @JsonProperty("id") val id: Int,
        @JsonProperty("anime_id") val animeId: Int,
        @JsonProperty("episode") val episode: Int,
        @JsonProperty("title") val title: String,
        @JsonProperty("snapshot") val snapshot: String,
        @JsonProperty("session") val session: String,
        @JsonProperty("filler") val filler: Int,
        @JsonProperty("created_at") val createdAt: String
    )

    private data class AnimePaheAnimeData(
        @JsonProperty("total") val total: Int,
        @JsonProperty("per_page") val perPage: Int,
        @JsonProperty("current_page") val currentPage: Int,
        @JsonProperty("last_page") val lastPage: Int,
        @JsonProperty("next_page_url") val nextPageUrl: String?,
        @JsonProperty("prev_page_url") val prevPageUrl: String?,
        @JsonProperty("from") val from: Int,
        @JsonProperty("to") val to: Int,
        @JsonProperty("data") val data: List<AnimeData>
    )

    data class LinkLoadData(
        @JsonProperty("mainUrl") val mainUrl: String,
        @JsonProperty("is_play_page") val is_play_page: Boolean,
        @JsonProperty("episode_num") val episode_num: Int,
        @JsonProperty("page") val page: Int,
        @JsonProperty("session") val session: String,
        @JsonProperty("episode_session") val episode_session: String,
    ) {
        val headers = mapOf("Cookie" to "__ddg2_=1234567890")
        suspend fun getUrl(): String? {
            return if (is_play_page) {
                "$mainUrl/play/${session}/${episode_session}"
            } else {
                val url = "$mainUrl/api?m=release&id=${session}&sort=episode_asc&page=${page + 1}"
                val jsonResponse = app.get(url,headers=headers).parsedSafe<AnimePaheAnimeData>() ?: return null
                val episode = jsonResponse.data.firstOrNull { it.episode == episode_num }?.session
                    ?: return null
                "$mainUrl/play/${session}/${episode}"
            }
        }
    }

    private suspend fun generateListOfEpisodes(session: String): ArrayList<Episode> {
        try {
            val uri = "$mainUrl/api?m=release&id=$session&sort=episode_asc&page=1"
            val req = app.get(uri, headers = headers).text
            val data = parseJson<AnimePaheAnimeData>(req)
            val lastPage = data.lastPage
            val perPage = data.perPage
            val total = data.total
            var ep = 1
            val episodes = ArrayList<Episode>()

            fun getEpisodeTitle(k: AnimeData): String {
                return k.title.ifEmpty {
                    "Episode ${k.episode}"
                }
            }

            if (lastPage == 1 && perPage > total) {
                data.data.forEach {
                    episodes.add(
                        newEpisode(
                            LinkLoadData(
                                mainUrl,
                                true,
                                0,
                                0,
                                session,
                                it.session
                            ).toJson()
                        ) {
                            addDate(it.createdAt)
                            this.name = getEpisodeTitle(it)
                            this.posterUrl = it.snapshot
                        }
                    )
                }
            } else {
                for (page in 0 until lastPage) {
                    for (i in 0 until perPage) {
                        if (ep <= total) {
                            episodes.add(
                                Episode(
                                    LinkLoadData(
                                        mainUrl,
                                        false,
                                        ep,
                                        page + 1,
                                        session,
                                        ""
                                    ).toJson()
                                )
                            )
                            ++ep
                        }
                    }
                }
            }
            return episodes
        } catch (e: Exception) {
            return ArrayList()
        }
    }

    /**
     * Required to make bookmarks work with a session system
     **/
    data class LoadData(val session: String, val sessionDate: Long, val name: String)

    override suspend fun load(url: String): LoadResponse? {
        return suspendSafeApiCall {
            val session = parseJson<LoadData>(url).let { data ->
                // Outdated
                if (data.sessionDate + 60 * 10 < unixTime) {
                    parseJson<LoadData>(
                        search(data.name).firstOrNull()?.url ?: return@let null
                    ).session
                } else {
                    data.session
                }
            } ?: return@suspendSafeApiCall null
            val html = app.get("https://animepahe.ru/anime/$session",headers=headers).text
            val doc = Jsoup.parse(html)
            val japTitle = doc.selectFirst("h2.japanese")?.text()
            val animeTitle = doc.selectFirst("span.sr-only.unselectable")?.text()
            val poster = doc.selectFirst(".anime-poster a")?.attr("href")

            val tvType = doc.selectFirst("""a[href*="/anime/type/"]""")?.text()

            val trailer: String? = if (html.contains("https://www.youtube.com/watch")) {
                YOUTUBE_VIDEO_LINK.find(html)?.destructured?.component1()
            } else {
                null
            }

            val episodes = generateListOfEpisodes(session)
            val year = Regex("""<strong>Aired:</strong>[^,]*, (\d+)""")
                .find(html)?.destructured?.component1()
                ?.toIntOrNull()

            val status =
                if (doc.selectFirst("a[href='/anime/airing']") != null)
                    ShowStatus.Ongoing
                else if (doc.selectFirst("a[href='/anime/completed']") != null)
                    ShowStatus.Completed
                else null

            val synopsis = doc.selectFirst(".anime-synopsis")?.text()

            var anilistId: Int? = null
            var malId: Int? = null

            doc.select(".external-links > a").forEach { aTag ->
                val split = aTag.attr("href").split("/")

                if (aTag.attr("href").contains("anilist.co")) {
                    anilistId = split[split.size - 1].toIntOrNull()
                } else if (aTag.attr("href").contains("myanimelist.net")) {
                    malId = split[split.size - 1].toIntOrNull()
                }
            }

            newAnimeLoadResponse(animeTitle ?: japTitle ?: "", url, getType(tvType.toString())) {
                engName = animeTitle
                japName = japTitle
                this.posterUrl = poster
                this.year = year
                addEpisodes(DubStatus.Subbed, episodes)
                this.showStatus = status
                plot = synopsis
                tags = if (!doc.select(".anime-genre > ul a").isEmpty()) {
                    ArrayList(doc.select(".anime-genre > ul a").map { it.text().toString() })
                } else {
                    null
                }

                addMalId(malId)
                addAniListId(anilistId)
                addTrailer(trailer)
            }
        }
    }


    private fun isNumber(s: String?): Boolean {
        return s?.toIntOrNull() != null
    }

    private fun cookieStrToMap(cookie: String): Map<String, String> {
        val cookies = mutableMapOf<String, String>()
        for (string in cookie.split("; ")) {
            val split = string.split("=").toMutableList()
            val name = split.removeFirst().trim()
            val value = if (split.size == 0) {
                "true"
            } else {
                split.joinToString("=")
            }
            cookies[name] = value
        }
        return cookies.toMap()
    }

    private fun getString(content: String, s1: Int, s2: Int): String {
        val characterMap = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ+/"

        val slice2 = characterMap.slice(0 until s2)
        var acc: Long = 0

        for ((n, i) in content.reversed().withIndex()) {
            acc += (when (isNumber("$i")) {
                true -> "$i".toLong()
                false -> "0".toLong()
            }) * s1.toDouble().pow(n.toDouble()).toInt()
        }

        var k = ""

        while (acc > 0) {
            k = slice2[(acc % s2).toInt()] + k
            acc = (acc - (acc % s2)) / s2
        }

        return when (k != "") {
            true -> k
            false -> "0"
        }
    }

    private fun decrypt(fullString: String, key: String, v1: Int, v2: Int): String {
        var r = ""
        var i = 0

        while (i < fullString.length) {
            var s = ""

            while (fullString[i] != key[v2]) {
                s += fullString[i]
                ++i
            }
            var j = 0

            while (j < key.length) {
                s = s.replace(key[j].toString(), j.toString())
                ++j
            }
            r += (getString(s, v2, 10).toInt() - v1).toChar()
            ++i
        }
        return r
    }

    private fun zipGen(gen: Sequence<Pair<Int, Int>>): ArrayList<Pair<Pair<Int, Int>, Pair<Int, Int>>> {
        val allItems = gen.toList().toMutableList()
        val newList = ArrayList<Pair<Pair<Int, Int>, Pair<Int, Int>>>()

        while (allItems.size > 1) {
            newList.add(Pair(allItems[0], allItems[1]))
            allItems.removeAt(0)
            allItems.removeAt(0)
        }
        return newList
    }

    private fun decodeAdfly(codedKey: String): String {
        var r = ""
        var j = ""

        for ((n, l) in codedKey.withIndex()) {
            if (n % 2 != 0) {
                j = l + j
            } else {
                r += l
            }
        }

        val encodedUri = ((r + j).toCharArray().map { it.toString() }).toMutableList()
        val numbers = sequence {
            for ((i, n) in encodedUri.withIndex()) {
                if (isNumber(n)) {
                    yield(Pair(i, n.toInt()))
                }
            }
        }

        for ((first, second) in zipGen(numbers)) {
            val xor = first.second.xor(second.second)
            if (xor < 10) {
                encodedUri[first.first] = xor.toString()
            }
        }
        var returnValue = String(encodedUri.joinToString("").toByteArray(), Charsets.UTF_8)
        returnValue = base64Decode(returnValue)
        return returnValue.slice(16..returnValue.length - 17)
    }

    private data class VideoQuality(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("audio") val audio: String?,
        @JsonProperty("kwik") val kwik: String?,
        @JsonProperty("kwik_pahewin") val kwikPahewin: String
    )

    private data class AnimePaheEpisodeLoadLinks(
        @JsonProperty("data") val data: List<Map<String, VideoQuality>>
    )

    private suspend fun getStreamUrlFromKwik(url: String?): String? {
        if (url == null) return null
        val response =
            app.get(
                url,
                headers = mapOf("referer" to mainUrl),
                cookies = cookies
            ).text
        val unpacked = getAndUnpack(response)
        return Regex("source=\'(.*?)\'").find(unpacked)?.groupValues?.get(1)
    }


    private suspend fun extractVideoLinks(
        data: String,
        callback: (ExtractorLink) -> Unit
    ) {
        val parsed = parseJson<LinkLoadData>(data)
        val headers = mapOf("referer" to "$mainUrl/")
        val episodeUrl = parsed.getUrl() ?: return

        val text = app.get(episodeUrl, headers = headers).text
        val urlRegex = Regex("""let url = "(.*?)";""")
        val embed = urlRegex.find(text)?.groupValues?.getOrNull(1) ?: return

        getStreamUrlFromKwik(embed)?.let { link ->
            callback(
                ExtractorLink(
                    this.name,
                    "Kwik",
                    link,
                    "https://kwik.cx/",
                    Qualities.Unknown.value,
                    link.contains(".m3u8")
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
        val parsed = parseJson<LinkLoadData>(data)
        val episodeUrl = parsed.getUrl() ?: ""
        val document= app.get(episodeUrl, headers= headers).document
        document.select("div.dropup button")
            .map {
                var lang=""
                val dub=it.select("span").text()
                if (dub.contains("eng")) lang="DUB" else lang="SUB"
                val quality = it.attr("data-resolution")
                val href = it.attr("data-src")
                    Log.d("Phisher", "$lang $quality $href")
                        loadSourceNameExtractor(
                            "Animepahe [$lang]",
                            href,
                            mainUrl,
                            subtitleCallback,
                            callback,
                            quality
                        )
            }
        return true
    }
}
