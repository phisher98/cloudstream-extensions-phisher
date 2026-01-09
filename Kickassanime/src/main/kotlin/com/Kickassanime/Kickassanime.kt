package com.kickassanime

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.Gson
import com.kickassanime.CryptoAES.decodeHex
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.jsoup.Jsoup
import java.security.MessageDigest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Calendar

class Kickassanime : MainAPI() {
    override var mainUrl = "https://kaa.to"
    override var name = "Kickassanime"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch =  true
    override val hasDownloadSupport = true

    override val supportedSyncNames = setOf(
        SyncIdName.MyAnimeList,
        SyncIdName.Anilist
    )

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    companion object {
        var mainUrl = "https://kaa.to"
        fun getStatus(t: String): ShowStatus {
            return when (t) {
                "finished_airing" -> ShowStatus.Completed
                "currently_airing" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    override val mainPage = mainPageOf(
        "filters=${generateFilterWithCurrentYear()}" to "Airing",
        "$mainUrl/api/show/trending" to "Trending",
        "$mainUrl/api/show/popular" to "Popular Animes",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse? {
        val url=if (request.data.startsWith("filter"))
        {
            "${mainUrl}/api/anime?page=$page&${request.data}"
        }
        else
        {
            "${request.data}?page=$page"
        }
        val home = app.get(url).parsedSafe<ResponseHome>()?.result?.map { media ->
            media.toSearchResponse()
        }
        return home?.let { newHomePageResponse(request.name, it) }
    }

    private fun getProperAnimeLink(uri: String): String {
        return when {
            uri.contains("/episode") -> fixUrl(uri.substringBeforeLast("/"))
            else -> fixUrl(uri)
        }
    }

    private fun Result.toSearchResponse(): SearchResponse {
        val href = getProperAnimeLink(this.slug)
        val title = (this.titleEn ?: this.title).replace("\"","")
        val posterUrl = getImageUrl(this.poster.hq)
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query,1).items

    override suspend fun search(query: String,page: Int): SearchResponseList {
val json = """
{   "page": "$page",
    "query": "$query"
}
""".trimIndent()
        val host = app.get(mainUrl, allowRedirects = false).headers["location"]
        val mediaType = "application/json".toMediaType()
        val requestBody = json.toRequestBody(mediaType)
        val headers= mapOf(
            "Accept" to "*/*",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            "Content-Type" to "application/json",
            "x-origin" to "kickass-anime.ru"
        )
        val res=app.post("${host}api/fsearch", requestBody = requestBody, headers = headers).toString()
        return tryParseJson<Search>(res)?.result?.map {
            it.toSearchResponse()
        }?.toNewSearchResponseList() ?: throw ErrorLoadingException("Invalid Json reponse")
    }

    private fun SearchResult.toSearchResponse(): SearchResponse {
        val title=this.titleEn ?: this.title
        val poster= getImageUrl(this.poster.hq)
        val href="${mainUrl}/${this.slug}"
        return newAnimeSearchResponse(
            title,
            href,
            TvType.TvSeries,
        ) {
            this.posterUrl=poster
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val showName = url.substringAfter(mainUrl)
        val loadJson = app.get("$mainUrl/api/show/$showName").parsedSafe<loadres>()

        val title = loadJson?.titleEn.takeUnless { it.isNullOrEmpty() } ?: loadJson?.title ?: "Unknown"
        val poster = loadJson?.banner?.hq?.let(::getBannerUrl) ?: loadJson?.poster?.hq?.let(::getImageUrl)
        val description = loadJson?.synopsis
        val tags = loadJson?.genres
        val status = getStatus(loadJson?.status ?: "")

        val episodesJson = app.get("$mainUrl/api/show$showName/episodes?ep=1&lang=ja-JP").toString()
        val episodes = parseJsonToEpisodes(episodesJson).mapNotNull { epJson ->
            val epNumber = epJson.episode_number.toString().substringBefore(".").toIntOrNull() ?: return@mapNotNull null
            newEpisode("$mainUrl/api/show$showName/episode/ep-$epNumber-${epJson.slug}") {
                name = epJson.title
                episode = epNumber
                posterUrl = getThumbnailUrl(epJson.thumbnail.hq)
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.Anime, episodes).apply {
            name = title
            posterUrl = poster
            backgroundPosterUrl = poster
            plot = description
            this.tags = tags
            showStatus = status
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).parsedSafe<ServersRes>()?.servers?.amap{ it ->
            if(it.name.contains("VidStreaming")) {
                val host = getBaseUrl(it.src)
                val headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                val key = "e13d38099bf562e8b9851a652d2043d3".toByteArray()
                val query = it.src.substringAfter("?id=").substringBefore("&")
                val html = app.get(it.src).toString()

                //If HTML have m3u8
                if (html.contains(".m3u8", ignoreCase = true)) {
                    val match = Regex("""(https?:)?//[^\s"'<>]+\.m3u8""", RegexOption.IGNORE_CASE)
                        .find(html)

                    val videoheaders = mapOf(
                        "Accept" to "*/*",
                        "Accept-Language" to "en-US,en;q=0.5",
                        "Origin" to host,
                        "Sec-Fetch-Dest" to "empty",
                        "Sec-Fetch-Mode" to "cors",
                        "Sec-Fetch-Site" to "cross-site"
                    )

                    match?.value?.let { url ->
                        val m3u8Url = if (url.startsWith("//")) "https:$url" else url
                        callback.invoke(
                            newExtractorLink(
                                "VidStreaming",
                                "VidStreaming",
                                m3u8Url,
                                ExtractorLinkType.M3U8
                            )
                            {
                                this.quality = Qualities.P1080.value
                                this.headers = videoheaders
                            }
                        )
                    }
                }

                val (sig, timeStamp, route) = getSignature(html, it.name, query, key) ?: return@amap
                val sourceurl = "$host$route?id=$query&e=$timeStamp&s=$sig"
                val encjson = app.get(sourceurl, headers = headers).parsedSafe<Encrypted>()?.data
                    ?: "Not Found"

                val (encryptedData, ivhex) = encjson.substringAfter(":\"")
                    .substringBefore('"')
                    .replace("\\", "")
                    .split(":")
                val iv = ivhex.decodeHex()
                val decrypted =
                    tryParseJson<m3u8>(CryptoAES.decrypt(encryptedData, key, iv).toJson())

                val m3u8 = httpsify(decrypted?.hls!!)
                val videoheaders = mapOf(
                    "Accept" to "*/*",
                    "Accept-Language" to "en-US,en;q=0.5",
                    "Origin" to host,
                    "Sec-Fetch-Dest" to "empty",
                    "Sec-Fetch-Mode" to "cors",
                    "Sec-Fetch-Site" to "cross-site"
                )

                callback.invoke(
                    newExtractorLink(
                        "VidStreaming",
                        "VidStreaming",
                        m3u8,
                        ExtractorLinkType.M3U8
                        )
                        {
                            this.quality = Qualities.P1080.value
                            this.headers = videoheaders
                        }
                )

                decrypted.subtitles.amap {
                    subtitleCallback.invoke(
                        newSubtitleFile(
                            it.name,  // Use label for the name
                            it.src     // Use extracted URL
                        )
                    )
                }
            }
            else
            if (it.name.contains("BirdStream"))
            {
                val baseurl=getBaseUrl(it.src)

                val headers = mapOf(
                    "Origin" to baseurl,
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                )

                val res= app.get(it.src, headers = headers).text

                val regex = Regex("""props="(.*?)"""")
                val match = regex.find(res)
                val encodedJson = match?.groupValues?.get(1)

                if (encodedJson != null) {
                    val unescapedJson = org.jsoup.parser.Parser.unescapeEntities(encodedJson, false)
                    val json = JSONObject(unescapedJson)

                    val videoUrl = "https:" + json.getJSONArray("manifest").getString(1)
                    callback.invoke(
                        newExtractorLink(
                            "CatStream",
                            "CatStream DASH",
                            videoUrl,
                            ExtractorLinkType.M3U8
                        )
                        {
                            this.referer = baseurl
                            this.quality = Qualities.P1080.value
                            this.headers = headers
                        }
                    )

                    val subtitleArray = json.getJSONArray("subtitles").getJSONArray(1)

                    for (i in 0 until subtitleArray.length()) {
                        val sub = subtitleArray.getJSONArray(i).getJSONObject(1)

                        val src = sub.getJSONArray("src").getString(1)
                        val name = sub.getJSONArray("name").getString(1)
                        subtitleCallback.invoke(
                            newSubtitleFile(
                                name,  // Use label for the name
                                src    // Use extracted URL
                            )
                        )
                    }

                } else {
                    println("Could not find embedded JSON in props attribute")
                }
            }
            else
                if (it.name.contains("CatStream")) {

                    val baseurl=getBaseUrl(it.src)
                    val headers = mapOf(
                        "Origin" to baseurl,
                        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                    )
                    val res = app.get(it.src, headers = headers).text

                    val regex = Regex("""props="(.*?)"""")
                    val match = regex.find(res)
                    val encodedJson = match?.groupValues?.get(1)

                    if (encodedJson != null) {
                        val unescapedJson = org.jsoup.parser.Parser.unescapeEntities(encodedJson, false)
                        val json = JSONObject(unescapedJson)

                        val videoUrl = "https:" + json.getJSONArray("manifest").getString(1)
                        callback.invoke(
                            newExtractorLink(
                                "CatStream",
                                "CatStream HLS",
                                videoUrl,
                                ExtractorLinkType.M3U8
                            )
                            {
                                this.referer = baseurl
                                this.quality = Qualities.P1080.value
                                this.headers = headers
                            }
                        )

                        val subtitleArray = json.getJSONArray("subtitles").getJSONArray(1)

                        for (i in 0 until subtitleArray.length()) {
                            val sub = subtitleArray.getJSONArray(i).getJSONObject(1)

                            val src = sub.getJSONArray("src").getString(1)
                            val name = sub.getJSONArray("name").getString(1)
                            subtitleCallback.invoke(
                                newSubtitleFile(
                                    name,  // Use label for the name
                                    src    // Use extracted URL
                                )
                            )
                        }

                    } else {
                        println("Could not find embedded JSON in props attribute")
                    }
                }
        }

        return true
    }

    private fun getSignature(
        html: String,
        server: String,
        query: String,
        key: ByteArray
    ): Triple<String, String, String>? {
        // Define the order based on the server type
        val headers= mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
        val order = when (server) {
            "VidStreaming", "DuckStream" -> listOf("IP", "USERAGENT", "ROUTE", "MID", "TIMESTAMP", "KEY")
            "BirdStream" -> listOf("IP", "USERAGENT", "ROUTE", "MID", "KEY")
            else -> return null
        }

        // Parse the HTML using Jsoup
        val document = Jsoup.parse(html)
        val cidRaw = document.select("script:containsData(cid:)").firstOrNull()
            ?.html()?.substringAfter("cid: '")?.substringBefore("'")?.decodeHex()
            ?: return null
        val cid = String(cidRaw).split("|")

        // Generate timestamp
        val timeStamp = (System.currentTimeMillis() / 1000 + 60).toString()

        // Update route
        val route = cid[1].replace("player.php", "source.php")

        val signature = buildString {
            order.forEach {
                when (it) {
                    "IP" -> append(cid[0])
                    "USERAGENT" -> append(headers["User-Agent"] ?: "")
                    "ROUTE" -> append(route)
                    "MID" -> append(query)
                    "TIMESTAMP" -> append(timeStamp)
                    "KEY" -> append(String(key))
                    "SIG" -> append(html.substringAfter("signature: '").substringBefore("'"))
                    else -> {}
                }
            }
        }
        // Compute SHA-1 hash of the signature
        return Triple(sha1sum(signature), timeStamp, route)
    }

    // Helper function to decode a hexadecimal string

    private fun sha1sum(value: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-1")
            val bytes = md.digest(value.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            throw Exception("Attempt to create the signature failed miserably.")
        }
    }

    //search

    data class Search(
        val result: List<SearchResult>,
        val maxPage: Long,
    )

    data class SearchResult(
        @JsonProperty("episode_count")
        val episodeCount: Long,
        val genres: List<String>,
        val locales: List<String>,
        val slug: String,
        val status: String,
        val synopsis: String,
        val title: String,
        @JsonProperty("title_en")
        val titleEn: String?,
        val type: String,
        val year: Long,
        val poster: SearchPoster,
        @JsonProperty("episode_duration")
        val episodeDuration: Long,
        @JsonProperty("watch_uri")
        val watchUri: String?,
        @JsonProperty("episode_number")
        val episodeNumber: Long?,
        @JsonProperty("episode_string")
        val episodeString: String?,
    )

    data class SearchPoster(
        val formats: List<String>,
        val sm: String,
        val aspectRatio: Double,
        val hq: String,
    )



    //Trending
data class ResponseHome(
    @JsonProperty("page_count")
    val pageCount: Long,
    val result: List<Result>,
)

data class Result(
    val title: String,
    @JsonProperty("title_en")
    val titleEn: String?,
    val synopsis: String,
    val status: String,
    val type: String,
    val slug: String,
    val rating: String?,
    val year: Long?,
    val poster: Poster,
    val genres: List<String>,
)

data class Poster(
    val formats: List<String>,
    val sm: String,
    val aspectRatio: Double,
    val hq: String,
)

//
//Episode

data class Episoderesponse(
    val slug: String,
    val title: String,
    val duration_ms: Long,
    val episode_number: Number,
    val episode_string: String,
    val thumbnail: Thumbnail
)

data class Thumbnail(
    val formats: List<String>,
    val sm: String,
    val aspectRatio: Double,
    val hq: String
)

//loadapi

data class loadres(
        val episodeDuration: Long?,
        val genres: List<String>?,
        val locales: List<String>?,
        val season: String?,
        val slug: String?,
        val startDate: String?,
        val status: String?,
        val synopsis: String?,
        val title: String?,
        val titleEn: String?,
        val titleOriginal: String?,
        val type: String?,
        val year: Long?,
        val poster: LoadPoster?,
        val banner: Banner?,
        val endDate: String?,
        val rating: String?,
        val watchUri: String?,
)


data class LoadPoster(
    val formats: List<String>,
    val sm: String,
    val aspectRatio: Double,
    val hq: String,
)

data class Banner(
    val formats: List<String>,
    val sm: String,
    val aspectRatio: Double,
    val hq: String,
)

//servers

data class ServersRes(
    val servers: List<Server>,

)
data class Server(
    val name: String,
    val shortName: String,
    val src: String,
)
//m3u8

data class m3u8(
        val hls: String,
        val subtitles: List<Subtitle>,
        val key: String,
)

data class Subtitle(
        val language: String,
        val name: String,
        val src: String,
)

private fun generateFilterWithCurrentYear(): String {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val jsonObject = """{"year":$currentYear,"status":"airing"}"""
    val base64Encoded = base64Encode(jsonObject.toByteArray()).trim()
    return URLEncoder.encode(base64Encoded, "UTF-8")
}

// Encrypted
data class Encrypted(
    val data: String,
)
    private val gson = Gson()
    private fun parseJsonToEpisodes(json: String): List<Episoderesponse> {
        data class Response(val result: List<Episoderesponse>)
        return try {
            gson.fromJson(json, Response::class.java).result
        } catch (_: Exception) {
            emptyList()
        }
    }
}
