package com.phisher98

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.lagradost.api.Log
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.extractors.helper.AesHelper.cryptoAESHandler
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.jsoup.select.Elements
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Collections
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration.Companion.milliseconds


val session = Session(Requests().baseClient)

val webMutex = Mutex()
private val streamPlayExtractorMapper by lazy { jacksonObjectMapper() }
private val normalizeAlphaNumSpaceRegex = Regex("[^a-z0-9 ]")
private val normalizeAlphaNumRegex = Regex("[^a-z0-9]")


object StreamPlayExtractor : StreamPlay() {

    private val cloudflareKiller by lazy { CloudflareKiller() }

    suspend fun invoke2embed(
        imdbId: String?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        if (imdbId.isNullOrBlank()) return

        val url = if (season == null) {
            "$twoEmbedAPI/embed/$imdbId"
        } else {
            "$twoEmbedAPI/embedtv/$imdbId&s=$season&e=$episode"
        }

        val headers = mapOf(
            "Content-Type" to "application/x-www-form-urlencoded",
            "Referer" to url,
        )

        val framesrc = app.post(
            url = url,
            data = mapOf("pls" to "pls"),
            headers = headers
        ).document.selectFirst("iframe#iframesrc")?.attr("data-src") ?: return

        val ref = getBaseUrl(framesrc)
        val id = framesrc.substringAfter("id=", "")
            .substringBefore("&")
            .takeIf { it.isNotBlank() } ?: return

        loadExtractor(
            "https://uqloads.xyz/e/$id",
            "$ref/",
            subtitleCallback,
            callback
        )
    }

    suspend fun invokeMultimovies(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val multimoviesApi = getDomains()?.multiMovies ?: return
        if (title.isNullOrBlank()) return

        val fixTitle = title.createSlug() ?: return

        val url = if (season == null) {
            "$multimoviesApi/movies/$fixTitle"
        } else {
            "$multimoviesApi/episodes/$fixTitle-${season}x${episode}"
        }

        val response = webMutex.withLock {
            safeGet(url, interceptor = cloudflareKiller)
        }

        if (response.code != 200) return

        val req = response.document
        if (req.text().contains("Just a moment", ignoreCase = true)) return

        val playerOptions = req.select("ul#playeroptionsul li").map {
            Triple(
                it.attr("data-post"),
                it.attr("data-nume"),
                it.attr("data-type")
            )
        }

        playerOptions.safeAmap { (postId, nume, type) ->
            if (nume.contains("trailer", ignoreCase = true)) return@safeAmap

            runCatching {
                val postResponse = app.post(
                    url = "$multimoviesApi/wp-admin/admin-ajax.php",
                    data = mapOf(
                        "action" to "doo_player_ajax",
                        "post" to postId,
                        "nume" to nume,
                        "type" to type
                    ),
                    referer = url,
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                )
                if (postResponse.code != 200) return@runCatching

                val responseData = tryParseJson<ResponseHash>(postResponse.text) ?: return@runCatching
                val embedUrl = responseData.embed_url

                val link = embedUrl
                    .trim()
                    .removeSurrounding("\"")
                    .takeIf { it.startsWith("http") }
                    ?: return@runCatching

                if (!link.contains("youtube", ignoreCase = true)) {
                    loadSourceNameExtractor(
                        "Multimovies",
                        link,
                        "$multimoviesApi/",
                        subtitleCallback,
                        callback
                    )
                }
            }
        }
    }

    suspend fun invokeZshow(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = title?.createSlug() ?: return
        val url = if (season == null) {
            "$zshowAPI/movie/$fixTitle-$year"
        } else {
            "$zshowAPI/episode/$fixTitle-season-$season-episode-$episode"
        }
        val response = safeGet(url)
        if (response.code != 200) return
        invokeWpmovies("ZShow", url, subtitleCallback, callback, encrypt = true)
    }

    private suspend fun invokeWpmovies(
        name: String? = null,
        url: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        fixIframe: Boolean = false,
        encrypt: Boolean = false,
        hasCloudflare: Boolean = false,
        interceptor: Interceptor? = null,
    ) {
        fun String.fixBloat(): String {
            return this.replace("\"", "").replace("\\", "")
        }

        val res = safeGet(
            url ?: return,
            interceptor = if (hasCloudflare) interceptor else null
        )
        val referer = getBaseUrl(res.url)
        val document = res.document
        document.select("ul#playeroptionsul > li").map {
            Triple(it.attr("data-post"), it.attr("data-nume"), it.attr("data-type"))
        }.safeAmap { (id, nume, type) ->
            val json = app.post(
                url = "$referer/wp-admin/admin-ajax.php",
                data = mapOf(
                    "action" to "doo_player_ajax",
                    "post" to id,
                    "nume" to nume,
                    "type" to type
                ),
                headers = mapOf(
                    "Accept" to "*/*",
                    "X-Requested-With" to "XMLHttpRequest"
                ),
                referer = url,
                interceptor = if (hasCloudflare) interceptor else null
            )
            val source = tryParseJson<ResponseHash>(json.text)?.let {
                when {
                    encrypt -> {
                        val meta =
                            tryParseJson<ZShowEmbed>(it.embed_url)?.meta ?: return@safeAmap
                        val key = generateWpKey(it.key ?: return@safeAmap, meta)
                        cryptoAESHandler(
                            it.embed_url,
                            key.toByteArray(),
                            false
                        )?.fixBloat() ?: return@safeAmap
                    }

                    fixIframe -> Jsoup.parse(it.embed_url).select("IFRAME").attr("SRC")
                    else -> it.embed_url
                }
            } ?: return@safeAmap
            when {
                !source.contains("youtube") -> {
                    loadDisplaySourceNameExtractor(
                        name,
                        name,
                        source,
                        "$referer/",
                        subtitleCallback,
                        callback
                    )
                }
            }
        }
    }

    suspend fun invokeTokyoInsider(
        jptitle: String? = null,
        title: String? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
        dubtype: String? = null
    ) {
        if (dubtype == null || (!dubtype.equals(
                "SUB",
                ignoreCase = true
            ) && !dubtype.equals("Movie", ignoreCase = true))
        ) return

        fun formatString(input: String?) = input?.replace(" ", "_").orEmpty()

        val jpFixTitle = formatString(jptitle)
        val fixTitle = formatString(title)
        val ep = episode ?: ""

        if (jpFixTitle.isBlank() && fixTitle.isBlank()) return

        var response =
            safeGet("https://www.tokyoinsider.com/anime/S/${jpFixTitle}_(TV)/episode/$ep")
        if (response.code != 200) return
        var doc = response.document

        if (doc.select("div.c_h2").text().contains("We don't have any files for this episode")) {
            response = safeGet("https://www.tokyoinsider.com/anime/S/${fixTitle}_(TV)/episode/$ep")
            if (response.code != 200) return
            doc = response.document
        }

        val href = doc.select("div.c_h2 > div:nth-child(1) > a").attr("href")
        if (href.isNotBlank()) {
            callback.invoke(
                newExtractorLink(
                    "TokyoInsider",
                    "TokyoInsider",
                    url = href,
                    INFER_TYPE
                ) {
                    this.referer = ""
                    this.quality = Qualities.P1080.value
                }
            )
        }
    }

    suspend fun invokeAnizone(
        title: String? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        dubtype: String?
    ) {

        if (dubtype == null || (!dubtype.equals(
                "SUB",
                ignoreCase = true
            ) && !dubtype.equals("Movie", ignoreCase = true))
        ) return

        val url = "https://anizone.to/anime?search=$title"

        val link = app.get(url).document.select("div.truncate > a").firstOrNull {
            it.text().contains(title.toString(), ignoreCase = true)
        }?.attr("href") ?: return

        val document = app.get("$link/$episode").document

        document.select("track").forEach {
            subtitleCallback.invoke(
                newSubtitleFile(
                    getLanguage(it.attr("label")),
                    it.attr("src")
                )
            )
        }

        val source = document.select("media-player").attr("src")
        callback.invoke(
            newExtractorLink(
                "Anizone",
                "⌜ Anizone ⌟ Multi Audio 🌐",
                source,
                type = ExtractorLinkType.M3U8,
            ) {
                this.quality = Qualities.P1080.value
            }
        )
    }


    suspend fun invokeKisskh(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        lastSeason: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val slug = title.createSlug() ?: return
        val type = if (season == null) "2" else "1"

        val searchResponse = safeGet(
            "$kissKhAPI/api/DramaList/Search?q=$title&type=$type",
            referer = "$kissKhAPI/"
        )
        if (searchResponse.code != 200) return

        val res = tryParseJson<ArrayList<KisskhResults>>(searchResponse.text) ?: return
        if (res.isEmpty()) return

        val (id, contentTitle) = if (res.size == 1) {
            res.first().id to res.first().title
        } else {
            val data = res.find {
                val slugTitle = it.title.createSlug() ?: return@find false
                when {
                    season == null -> slugTitle == slug
                    lastSeason == 1 -> slugTitle.contains(slug)
                    else -> slugTitle.contains(slug) && it.title?.contains(
                        "Season $season",
                        true
                    ) == true
                }
            } ?: res.find { it.title.equals(title) }
            data?.id to data?.title
        }

        if (id == null) return

        val detailResponse = safeGet(
            "$kissKhAPI/api/DramaList/Drama/$id?isq=false",
            referer = "$kissKhAPI/Drama/${getKisskhTitle(contentTitle)}?id=$id"
        )
        if (detailResponse.code != 200) return

        val resDetail = detailResponse.parsedSafe<KisskhDetail>() ?: return
        val epsId = if (season == null) {
            resDetail.episodes?.firstOrNull()?.id
        } else {
            resDetail.episodes?.find { it.number == episode }?.id
        } ?: return

        val (kkey, kkey1) = coroutineScope {
            val videoKeyDeferred = async {
                try {
                    safeGet("${BuildConfig.KissKh}$epsId&version=2.8.10", timeout = 10000)
                        .parsedSafe<KisskhKey>()?.key
                } catch (_: Exception) {
                    null
                }
            }

            val subtitleKeyDeferred = async {
                try {
                    safeGet("${BuildConfig.KisskhSub}$epsId&version=2.8.10", timeout = 10000)
                        .parsedSafe<KisskhKey>()?.key
                } catch (_: Exception) {
                    null
                }
            }

            videoKeyDeferred.await() to subtitleKeyDeferred.await()
        }

        if (kkey == null || kkey1 == null) return

        val (sourcesData, subResponse) = coroutineScope {
            val sourcesDeferred = async {
                try {
                    safeGet(
                        "$kissKhAPI/api/DramaList/Episode/$epsId.png?err=false&ts=&time=&kkey=$kkey",
                        referer = "$kissKhAPI/Drama/${getKisskhTitle(contentTitle)}/Episode-${episode ?: 0}?id=$id&ep=$epsId&page=0&pageSize=100"
                    ).parsedSafe<KisskhSources>()
                } catch (_: Exception) {
                    null
                }
            }

            val subDeferred = async {
                try {
                    tryParseJson<List<KisskhSubtitle>>(safeGet("$kissKhAPI/api/Sub/$epsId&kkey=$kkey1").text)
                } catch (_: Exception) {
                    null
                }
            }

            sourcesDeferred.await() to subDeferred.await()
        }

        sourcesData?.let { source ->
            listOf(source.video, source.thirdParty).forEach { link ->
                val safeLink = link ?: return@forEach
                when {
                    safeLink.contains(".m3u8") || safeLink.contains(".mp4") -> {
                        val safe = safeLink.takeIf { it.startsWith("http") } ?: return@forEach

                        callback.invoke(
                            newExtractorLink(
                                "Kisskh",
                                "Kisskh",
                                safe,
                                INFER_TYPE
                            ) {
                                referer = kissKhAPI
                                quality = Qualities.P720.value
                                headers = mapOf("Origin" to kissKhAPI)
                            }
                        )
                    }
                    else -> {
                        val cleanedLink = safeLink.substringBefore("?").takeIf { it.isNotBlank() }
                            ?: return@forEach
                        loadSourceNameExtractor(
                            "Kisskh",
                            fixUrl(cleanedLink, kissKhAPI),
                            "$kissKhAPI/",
                            subtitleCallback,
                            callback,
                            Qualities.P720.value
                        )
                    }
                }
            }
        }

        subResponse?.forEach { sub ->
            val lang = getLanguage(sub.label ?: "UnKnown")
            subtitleCallback.invoke(newSubtitleFile(lang, sub.src ?: return@forEach))
        }
    }

    // Shared data class to hold pre-fetched anime metadata
    data class AnimeResolvedIds(
        val malId: Int?,
        val anilistId: Int?,
        val anidbEid: Int,
        val zoroIds: List<String>? = null,
        val zoroTitle: String?,
        val aniXL: String?,
        val kaasSlug: String?,
        val animepaheUrl: String?,
        val animekaiId: String?,
        val tmdbYear: Int?,
    )

    suspend fun resolveAnimeIds(
        title: String?,
        date: String?,
        airedDate: String?,
        season: Int?,
        episode: Int?,
    ): AnimeResolvedIds {
        val (anilistId, malId) = convertTmdbToAnimeId(
            title, date, airedDate, if (season == null) TvType.AnimeMovie else TvType.Anime
        )

        var anijson: String? = null
        try {
            anijson = safeGet("https://api.ani.zip/mappings?mal_id=$malId").toString()
        } catch (e: Exception) {
            println("Error fetching mapping: ${e.message}")
        }

        val anidbEid = getAnidbEid(anijson ?: "{}", episode ?: 1) ?: 0

        val malsync = malId?.let {
            runCatching {
                safeGet("$malsyncAPI/mal/anime/$it").parsedSafe<MALSyncResponses>()?.sites
            }.getOrNull()
        }

        return AnimeResolvedIds(
            malId = malId,
            anilistId = anilistId,
            anidbEid = anidbEid,
            zoroIds = malsync?.zoro?.keys?.toList()?.filterNotNull(),
            zoroTitle = malsync?.zoro?.values?.firstNotNullOfOrNull { it["title"] }
                ?.replace(":", " "),
            aniXL = malsync?.AniXL?.values?.firstNotNullOfOrNull { it["url"] },
            kaasSlug = malsync?.KickAssAnime?.values?.firstNotNullOfOrNull { it["identifier"] },
            animepaheUrl = malsync?.animepahe?.values?.firstNotNullOfOrNull { it["url"] },
            animekaiId = malsync?.AnimeKAI?.values?.firstNotNullOfOrNull { it["identifier"] },
            tmdbYear = date?.substringBefore("-")?.toIntOrNull(),
        )
    }


    fun normalizeTitle(title: String?): String? {
        return title
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9 ]"), "")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
    }

    suspend fun invokeAnichi(
        name: String?,
        engtitle: String?,
        year: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        dubtype: String?,
    ) {

        val isMovie = dubtype == "Movie"
        val privatereferer = "https://allmanga.to"
        val ephash = "d405d0edd690624b66baba3068e0edc3ac90f1597d898a1ec8db4e5c43c00fec"
        val queryhash = "a24c500a1b765c68ae1d8dd85174931f661c71369c89b92b88b75a725afc471c"
        val type = if (episode == null) "Movie" else "TV"

        val normalizedName = normalizeTitle(name)
        val normalizedEngTitle = normalizeTitle(engtitle)

        val variables = if (isMovie) {
            """{"search":{"query":"$name"},"limit":26,"page":1,"translationType":"sub","countryOrigin":"ALL"}"""
        } else {
            """{"search":{"types":["$type"],"year":$year,"query":"$name"},"limit":26,"page":1,"translationType":"sub","countryOrigin":"ALL"}"""
        }

        val query =
            "${BuildConfig.ANICHI_API}?variables=$variables&extensions={\"persistedQuery\":{\"version\":1,\"sha256Hash\":\"$queryhash\"}}"
        val response = safeGet(query, referer = privatereferer)
            .parsedSafe<AnichiRoot>()
            ?.data?.shows?.edges ?: return

        val matched = response.find { item ->
            val itemName = normalizeTitle(item.name)
            val itemEnglishName = normalizeTitle(item.englishName)

            (normalizedName != null && (itemName == normalizedName || itemEnglishName == normalizedName)) ||
                    (normalizedEngTitle != null && (itemName == normalizedEngTitle || itemEnglishName == normalizedEngTitle))
        } ?: return

        val id = matched.id
        val langTypes = listOf("sub", "dub")
        val headers =
            mapOf(
                "Referer" to "https://allmanga.to"
            )

        langTypes.safeAmap { lang ->
            if (isMovie || (dubtype != null && lang.contains(dubtype, ignoreCase = true))) {
                val epQuery = """${BuildConfig.ANICHI_API}?variables={"showId":"$id","translationType":"$lang","episodeString":"${episode ?: 1}"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"$ephash"}}"""

                val responseText = safeGet(
                    epQuery,
                    referer = privatereferer,
                    headers = headers
                ).text

                val episodeLinks = run {
                    val encrypted = tryParseJson<EncryptedResponse>(responseText)
                        ?.data
                        ?.tobeparsed

                    val finalJson = encrypted
                        ?.let { decodeToBeParsed(it) }
                        ?: responseText

                    tryParseJson<AnichiEP>(finalJson)?.let {
                        it.data?.episode?.sourceUrls
                            ?: it.episode?.sourceUrls
                    }
                } ?: return@safeAmap

                episodeLinks.safeAmap { source ->
                    safeApiCall {
                        val sourceUrl = source.sourceUrl

                        if (sourceUrl.startsWith("http")) {
                            val host = sourceUrl.getHost()
                            loadDisplaySourceNameExtractor(
                                "Allanime",
                                "⌜ Allanime ⌟ | $host | [${lang.uppercase()}]",
                                sourceUrl,
                                "",
                                subtitleCallback,
                                callback
                            )
                            return@safeApiCall
                        }

                        if (URI(sourceUrl).isAbsolute || sourceUrl.startsWith("//")) {
                            val fixedLink =
                                if (sourceUrl.startsWith("//")) "https:$sourceUrl" else sourceUrl
                            val host = fixedLink.getHost()

                            loadDisplaySourceNameExtractor(
                                "Allanime",
                                "⌜ Allanime ⌟ | $host | [${lang.uppercase()}]",
                                fixedLink,
                                "",
                                subtitleCallback,
                                callback
                            )

                            return@safeApiCall
                        }

                        val decoded =
                            if (sourceUrl.startsWith("--")) decrypthex(sourceUrl) else sourceUrl
                        val fixedLink = decoded.fixUrlPath()
                        val links = try {
                            safeGet(fixedLink, headers = headers)
                                .parsedSafe<AnichiVideoApiResponse>()
                                ?.links ?: emptyList()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            return@safeApiCall
                        }

                        links.forEach { server ->
                            val host = server.link.getHost()
                            when {
                                source.sourceName.contains("Default") && server.resolutionStr in listOf(
                                    "SUB",
                                    "Alt vo_SUB"
                                ) -> {
                                    getM3u8Qualities(
                                        server.link,
                                        "https://static.crunchyroll.com/",
                                        host
                                    ).forEach(callback)
                                }


                                source.sourceName.contains("Uns") -> {
                                    loadDisplaySourceNameExtractor(
                                        "Allanime VidStack",
                                        "⌜ Allanime VidStack ⌟ | $host | [${lang.uppercase()}]",
                                        sourceUrl,
                                        "",
                                        subtitleCallback,
                                        callback
                                    )
                                }

                                server.hls == null -> {
                                    callback.invoke(
                                        newExtractorLink(
                                            "Allanime",
                                            "⌜ Allanime ⌟ | ${host.capitalize()} | [${lang.uppercase()}]",
                                            server.link,
                                            INFER_TYPE
                                        ) {
                                            this.quality = Qualities.P1080.value
                                        }
                                    )
                                }


                                server.hls -> {
                                    val endpoint = "https://allanime.day/player?uri=" +
                                            if (URI(server.link).host.isNotEmpty()) server.link
                                            else "https://allanime.day${URI(server.link).path}"
                                    getM3u8Qualities(
                                        server.link,
                                        server.headers?.referer ?: endpoint,
                                        host
                                    ).forEach(callback)
                                }

                                else -> {
                                    server.subtitles?.forEach { sub ->
                                        val langName =
                                            SubtitleHelper.fromTagToEnglishLanguageName(
                                                sub.lang ?: ""
                                            )
                                                ?: sub.lang.orEmpty()
                                        val src = sub.src ?: return@forEach
                                        subtitleCallback(newSubtitleFile(langName, httpsify(src)))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    suspend fun invokeAnineko(
        title: String?,
        jpTitle: String?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        dubtype: String?,
    ) {
        if (title == null || episode == null) return
        val isDub = dubtype == "DUB"
        
        var slug: String? = null
        val searchTitles = listOfNotNull(jpTitle, title).filter { it.isNotBlank() }
        for (searchTitle in searchTitles) {
            val searchUrl = "$anineko/browser?keyword=$searchTitle"
            val searchDoc = app.get(searchUrl).document
            val firstResult = searchDoc.selectFirst("a.nv-anime-thumb")
            val href = firstResult?.attr("href")
            if (href != null) {
                slug = href.substringAfterLast("/watch/").substringBefore("?")
                break
            }
        }
        if (slug == null) return

        val url = "$anineko/watch/$slug/ep-$episode"

        val doc = app.get(url).document

        val panels = doc.select(".nv-server-grid")
        val targetPanels = if (panels.isNotEmpty()) {
            panels.filter {
                val dataId = it.attr("data-id").lowercase()
                if (isDub) dataId.contains("dub") else !dataId.contains("dub")
            }
        } else {
            listOf(doc)
        }

        targetPanels.forEach { panel ->
            panel.select(".server-video").forEach { serverBtn ->
                val videoUrl = serverBtn.attr("data-video")
                val serverName = serverBtn.ownText().trim()
                val typeName = serverBtn.selectFirst("span")?.text()

                val subMatch = Regex("""(?:sub|caption_1|c1_file)=([^&]+)""").find(videoUrl)
                if (subMatch != null) {
                    val subUrl = subMatch.groupValues[1]
                    val subLang = Regex("""(?:sub_1|c1_label)=([^&]+)""").find(videoUrl)?.groupValues?.get(1) ?: "English"
                    subtitleCallback.invoke(newSubtitleFile(subLang, subUrl))
                }

                val finalUrl = if (videoUrl.startsWith("//")) "https:$videoUrl" else videoUrl
                val embedDoc = app.get(finalUrl, headers = mapOf("Referer" to "$anineko/")).text

                val hlsRegexes = listOf(
                    Regex("""const\s+src\s*=\s*["'](https?://[^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE),
                    Regex("""file\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE),
                    Regex("""["'](https?://[^"']+/master\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE),
                    Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE)
                )

                var m3u8Url: String? = null
                for (regex in hlsRegexes) {
                    val match = regex.find(embedDoc)
                    if (match != null) {
                        m3u8Url = match.groupValues[1]
                        break
                    }
                }

                if (m3u8Url != null) {
                    val sourceName = if (typeName != null) "$serverName - $typeName" else serverName
                    generateM3u8(
                        sourceName,
                        m3u8Url,
                        finalUrl
                    ).forEach { link ->
                        val newLink = newExtractorLink(
                            source = "AniNeko",
                            name = "⌜ AniNeko ⌟ " + link.name,
                            url = link.url,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.quality = link.quality
                            this.headers = link.headers
                            this.extractorData = link.extractorData
                        }
                        callback.invoke(newLink)
                    }
                } else if (serverName.contains("HD-")) {
                    val host = Regex("""https?://([^/]+)""").find(finalUrl)?.groupValues?.get(1) ?: ""
                    val extractor = object : com.lagradost.cloudstream3.extractors.StreamWishExtractor() {
                        override var mainUrl = "https://$host"
                        override var name = serverName
                    }
                    val links = mutableListOf<ExtractorLink>()
                    extractor.getUrl(finalUrl, "https://$host/", subtitleCallback) { link ->
                        links.add(link)
                    }
                    links.forEach { link ->
                        val newLink = newExtractorLink(
                            source = "AniNeko",
                            name = "⌜ AniNeko ⌟ " + link.name + if (typeName != null) " - $typeName" else "",
                            url = link.url,
                            type = if (link.isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.quality = link.quality
                            this.headers = link.headers
                            this.extractorData = link.extractorData
                        }
                        callback.invoke(newLink)
                    }
                } else {
                    val links = mutableListOf<ExtractorLink>()
                    loadExtractor(finalUrl, "$anineko/", subtitleCallback) { link ->
                        links.add(link)
                    }
                    links.forEach { link ->
                        val newLink = newExtractorLink(
                            source = "AniNeko",
                            name = "⌜ AniNeko ⌟ " + link.name + if (typeName != null) " - $typeName" else "",
                            url = link.url,
                            type = if (link.isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.quality = link.quality
                            this.headers = link.headers
                            this.extractorData = link.extractorData
                        }
                        callback.invoke(newLink)
                    }
                }
            }
        }
    }

    suspend fun invokeAnimepahe(
        url: String,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        dubtype: String?,
    ) {
        val isMovie = dubtype == "Movie"
        val headers = mapOf("Cookie" to "__ddg2_=1234567890")

        val id = safeGet(url.replace(".si",".com"), headers)
            .document.selectFirst("meta[property=og:url]")
            ?.attr("content").toString().substringAfterLast("/")

        val animeData = safeGet(
            "$animepaheAPI/api?m=release&id=$id&sort=episode_asc&page=1",
            headers
        ).parsedSafe<animepahe>()?.data.orEmpty()

        val targetIndex = (episode ?: 1) - 1
        if (targetIndex !in animeData.indices) return
        val session = animeData[targetIndex].session

        val document = safeGet(
            "$animepaheAPI/play/$id/$session",
            headers
        ).document

        document.select("#resolutionMenu button").safeAmap {
            val dubText = it.select("span").text().lowercase()
            val type = if ("eng" in dubText) "DUB" else "SUB"

            val qualityRegex = Regex("""(.+?)\s+·\s+(\d{3,4}p)""")
            val text = it.text()
            val match = qualityRegex.find(text)

            val source = match?.groupValues?.getOrNull(1)?.trim() ?: "Unknown"
            val quality = match?.groupValues?.getOrNull(2)?.substringBefore("p")?.toIntOrNull()
                ?: Qualities.Unknown.value

            val href = it.attr("data-src")
            if ("kwik" in href && (isMovie || (dubtype != null && type.contains(
                    dubtype,
                    ignoreCase = true
                )))
            ) {
                loadDisplaySourceNameExtractor(
                    "Animepahe",
                    "⌜ Animepahe ⌟ $source | [$type]",
                    href,
                    "",
                    subtitleCallback,
                    callback,
                    quality
                )
            }
        }

        document.select("div#pickDownload > a").safeAmap {
            val qualityRegex = Regex("""(.+?)\s+·\s+(\d{3,4}p)""")

            val href = it.attr("href")
            var type = "SUB"
            if (it.select("span").text().contains("eng", ignoreCase = true))
                type = "DUB"

            val text = it.text()
            val match = qualityRegex.find(text)
            val source = match?.groupValues?.getOrNull(1) ?: "Unknown"
            val quality = match?.groupValues?.getOrNull(2)?.substringBefore("p") ?: "Unknown"
            if (isMovie || (dubtype != null && type.contains(dubtype, ignoreCase = true))) {
                loadDisplaySourceNameExtractor(
                    "Animepahe Pahe",
                    "⌜ Animepahe ⌟ Pahe $source | [$type]",
                    href,
                    "",
                    subtitleCallback,
                    callback,
                    quality.toIntOrNull()
                )
            }
        }
    }

    //AnimetoSho
    private data class ServerLink(
        val url: String,
        val size: String,
        val qualityIndex: Int,
        val group: String,
        val meta: ReleaseMeta
    )

    data class ReleaseMeta(
        val group: String,
        val resolution: Int?,
        val codec: String?,
        val platform: String?,
        val source: String?,
        val audio: String?,
        val subtitles: String?,
        val bitDepth: Int? = null,
        val audioCodec: String? = null
    )

    fun parseReleaseMeta(title: String): ReleaseMeta =
        ReleaseMeta(
            group = GROUP_REGEX.find(title)?.groupValues?.get(1).orEmpty(),
            resolution = RES_REGEX.find(title)?.groupValues?.get(1)?.toIntOrNull(),
            codec = CODEC_REGEX.find(title)?.value?.uppercase(),
            platform = PLATFORM_REGEX.find(title)?.value?.uppercase(),
            source = SOURCE_REGEX.find(title)?.value?.replace(" ", "-")?.uppercase(),
            audio = AUDIO_REGEX.find(title)?.value,
            subtitles = SUB_REGEX.find(title)?.value,
            bitDepth = BIT_DEPTH_REGEX.find(title)
                ?.value
                ?.filter { it.isDigit() }
                ?.toIntOrNull(),
            audioCodec = AUDIO_CODEC_REGEX.find(title)?.value?.uppercase()
        )


    private val GROUP_REGEX = Regex("""^\[(.*?)]""")

    private val HOST_REGEX =
        Regex("""KrakenFiles|GoFile|AkiraBox|BuzzHeavier""", RegexOption.IGNORE_CASE)

    private val RES_REGEX = Regex("""(4320|2160|1440|1080|720|480)p""", RegexOption.IGNORE_CASE)

    private val CODEC_REGEX =
        Regex("""AV1|HEVC|H\.?265|x265|H\.?264|x264""", RegexOption.IGNORE_CASE)

    private val PLATFORM_REGEX = Regex("""AMZN|NF|CR|BILI|IQIYI""", RegexOption.IGNORE_CASE)

    private val SOURCE_REGEX = Regex("""WEB[- .]?DL|WEB[- .]?Rip""", RegexOption.IGNORE_CASE)

    private val AUDIO_REGEX =
        Regex("""Dual[- ]?Audio|Eng(lish)?[- ]?Dub|Japanese""", RegexOption.IGNORE_CASE)

    private val AUDIO_CODEC_REGEX =
        Regex("""AAC(\d\.\d)?|DDP(\d\.\d)?|OPUS|FLAC""", RegexOption.IGNORE_CASE)

    private val BIT_DEPTH_REGEX = Regex("""10[- ]?bit|10bits|8[- ]?bit""", RegexOption.IGNORE_CASE)

    private val SUB_REGEX = Regex(
        """Multi[- ]?Subs?|MultiSub|Multiple Subtitles|Eng(lish)?[- ]?Sub|ESub|Subbed""",
        RegexOption.IGNORE_CASE
    )


    private fun Elements.getLinks(): List<ServerLink> =
        flatMap { ele ->
            val title = ele.select("div.link a").text()
            val meta = parseReleaseMeta(title)

            val size = ele.select("div.size").text()
            val quality = meta.resolution ?: Qualities.Unknown.value

            ele.select("div.links a:matches(${HOST_REGEX.pattern})").map { a ->
                ServerLink(
                    url = a.attr("href"),
                    size = size,
                    qualityIndex = quality,
                    group = meta.group,
                    meta = meta
                )
            }
        }

    suspend fun invokeAnimetosho(
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        dubtype: String?,
        anidbEid: Int?
    ) {
        if (dubtype !in setOf("SUB", "Movie")) return

        val url = "$animetoshoAPI/episode/$anidbEid"

        val res = safeGet(url).document

        res.select("div.home_list_entry:has(div.links)")
            .getLinks()
            .asSequence()
            .filter {
                it.qualityIndex == Qualities.P1080.value ||
                        it.qualityIndex == Qualities.P720.value
            }
            .forEach { it ->
                val displayName = buildString {
                    append("⌜ Animetosho ⌟")

                    if (it.meta.group.isNotBlank())
                        append(" ${it.meta.group}")

                    val sourceBlock = listOfNotNull(
                        it.meta.platform,
                        it.meta.source
                    ).joinToString(" ")

                    listOfNotNull(
                        sourceBlock.takeIf { it.isNotBlank() },
                        it.meta.audio,
                        it.meta.subtitles,
                        it.meta.codec,
                        it.size.takeIf { it.isNotBlank() }
                    ).joinToString(" | ").let {
                        if (it.isNotBlank()) append(" | $it")
                    }
                }

                loadDisplaySourceNameExtractor(
                    "Animetosho ${it.meta.group}",
                    displayName,
                    it.url,
                    "$animetoshoAPI/",
                    subtitleCallback,
                    callback,
                    "${it.meta.resolution}".toIntOrNull()
                )
            }
    }


    suspend fun invokeReAnime(
        anilistId: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        dubtype: String?,
    ) {
        val type = when (dubtype) {
            "DUB" -> "dub"
            else -> "sub"
        }

        val res = app.get("$reanime/api/flix/$anilistId/$episode")
            .parsedSafe<ReAnime>()
            ?.servers
            ?.filter { it.dataType.equals(type, true) }
            ?: return

        res.forEach {
            loadDisplaySourceNameExtractor(
                "ReAnime ${it.serverName}",
                "ReAnime ${it.serverName} [${it.dataType.capitalize()}]",
                it.dataLink,
                "$reanime/",
                subtitleCallback,
                callback
            )
        }
    }


    suspend fun invokeAnimex(
        malId: Int? = null,
        anilistId: Int? = null,
        title: String? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        dubtype: String?
    ) {
        val isMovie = dubtype == "Movie"
        val json = "application/json; charset=utf-8".toMediaType()
        val epNum = if (isMovie) 1 else (episode ?: return)
        val searchTitle = title ?: return

        val headers = mapOf(
            "Origin" to base64Decode("aHR0cHM6Ly9hbmltZXgub25l"),
            "Referer" to base64Decode("aHR0cHM6Ly9hbmltZXgub25lLw=="),
            "User-Agent" to USER_AGENT,
            "Content-Type" to "application/json"
        )

        val body = """
        {
          "query":"query FastSearch(${'$'}query: String, ${'$'}limit: Int) { catalogAnime(filter: { query: ${'$'}query }, limit: ${'$'}limit) { items { id anilistId malId titleRomaji titleEnglish format } } }",
          "variables":{
            "query":"$searchTitle",
            "limit":10
          }
        }
    """.trimIndent()

        val response = app.post(
            base64Decode("aHR0cHM6Ly9ncmFwaHFsLmFuaW1leC5vbmUvZ3JhcGhxbA=="),
            requestBody = body.toRequestBody(json),
            headers = headers
        ).parsedSafe<SearchResponse>() ?: return

        val anime = response.data.catalogAnime.items.firstOrNull {
            when {
                anilistId != null -> it.anilistId == anilistId
                malId != null -> it.malId == malId
                else -> false
            }
        } ?: response.data.catalogAnime.items.firstOrNull {
            it.titleEnglish.equals(searchTitle, true) ||
                    it.titleRomaji.equals(searchTitle, true)
        } ?: response.data.catalogAnime.items.firstOrNull() ?: return

        val servers = app.get(
            base64Decode("aHR0cHM6Ly9wcC5hbmltZXgub25lL3Jlc3QvYXBpL3NlcnZlcnM="),
            params = mapOf(
                "id" to anime.id,
                "epNum" to epNum.toString()
            ),
            headers = headers
        ).parsedSafe<ServerResponse>() ?: return

        val requestTypes = when {
            isMovie -> listOf("sub", "dub")
            dubtype.equals("dub", true) -> listOf("dub")
            else -> listOf("sub")
        }

        val providers = buildList {

            if ("sub" in requestTypes) {
                servers.subProviders.forEach {
                    add("sub" to it.id)
                }
            }

            if ("dub" in requestTypes) {
                servers.dubProviders.forEach {
                    add("dub" to it.id)
                }
            }
        }.sortedBy { (type, id) ->

            val provider = when (type) {
                "sub" -> servers.subProviders.find { it.id == id }
                else -> servers.dubProviders.find { it.id == id }
            }

            !(provider?.default ?: false)
        }

        for ((type, providerId) in providers) {

            delay((400L..900L).random().milliseconds)

            runCatching {

                val sourceResponse = app.get(
                    base64Decode("aHR0cHM6Ly9wcC5hbmltZXgub25lL3Jlc3QvYXBpL3NvdXJjZXM="),
                    params = mapOf(
                        "id" to anime.id,
                        "epNum" to epNum.toString(),
                        "type" to type,
                        "providerId" to providerId
                    ),
                    headers = headers
                ).parsedSafe<SourceResponse>() ?: return@runCatching

                val referer = sourceResponse.headers?.get("Referer")
                    ?: "https://animex.one/"


                sourceResponse.sources?.forEach { source ->
                    val providerInfo = when (type) {
                        "sub" -> servers.subProviders.find { it.id == providerId }
                        else -> servers.dubProviders.find { it.id == providerId }
                    }

                    val subType = providerInfo?.tip
                        ?.substringBefore(",")
                        ?.trim()
                        ?: "Unknown"

                    callback.invoke(
                        newExtractorLink(
                            "Animex",
                            "Animex [$subType] [${type.replaceFirstChar(Char::uppercase)}] [${providerId.replaceFirstChar(Char::uppercase)}]",
                            source.url
                        ) {
                            this.referer = referer
                            quality = getQualityFromName(source.quality)
                                .takeIf { it != Qualities.Unknown.value }
                                ?: Qualities.P1080.value                        }
                    )
                }

                sourceResponse.tracks?.forEach { track ->
                    track.file?.let {
                        subtitleCallback.invoke(
                            newSubtitleFile(
                                track.label ?: "Unknown",
                                it
                            )
                        )
                    }
                }

            }.onFailure {
                println("Animex failed [$type][$providerId] : ${it.message}")
            }
        }
    }

    data class SearchResponse(
        val data: SearchData
    )

    data class SearchData(
        val catalogAnime: CatalogAnime
    )

    data class CatalogAnime(
        val items: List<AnimeItem>
    )

    data class AnimeItem(
        val id: String,
        val anilistId: Int?,
        val malId: Int?,
        val titleRomaji: String?,
        val titleEnglish: String?,
        val format: String?
    )

    data class ServerResponse(
        val subProviders: List<Provider> = emptyList(),
        val dubProviders: List<Provider> = emptyList()
    )

    data class Provider(
        val id: String,
        val default: Boolean? = false,
        val tip: String? = null
    )

    data class SourceResponse(
        val sources: List<VideoSource>? = null,
        val tracks: List<Track>? = null,
        val headers: Map<String, String>? = null
    )

    data class VideoSource(
        val url: String,
        val quality: String? =null
    )

    data class Track(
        val file: String? = null,
        val label: String? = null
    )

    //Broken for now
    suspend fun invokeHianime(
        malId: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        dubtype: String?,
    ) {
        if (malId == null || episode == null) return

        val name = "HiAnime"
        val megaBase = "https://megaplay.buzz"
        val vidwishBase = "https://vidwish.live"
        val megacloudbloggy = "https://megacloud.bloggy.click"
        val type = if (dubtype.equals("sub", true)) "sub" else "dub"

        val megaUrl = "$megaBase/stream/mal/$malId/$episode/$type"

        val doc = try {
            app.get(megaUrl, referer = megaUrl).document
        } catch (_: Exception) {
            return
        }

        val player = doc.selectFirst("div.fix-area#megaplay-player") ?: return
        val dataId = player.attr("data-id").takeIf(String::isNotBlank)
        val realId = player.attr("data-realid").takeIf(String::isNotBlank)

        suspend fun process(
            displayName: String,
            apiUrl: String,
            referer: String,
            origin: String
        ) {

            val mainHeaders = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0",
                "Accept" to "*/*",
                "Origin" to origin,
                "Referer" to origin,
                "Connection" to "keep-alive",
                "Pragma" to "no-cache",
                "Cache-Control" to "no-cache"
            )

            try {
                val json = app.get(
                    apiUrl,
                    referer = referer,
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                ).parsedSafe<HiAnimeSourcesResponse>() ?: return

                val file = json.sources?.file
                if (!file.isNullOrBlank()) {
                    generateM3u8(
                        displayName,
                        file,
                        origin,
                        headers = mainHeaders
                    ).forEach(callback)
                }

                val tracks = json.tracks
                if (!tracks.isNullOrEmpty()) {
                    tracks.forEach {
                        val sub = it.file ?: return@forEach
                        subtitleCallback(
                            newSubtitleFile(it.label ?: "Unknown", sub)
                            {
                                this.headers = mapOf(
                                    "Referer" to "$origin/"
                                )
                            }
                        )
                    }
                }

            } catch (_: Exception) {
                // isolated failure
            }
        }

        // -------- MegaPlay --------
        dataId?.let {
            process(
                displayName = "[$name] MegaPlay",
                apiUrl = "$megaBase/stream/getSources?id=$it&id=$it",
                referer = megaUrl,
                origin = "$megaBase/"
            )
        }

        // -------- Vidwish --------
        realId?.let { rid ->
            val vidPage = "$vidwishBase/stream/s-2/$rid/$type"

            try {
                val vidDoc = app.get(vidPage, referer = megaUrl).document

                val vidPlayer = vidDoc.selectFirst("div.fix-area#megaplay-player")
                val vidDataId = vidPlayer?.attr("data-id")?.takeIf(String::isNotBlank)

                vidDataId?.let { vidId ->
                    process(
                        displayName = "[$name] Vidwish",
                        apiUrl = "$vidwishBase/stream/getSources?id=$vidId&id=$vidId",
                        referer = vidPage,
                        origin = "$vidwishBase/"
                    )
                }

            } catch (_: Exception) {
                // ignore
            }
        }

        // -------- Megacloudbloggy --------
        realId?.let { rid ->
            val megacloudPage = "$megacloudbloggy/stream/s-3/$rid/$type"

            try {
                val megacloudDoc = app.get(megacloudPage, referer = megaUrl).document

                val megacloudPlayer = megacloudDoc.selectFirst("div.fix-area#megaplay-player")
                val megacloudDataId = megacloudPlayer?.attr("data-id")?.takeIf(String::isNotBlank)

                megacloudDataId?.let { vidId ->
                    process(
                        displayName = "[$name] MegaCloud",
                        apiUrl = "$megacloudbloggy/stream/getSources?id=$vidId&id=$vidId",
                        referer = megacloudbloggy,
                        origin = "$megacloudbloggy/"
                    )
                }

            } catch (_: Exception) {
                // ignore
            }
        }
    }

    suspend fun invokeKickAssAnime(
        engtitle: String?,
        slugid: String?,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        dubtype: String?,
    ) {
        val isMovie = dubtype == "Movie"
        var slug = slugid
        if (slug.isNullOrBlank()) {
            if (engtitle.isNullOrBlank()) return

            val host = "https://kickass-anime.ro/"

            val json = """
                {
                    "page": 1,
                    "query": "$engtitle"
                }
            """.trimIndent()

            val requestBody = json.toRequestBody("application/json".toMediaType())

            val headers = mapOf(
                "Accept" to "*/*",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
                "Content-Type" to "application/json",
                "x-origin" to "kickass-anime.ru"
            )

            val searchJson = app
                .post("${host}api/fsearch", requestBody = requestBody, headers = headers)
                .toString()

            val resultArray = JSONObject(searchJson).optJSONArray("result") ?: return

            for (i in 0 until resultArray.length()) {
                val item = resultArray.getJSONObject(i)
                if (item.optString("title_en", "")
                        .equals(engtitle, ignoreCase = true)
                ) {
                    slug = item.optString("slug")
                    break
                }
            }
        }

        val locales: List<String> = when {
            isMovie -> listOf("en-US", "ja-JP") // BOTH
            dubtype.equals("DUB", ignoreCase = true) -> listOf("en-US")
            dubtype.equals("SUB", ignoreCase = true) -> listOf("ja-JP")
            else -> return // null or invalid → block
        }

        locales.safeAmap { locale ->
            val json = safeGet(
                "$KickassAPI/api/show/$slug/episodes?ep=1&lang=$locale",
                timeout = 5000L
            ).toString()

            val jsonresponse = parseJsonToEpisodes(json)

            val matchedSlug = jsonresponse.firstOrNull {
                it.episode_number.toString()
                    .substringBefore(".")
                    .toIntOrNull() == episode
            }?.slug ?: return@safeAmap

            val href = "$KickassAPI/api/show/$slug/episode/ep-$episode-$matchedSlug"
            val servers = safeGet(href).parsedSafe<ServersResKAA>()?.servers ?: return@safeAmap

            servers.safeAmap { server ->
                if (server.name.contains("VidStreaming")) {
                    val host = getBaseUrl(server.src)
                    val headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                    )

                    val key = "e13d38099bf562e8b9851a652d2043d3".toByteArray()
                    val query = server.src.substringAfter("?id=").substringBefore("&")
                    val html = safeGet(server.src).toString()

                    //If HTML have m3u8
                    if (html.contains(".m3u8", ignoreCase = true)) {
                        val match =
                            Regex("""(https?:)?//[^\s"'<>]+\.m3u8""", RegexOption.IGNORE_CASE)
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

                    val (sig, timeStamp, route) = getSignature(html, server.name, query, key)
                        ?: return@safeAmap
                    val sourceUrl = "$host$route?id=$query&e=$timeStamp&s=$sig"

                    val encJson =
                        safeGet(sourceUrl, headers = headers).parsedSafe<EncryptedKAA>()?.data
                            ?: return@safeAmap

                    val (encryptedData, ivHex) = encJson
                        .substringAfter(":\"")
                        .substringBefore('"')
                        .split(":")
                    val decrypted = tryParseJson<m3u8KAA>(
                        CryptoAES.decrypt(encryptedData, key, ivHex.decodeHex()).toJson()
                    ) ?: return@safeAmap

                    val m3u8 = httpsify(decrypted.hls)
                    val videoHeaders = mapOf(
                        "Accept" to "*/*",
                        "Accept-Language" to "en-US,en;q=0.5",
                        "Origin" to host,
                        "Sec-Fetch-Dest" to "empty",
                        "Sec-Fetch-Mode" to "cors",
                        "Sec-Fetch-Site" to "cross-site"
                    )
                    val baseurl = getBaseUrl(server.src)

                    callback.invoke(
                        newExtractorLink(
                            server.name,
                            server.name,
                            m3u8,
                            ExtractorLinkType.M3U8
                        )
                        {
                            this.quality = Qualities.P1080.value
                            this.headers = videoHeaders
                        }
                    )

                    decrypted.subtitles.forEach { subtitle ->
                        subtitleCallback(
                            newSubtitleFile(
                                subtitle.name,
                                httpsify(subtitle.src)
                            )
                            {
                                this.headers = mapOf("Referer" to baseurl)
                            }
                        )
                    }
                } else if (server.name.contains("CatStream")) {
                    val baseurl = getBaseUrl(server.src)
                    val headers = mapOf(
                        "Origin" to baseurl,
                        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                    )

                    val res = safeGet(
                        server.src,
                        headers = headers
                    ).text

                    val regex = Regex("""props="(.*?)"""")
                    val match = regex.find(res)
                    val encodedJson = match?.groupValues?.get(1)

                    if (encodedJson != null) {
                        val unescapedJson = Parser.unescapeEntities(encodedJson, false)
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
                                    httpsify(src)    // Use extracted URL
                                )
                                {
                                    this.headers = mapOf("Referer" to baseurl)
                                }
                            )
                        }

                    } else {
                        println("Could not find embedded JSON in props attribute")
                    }
                }
            }
        }
    }


    suspend fun invokeUhdmovies(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        val domain = getDomains()?.uhdmovies ?: return

        val query = title
            ?.replace("-", " ")
            ?.replace(":", " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return

        val searchUrl = "$domain/search/${
            withContext(Dispatchers.IO) {
                URLEncoder.encode("$query $year", "UTF-8")
            }
        }"

        val redirectRegex = Regex("""window\.location\.replace\(["'](.*?)["']\)""")

        val pageUrl = safeGet(searchUrl).document
            .selectFirst("article div.entry-image a")
            ?.attr("href")
            ?: return

        val doc = safeGet(pageUrl).document

        val selector = if (season == null) {
            "div.entry-content p:matches($year)"
        } else {
            "div.entry-content p:matches((?i)(S0?$season|Season 0?$season))"
        }

        val epSelector = if (season == null) {
            "a:matches((?i)Download)"
        } else {
            "a:matches((?i)Episode $episode)"
        }

        val links = doc.select(selector)
            .asSequence()
            .mapNotNull {
                it.nextElementSibling()
                    ?.select(epSelector)
                    ?.attr("href")
            }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

        links.safeAmap { link ->
            val driveLink = try {
                if (link.contains("driveleech", true) || link.contains("driveseed", true)) {
                    val text = safeGet(link).text
                    val fileId = redirectRegex.find(text)?.groupValues?.getOrNull(1)
                        ?: return@safeAmap
                    getBaseUrl(link) + fileId
                } else {
                    bypassHrefli(link) ?: return@safeAmap
                }
            } catch (_: Exception) {
                return@safeAmap
            }

            loadSourceNameExtractor(
                "UHDMovies",
                driveLink,
                "",
                subtitleCallback,
                callback
            )
        }
    }

    suspend fun invokeSubtitleAPI(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        val url = if (season == null) {
            "$SubtitlesAPI/subtitles/movie/$id.json"
        } else {
            "$SubtitlesAPI/subtitles/series/$id:$season:$episode.json"
        }
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        )
        val response = safeGet(url, headers = headers, timeout = 10000L)
        if (response.code != 200) return
        response.parsedSafe<SubtitlesAPI>()?.subtitles?.safeAmap { it ->
            val lan = getLanguage(it.lang)
            val suburl = it.url
            subtitleCallback.invoke(
                newSubtitleFile(
                    lan.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                    suburl
                )
            )
        }
    }


    suspend fun invokeWYZIESubs(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        val key = wyziekey.takeIf { !it.isNullOrBlank() } ?: return

        val url = if (season != null) {
            "$WYZIESubsAPI/search?id=$id&season=$season&episode=$episode&source=all&key=$key"
        } else {
            "$WYZIESubsAPI/search?id=$id&source=all&key=$key"
        }

        val data = app.get(
            url,
            timeout = 10000
        ).parsedSafe<Array<WYZIESubtitle>>() ?: return

        data.forEach { sub ->
            val lang = sub.language ?: return@forEach

            subtitleCallback.invoke(
                newSubtitleFile(
                    sub.display ?: getLanguage(lang),
                    sub.url
                )
            )
        }
    }


    suspend fun invokeVideasy(
        title: String? = null,
        tmdbId: Int? = null,
        imdbId: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        fun quote(text: String): String {
            return URLEncoder.encode(text)
                .replace("+", "%20")
        }

        val headers = mapOf(
            "Accept" to "*/*",
            "User-Agent" to USER_AGENT,
            "Origin" to "https://www.cineby.sc",
            "Referer" to "https://www.cineby.sc/"
        )

        val servers = listOf(
            "myflixerzupcloud",
            "1movies",
            "moviebox",
            "primewire",
            "m4uhd",
            "hdmovie",
            "cdn",
            "primesrcme",
            "visioncine",
            "overflix",
            "superflix",
            "cuevana",
            "lamovie",
            "mb-flix",
        )

        if(title == null) return

        val firstPass = quote(title)
        val encTitle = quote(firstPass)

        servers.safeAmap { server ->
            val url = if (season == null) {
                "$videasyAPI/$server/sources-with-title?title=$encTitle&mediaType=movie&year=$year&tmdbId=$tmdbId&imdbId=$imdbId"
            } else {
                "$videasyAPI/$server/sources-with-title?title=$encTitle&mediaType=tv&year=$year&tmdbId=$tmdbId&episodeId=$episode&seasonId=$season&imdbId=$imdbId"
            }

            val encdata = safeGet(url, headers = headers).text

            val jsonBody = mapOf("text" to encdata, "id" to tmdbId)
            val response = app.post(
                "https://enc-dec.app/api/dec-videasy",
                json = jsonBody
            )

            if(response.isSuccessful) {
                val json = response.text
                val result = JSONObject(json).getJSONObject("result")

                val sourcesArray = result.getJSONArray("sources")
                for (i in 0 until sourcesArray.length()) {
                    val obj = sourcesArray.getJSONObject(i)
                    val quality = obj.getString("quality")
                    val source = obj.getString("url")

                    val type = if(source.contains(".m3u8")) {
                        ExtractorLinkType.M3U8
                    } else if(source.contains(".mp4") || source.contains(".mkv")) {
                        ExtractorLinkType.VIDEO
                    } else {
                        INFER_TYPE
                    }

                    callback.invoke(
                        newExtractorLink(
                            "Videasy[${server.uppercase()}]",
                            "Videasy[${server.uppercase()}]",
                            source,
                            type
                        ) {
                            this.quality = getIndexQuality(quality)
                            this.headers = headers
                        }
                    )
                }

                val subtitlesArray = result.getJSONArray("subtitles")
                for (i in 0 until subtitlesArray.length()) {
                    val obj = subtitlesArray.getJSONObject(i)
                    val source = obj.getString("url")
                    val language = obj.getString("language")

                    subtitleCallback.invoke(
                        newSubtitleFile(
                            getLanguage(language),
                            source
                        )
                    )
                }
            }
        }
    }

    suspend fun invokeMapple(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        if (tmdbId == null) return

        val base = mappleAPI.removeSuffix("/")

        val mediaType = if (season == null) "movie" else "tv"
        val tvSlug = if (season != null && episode != null) "$season-$episode" else ""

        val headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to "$base/",
            "Origin" to base,
            "Accept" to "*/*",
            "Content-Type" to "application/json"
        )

        val watchUrl = if (mediaType == "movie") {
            "$base/watch/movie/$tmdbId"
        } else {
            "$base/watch/tv/$tmdbId/$tvSlug"
        }

        val page = safeGet(watchUrl, headers = headers).text
        val tokenRegex = Regex("""window\.__REQUEST_TOKEN__\s*=\s*"([^"]+)"""")
        val requestToken = tokenRegex.find(page)?.groupValues?.get(1) ?: return

        val body = """
        {
            "mediaId": $tmdbId,
            "mediaType": "$mediaType",
            "requestToken": "$requestToken"
        }
    """.trimIndent()

        val tokenRes1 = JSONObject(
            app.post("$base/api/stream-token", requestBody = body.toRequestBody(), headers = headers).text
        )

        if (!tokenRes1.optBoolean("success")) return

        val finalToken = if (tokenRes1.optBoolean("requiresPow")) {
            val pow = tokenRes1.getJSONObject("pow")

            val nonce = solvePowChallenge(
                pow.getString("challenge"),
                pow.getInt("difficulty")
            ) ?: return

            val body2 = """
            {
                "mediaId": $tmdbId,
                "mediaType": "$mediaType",
                "requestToken": "$requestToken",
                "pow": {
                    "challengeId": "${pow.getString("challengeId")}",
                    "nonce": "$nonce"
                }
            }
        """.trimIndent()

            val tokenRes2 = JSONObject(
                app.post("$base/api/stream-token", requestBody = body2.toRequestBody(), headers = headers).text
            )

            if (!tokenRes2.optBoolean("success")) return
            tokenRes2.getString("token")
        } else {
            tokenRes1.getString("token")
        }

        val sources = listOf(
            "mapple",
            "willow",
            "cherry",
            "pines",
            "oak",
            "sequoia",
            "sakura",
            "magnolia"
        )

        sources.safeAmap { source ->
            try {
                val streamUrl =
                    "$base/api/stream?mediaId=$tmdbId&mediaType=$mediaType&tv_slug=$tvSlug" +
                            "&source=$source&apikey=mptv_sk_a8f29c4e7b3d1f" +
                            "&requestToken=$requestToken&token=$finalToken"

                val streamRes = JSONObject(safeGet(streamUrl, headers = headers).text)

                if (!streamRes.optBoolean("success")) return@safeAmap

                val m3u8 = streamRes
                    .getJSONObject("data")
                    .optString("stream_url")

                if (m3u8.isNotEmpty()) {
                    generateM3u8(
                        "Mapple [${source.uppercase()}]",
                        m3u8,
                        "$base/",
                        headers = headers
                    ).forEach(callback)
                }

            } catch (_: Exception) {
            }
        }
    }





    suspend fun invokeVidzee(
        id: Int?,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val secret = base64Decode("cGxlYXNlZG9udHNjcmFwZW1lc2F5d2FsbGFoaQ==")
        val keyBytes = secret.padEnd(32, '\u0000').toByteArray(Charsets.UTF_8)
        val defaultReferer = "https://player.vidzee.wtf/"

        (1..8).toList().safeAmap { sr ->
            try {
                val apiUrl = if (season == null) {
                    "https://player.vidzee.wtf/api/server?id=$id&sr=$sr"
                } else {
                    "https://player.vidzee.wtf/api/server?id=$id&sr=$sr&ss=$season&ep=$episode"
                }

                val response = safeGet(apiUrl).text
                val json = JSONObject(response)

                val globalHeaders = mutableMapOf<String, String>()
                json.optJSONObject("headers")?.let { headersObj ->
                    headersObj.keys().forEach { key ->
                        globalHeaders[key] = headersObj.getString(key)
                    }
                }

                val urls = json.optJSONArray("url") ?: JSONArray()
                for (i in 0 until urls.length()) {
                    val obj = urls.getJSONObject(i)
                    val encryptedLink = obj.optString("link")
                    val name = obj.optString("name", "Vidzee")
                    val type = obj.optString("type", "hls")
                    val lang = obj.optString("lang", "Unknown")
                    val flag = obj.optString("flag", "")

                    if (encryptedLink.isNotBlank()) {
                        val finalUrl = try {
                            decryptVidzeeUrl(encryptedLink, keyBytes)
                        } catch (e: Exception) {
                            Log.e("VidzeeDecrypt", "Failed to decrypt link: ${e.message}")
                            encryptedLink
                        }

                        try {
                            URI(finalUrl) // Validate URL
                            val headersMap = mutableMapOf<String, String>()
                            headersMap.putAll(globalHeaders)
                            val referer = headersMap["referer"] ?: defaultReferer
                            val displayName =
                                if (flag.isNotBlank()) "VidZee $name ($lang - $flag)" else "VidZee $name ($lang)"

                            callback.invoke(
                                newExtractorLink(
                                    "VidZee",
                                    displayName,
                                    finalUrl,
                                    if (type.equals("hls", ignoreCase = true))
                                        ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                ) {
                                    this.referer = referer
                                    this.headers = headersMap
                                    this.quality = Qualities.P1080.value
                                }
                            )
                        } catch (e: Exception) {
                            Log.e("VidzeeUrl", "Invalid URL: $finalUrl - ${e.message}")
                        }
                    }
                }

                val subs = json.optJSONArray("tracks") ?: JSONArray()
                for (i in 0 until subs.length()) {
                    val sub = subs.getJSONObject(i)
                    val subLang = sub.optString("lang", "Unknown")
                    val subUrl = sub.optString("url")
                    if (subUrl.isNotBlank()) subtitleCallback(newSubtitleFile(subLang, subUrl))
                }

            } catch (e: Exception) {
                Log.e("VidzeeApi", "Failed sr=$sr: ${e.message}")
            }
        }
    }

    fun decryptVidzeeUrl(encryptedUrl: String, keyBytes: ByteArray): String {
        try {
            val decoded = String(base64DecodeArray(encryptedUrl))
            val parts = decoded.split(":", limit = 2)
            if (parts.size != 2) {
                throw IllegalArgumentException("Invalid encrypted URL format")
            }

            val ivB64 = parts[0]
            val ciphertextB64 = parts[1]

            val iv = base64DecodeArray(ivB64)
            val ciphertext = base64DecodeArray(ciphertextB64)

            val keySpec = SecretKeySpec(keyBytes, 0, keyBytes.size, "AES")
            val ivSpec = IvParameterSpec(iv)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)

            val decrypted = cipher.doFinal(ciphertext)

            return String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("VidzeeDecrypt", "Decryption failed: ${e.message}")
            throw e
        }
    }


    suspend fun invokeTopMovies(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val topmoviesAPI = getDomains()?.topMovies ?: return

        imdbId ?: return

        val url = if (season == null) {
            "$topmoviesAPI/search/${imdbId}"
        } else {
            "$topmoviesAPI/search/${imdbId} Season $season"
        }

        val hrefpattern = runCatching {
            safeGet(url).document.select("#content_box article a")
                .firstOrNull()?.attr("href")?.takeIf(String::isNotBlank)
        }.getOrNull() ?: return

        val res = runCatching {
            safeGet(
                hrefpattern,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0"
                ),
                interceptor = wpRedisInterceptor
            ).document
        }.getOrNull() ?: return

        if (season == null) {
            val detailPageUrls = res.select("a.maxbutton-download-links")
                .mapNotNull { it.attr("href").takeIf { it.isNotBlank() } }

            detailPageUrls.safeAmap { detailPageUrl ->
                val detailPageDocument =
                    runCatching { safeGet(detailPageUrl).document }.getOrNull() ?: return@safeAmap

                val driveLinks = detailPageDocument.select("a.maxbutton-fast-server-gdrive")
                    .mapNotNull { it.attr("href").takeIf(String::isNotBlank) }

                driveLinks.safeAmap { driveLink ->
                    val finalLink = if (driveLink.contains("unblockedgames")) {
                        bypassHrefli(driveLink) ?: return@safeAmap
                    } else {
                        driveLink
                    }

                    loadSourceNameExtractor(
                        "TopMovies",
                        finalLink,
                        "$topmoviesAPI/",
                        subtitleCallback,
                        callback
                    )
                }
            }
        } else {
            val detailPageUrls = res.select("a.maxbutton-g-drive")
                .mapNotNull { it.attr("href").takeIf(String::isNotBlank) }

            detailPageUrls.safeAmap { detailPageUrl ->
                val detailPageDocument =
                    runCatching { safeGet(detailPageUrl).document }.getOrNull() ?: return@safeAmap

                val episodeLink = detailPageDocument.select("span strong")
                    .firstOrNull {
                        it.text().matches(Regex(".*Episode\\s+$episode.*", RegexOption.IGNORE_CASE))
                    }
                    ?.parent()?.closest("a")?.attr("href")
                    ?.takeIf(String::isNotBlank) ?: return@safeAmap

                val finalLink = if (episodeLink.contains("unblockedgames")) {
                    bypassHrefli(episodeLink) ?: return@safeAmap
                } else {
                    episodeLink
                }

                loadSourceNameExtractor(
                    "TopMovies",
                    finalLink,
                    "$topmoviesAPI/",
                    subtitleCallback,
                    callback
                )
            }
        }
    }


    suspend fun invokeMoviesmod(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val MoviesmodAPI = getDomains()?.moviesmod ?: return
        invokeModflix(
            imdbId,
            season,
            episode,
            subtitleCallback,
            callback,
            MoviesmodAPI
        )
    }


    suspend fun invokeModflix(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        api: String
    ) {
        val url: String = if (season == null) {
            "$api/search/$id"
        } else {
            "$api/search/$id $season"
        }
        val href = safeGet(url).document.selectFirst("#content_box article > a")?.attr("href")

        val hTag = if (season == null) "h4" else "h3"
        val aTag = if (season == null) "Download" else "Episode"
        val sTag = if (season == null) "" else "(S0$season|Season $season)"
        val res = app.get(
            href ?: return,
        ).document

        val entries = res.select("div.thecontent $hTag:matches((?i)$sTag.*(480p|720p|1080p|2160p))")
            .filter { element ->
                val text = element.text()
                !text.contains("MoviesMod", true)
            }

        entries.safeAmap {
            val link =
                it.nextElementSibling()?.select("a:contains($aTag)")?.attr("href")
                    ?.substringAfter("=") ?: ""
            val selector =
                if (season == null) "p a.maxbutton" else "h3 a:matches(Episode $episode)"

            if (link.isNotEmpty()) {
                val source = app.get(link).document.selectFirst(selector)?.attr("href") ?: return@safeAmap
                val bypassedLink = bypassHrefli(source).toString()
                loadSourceNameExtractor("Moviesmod", bypassedLink, "", subtitleCallback, callback)
            }
        }
    }

    private val vegaHeaders by lazy {
        mapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            "Accept-Language" to "en-US,en;q=0.9",
            "Cache-Control" to "no-cache",
            "Pragma" to "no-cache",
            "Upgrade-Insecure-Requests" to "1",
            "Sec-Fetch-Dest" to "document",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "none",
            "Sec-Fetch-User" to "?1",
            "sec-ch-ua" to "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Microsoft Edge\";v=\"120\"",
            "sec-ch-ua-mobile" to "?0",
            "sec-ch-ua-platform" to "\"Linux\"",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
            "cookie" to "xla=s4t"
        )
    }

    suspend fun invokeVegamovies(
        title: String?=null,
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val api = getDomains()?.vegamovies ?: return
        val imdb = id ?: return

        val headers = vegaHeaders // move to top-level val if reused

        suspend fun fetchResults(query: String): List<VegamoviesDocument> {
            val url = "$api/search.php?q=${query.replace(" ", "%20")}"
            return safeGet(url, referer = api, headers = headers)
                .parsedSafe<VegamoviesResponse>()?.hits
                ?.mapNotNull { it.document }
                ?: emptyList()
        }

        val imdbMatch = fetchResults(imdb).firstOrNull {
            it.imdb_id.equals(imdb, true)
        }

        val match = imdbMatch ?: run {
            val t = title ?: return
            val results = fetchResults(t)

            results.firstOrNull {
                it.post_title?.contains(t, ignoreCase = true) == true
            } ?: results.firstOrNull()
        } ?: return

        val permalink = match.permalink ?: return
        val mainDoc = safeGet(api + permalink, referer = api, headers = headers).document


        if (season == null) {
            mainDoc.select("button.dwd-button")
                .mapNotNull { it.parent()?.attr("href") }
                .filter { it.isNotBlank() }
                .distinct()
                .safeAmap { page ->
                    val doc = runCatching {
                        safeGet(page, referer = api, headers = headers).document
                    }.getOrNull() ?: return@safeAmap

                    val sources = doc.select("button.btn:matches((?i)(V-Cloud))")
                        .mapNotNull { it.parent()?.attr("href") }
                        .filter { it.isNotBlank() }

                    sources.forEach { source ->
                        loadSourceNameExtractor(
                            "VegaMovies",
                            source,
                            "",
                            subtitleCallback,
                            callback
                        )
                    }
                }
            return
        }

        val seasonRegex = Regex("(?i)Season $season")
        val episodeRegex = Regex("(?i)Episodes?:\\s*$episode")

        mainDoc.select("h3,h5")
            .asSequence()
            .filter { it.text().contains(seasonRegex) || it.text().contains("Episode", true) }
            .flatMap {
                generateSequence(it.nextElementSibling()) { el -> el.nextElementSibling() }
                    .takeWhile { el -> el.tagName() !in listOf("h3", "h5", "h4") }
                    .flatMap { el ->
                        el.select("a:matches((?i)(V-Cloud|Single|Episode))").asSequence()
                    }
            }
            .map { it.attr("href") }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
            .safeAmap { page ->
                val doc = runCatching {
                    safeGet(page, referer = api, headers = headers).document
                }.getOrNull() ?: return@safeAmap

                val epNode = doc.select("h4")
                    .firstOrNull { it.text().contains(episodeRegex) }
                    ?: return@safeAmap

                val links = epNode.nextElementSibling()
                    ?.select("a:matches((?i)(V-Cloud|Single|Episode))")
                    ?.map { it.attr("href") }
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()

                links.forEach { link ->
                    loadSourceNameExtractor(
                        "VegaMovies",
                        link,
                        "",
                        subtitleCallback,
                        callback
                    )
                }
            }
    }


    suspend fun invokeRogmovies(
        title: String? = null,
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val api = getDomains()?.rogmovies ?: return
        val headers = vegaHeaders

        suspend fun search(query: String): List<VegamoviesDocument> {
            return safeGet("$api/search.php?q=$query", referer = api, headers = headers)
                .parsedSafe<VegamoviesResponse>()?.hits
                ?.mapNotNull { it.document }
                ?: emptyList()
        }

        val results = when {
            !id.isNullOrBlank() -> search(id)
            !title.isNullOrBlank() -> search(title)
            else -> return
        }

        if (results.isEmpty()) return

        val keywords = title
            ?.lowercase()
            ?.replace(normalizeAlphaNumSpaceRegex, "")
            ?.split(" ")
            ?.filter { it.length > 2 }
            ?: emptyList()

        val match = results.firstOrNull {
            !id.isNullOrBlank() && it.imdb_id.equals(id, true)
        } ?: results.firstOrNull { doc ->
            val docTitle = doc.post_title
                ?.lowercase()
                ?.replace(normalizeAlphaNumSpaceRegex, "")
                ?: return@firstOrNull false

            keywords.any { docTitle.contains(it) }
        } ?: results.firstOrNull() ?: return

        val permalink = match.permalink ?: return
        val mainDoc = safeGet(api + permalink, referer = api, headers = headers).document

        if (season == null) {
            mainDoc.select("button.dwd-button")
                .mapNotNull { it.parent()?.attr("href") }
                .filter { it.isNotBlank() }
                .distinct()
                .safeAmap { page ->
                    val doc = runCatching {
                        safeGet(page, referer = api, headers = headers).document
                    }.getOrNull() ?: return@safeAmap

                    val sources = doc.select("button.btn:matches((?i)(V-Cloud|G-Direct))")
                        .mapNotNull { it.parent()?.attr("href") }
                        .filter { it.isNotBlank() }
                    sources.forEach { source ->
                        loadSourceNameExtractor(
                            "RogMovies",
                            source,
                            "",
                            subtitleCallback,
                            callback
                        )
                    }
                }
            return
        }

        val seasonRegex = Regex("(?i)Season $season")
        val episodeText = "Episode $episode"

        mainDoc.select("h3, h5")
            .asSequence()
            .filter { it.text().contains(seasonRegex) }
            .flatMap {
                generateSequence(it.nextElementSibling()) { el -> el.nextElementSibling() }
                    .takeWhile { it.tagName() !in listOf("h3", "h5") }
                    .flatMap {
                        it.select("a:matches((?i)(V-Cloud|Single|Episode|G-Direct))").asSequence()
                    }
            }
            .map { it.attr("href") }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
            .safeAmap { page ->
                val doc = runCatching {
                    safeGet(page, referer = api, headers = headers).document
                }.getOrNull() ?: return@safeAmap

                val epNode = doc.select("h4")
                    .firstOrNull { it.text().contains(episodeText, true) }
                    ?: return@safeAmap

                val links = epNode.nextElementSibling()
                    ?.select("a:matches((?i)(V-Cloud|Single|Episode|G-Direct))")
                    ?.map { it.attr("href") }
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()

                links.forEach { link ->
                    loadSourceNameExtractor(
                        "RogMovies",
                        link,
                        "",
                        subtitleCallback,
                        callback
                    )
                }
        }
    }

    suspend fun invokeNepu(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val slug = title?.createSlug() ?: return
        val headers = mapOf(
            "X-Requested-With" to "XMLHttpRequest"
        )

        val data = try {
            val res = safeGet(
                "$nepuAPI/ajax/posts?q=$title",
                headers = headers,
                referer = "$nepuAPI/"
            )
            if (res.code != 200) return
            res.parsedSafe<NepuSearch>()?.data
        } catch (e: Exception) {
            Log.e("Nepu", "Search request failed: ${e.localizedMessage}")
            return
        }

        val media =
            data?.find { it.url?.startsWith(if (season == null) "/movie/$slug-$year-" else "/serie/$slug-$year-") == true }
                ?: data?.find {
                    it.name.equals(
                        title,
                        true
                    ) && it.type == if (season == null) "Movie" else "Serie"
                }

        val mediaUrl = media?.url ?: return
        val fullMediaUrl =
            if (season == null) mediaUrl else "$mediaUrl/season/$season/episode/$episode"

        val dataId = try {
            val pageRes = safeGet(fixUrl(fullMediaUrl, nepuAPI))
            if (pageRes.code != 200) return
            pageRes.document.selectFirst("a[data-embed]")?.attr("data-embed")
        } catch (e: Exception) {
            Log.e("Nepu", "Media page request failed: ${e.localizedMessage}")
            return
        } ?: return

        val res = try {
            val postRes = app.post(
                "$nepuAPI/ajax/embed",
                data = mapOf("id" to dataId),
                referer = fullMediaUrl,
                headers = headers
            )
            if (postRes.code != 200) return
            postRes.text
        } catch (e: Exception) {
            Log.e("Nepu", "Embed request failed: ${e.localizedMessage}")
            return
        }

        val m3u8 = "(http[^\"]+)".toRegex().find(res)?.groupValues?.get(1) ?: return

        callback.invoke(
            newExtractorLink(
                "Nepu",
                "Nepu",
                url = m3u8,
                INFER_TYPE
            ) {
                this.referer = "$nepuAPI/"
                this.quality = Qualities.P1080.value
            }
        )
    }

    // only subs
    suspend fun invokeWatchsomuch(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        val id = imdbId?.removePrefix("tt")
        val epsId = app.post(
            "$watchSomuchAPI/Watch/ajMovieTorrents.aspx",
            data = mapOf(
                "index" to "0",
                "mid" to "$id",
                "wsk" to "30fb68aa-1c71-4b8c-b5d4-4ca9222cfb45",
                "lid" to "",
                "liu" to ""
            ),
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).parsedSafe<WatchsomuchResponses>()?.movie?.torrents?.let { eps ->
            if (season == null) {
                eps.firstOrNull()?.id
            } else {
                eps.find { it.episode == episode && it.season == season }?.id
            }
        } ?: return

        val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)

        val subUrl = if (season == null) {
            "$watchSomuchAPI/Watch/ajMovieSubtitles.aspx?mid=$id&tid=$epsId&part="
        } else {
            "$watchSomuchAPI/Watch/ajMovieSubtitles.aspx?mid=$id&tid=$epsId&part=S${seasonSlug}E${episodeSlug}"
        }

        safeGet(subUrl).parsedSafe<WatchsomuchSubResponses>()?.subtitles?.map { sub ->
            subtitleCallback.invoke(
                newSubtitleFile(
                    sub.label?.substringBefore("&nbsp") ?: "", fixUrl(
                        sub.url
                            ?: return@map null, watchSomuchAPI
                    )
                )
            )
        }
    }

    suspend fun invokeDahmerMovies(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val url = if (season == null) {
            "$dahmerMoviesAPI/movies/${title?.replace(":", "")} ($year)/"
        } else {
            "$dahmerMoviesAPI/tvs/${title?.replace(":", " -")}/Season $season/"
        }
        val request = safeGet(url, timeout = 60L)
        if (!request.isSuccessful) return
        val paths = request.document.select("a").map {
            it.text() to it.attr("href")
        }.filter {
            if (season == null) {
                it.first.contains(Regex("(?i)(1080p|2160p)"))
            } else {
                val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)
                it.first.contains(Regex("(?i)S${seasonSlug}E${episodeSlug}"))
            }
        }.ifEmpty { return }

        paths.forEach {
            val quality = getIndexQuality(it.first)
            val tags = getIndexQualityTags(it.first)
            val href =
                if (it.second.contains(dahmerMoviesAPI)) it.second else (dahmerMoviesAPI + it.second)

            callback.invoke(
                newExtractorLink(
                    "DahmerMovies",
                    "DahmerMovies $tags",
                    url = href,
                    ExtractorLinkType.VIDEO
                ) {
                    this.quality = quality
                }
            )
        }
    }

    suspend fun invokeNinetv(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (season == null) {
            "$nineTvAPI/movie/$tmdbId"
        } else {
            "$nineTvAPI/tv/$tmdbId-$season-$episode"
        }

        val response = safeGet(url, referer = "https://pressplay.top/")
        if (response.code != 200) return
        val iframe = response.document.selectFirst("iframe")?.attr("src") ?: return
        loadExtractor(iframe, "$nineTvAPI/", subtitleCallback, callback)
    }

    suspend fun invokeAllMovieland(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        runCatching {
            val playerResponse = safeGet("https://allmovieland.link/player.js?v=60%20128")
            if (playerResponse.code != 200) return@runCatching
            val playerScript = playerResponse.toString()
            val domainRegex = Regex("const AwsIndStreamDomain.*'(.*)';")
            val host =
                domainRegex.find(playerScript)?.groupValues?.getOrNull(1) ?: return@runCatching

            val resResponse = safeGet("$host/play/$imdbId", referer = "$allmovielandAPI/")
            if (resResponse.code != 200) return@runCatching
            val resData =
                resResponse.document.selectFirst("script:containsData(playlist)")?.data()
                    ?.substringAfter("{")?.substringBefore(";")?.substringBefore(")")
                    ?: return@runCatching

            val json = tryParseJson<AllMovielandPlaylist>("{$resData}") ?: return@runCatching
            val headers = mapOf("X-CSRF-TOKEN" to "${json.key}")
            val jsonfile =
                if (json.file?.startsWith("http") == true) json.file else host + json.file

            val serverResponse = safeGet(jsonfile, headers = headers, referer = "$allmovielandAPI/")
            if (serverResponse.code != 200) return@runCatching
            val serverJson = serverResponse.text.replace(Regex(""",\s*/"""), "")
            val cleanedJson = serverJson.replace(Regex(",\\s*\\[\\s*]"), "")

            val servers = tryParseJson<ArrayList<AllMovielandServer>>(cleanedJson)?.let { list ->
                if (season == null) {
                    list.mapNotNull { it.file?.let { file -> file to it.title.orEmpty() } }
                } else {
                    list.find { it.id == season.toString() }
                        ?.folder?.find { it.episode == episode.toString() }
                        ?.folder?.mapNotNull { it.file?.let { file -> file to it.title.orEmpty() } }
                }
            } ?: return@runCatching

            servers.safeAmap { (server, lang) ->
                runCatching {
                    val playlistResponse = app.post(
                        "$host/playlist/$server.txt",
                        headers = headers,
                        referer = "$allmovielandAPI/"
                    )
                    if (playlistResponse.code != 200) return@runCatching
                    val playlistUrl = playlistResponse.text
                    val headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36",
                        "Accept" to "*/*",
                        "Referer" to allmovielandAPI,
                        "Origin" to allmovielandAPI
                    )

                    generateM3u8(
                        "AllMovieLand-$lang",
                        playlistUrl,
                        allmovielandAPI,
                        headers = headers
                    ).forEach(callback)
                }.onFailure { it.printStackTrace() }
            }
        }.onFailure { it.printStackTrace() }
    }

    suspend fun invokeMoviesdrive(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val domain = getDomains()?.moviesdrive ?: return
        val id = imdbId ?: return

        val searchUrl = "$domain/search.php?q=$id"
        val root = runCatching {
            JSONObject(safeGet(searchUrl, interceptor = wpRedisInterceptor).text)
        }.getOrNull() ?: return

        val hits = root.optJSONArray("hits") ?: return

        val match = (0 until hits.length())
            .asSequence()
            .mapNotNull { hits.optJSONObject(it)?.optJSONObject("document") }
            .firstOrNull { it.optString("imdb_id") == id }
            ?: return

        val permalink = match.optString("permalink")
        if (permalink.isBlank()) return
        val href = if (permalink.startsWith("http")) permalink else domain + permalink
        val mainDoc = safeGet(href).document

        if (season == null) {
            val seenUrls = Collections.synchronizedSet(mutableSetOf<String>())

            mainDoc.select("h5 > a")
                .map { it.attr("href") }
                .filter { it.isNotBlank() }
                .distinct()
                .safeAmap { href ->
                    val servers = extractMdrive(href)
                    servers.forEach { server ->
                        if (seenUrls.add(server)) {
                            loadSourceNameExtractor("MoviesDrive", server, "", subtitleCallback, callback)
                        }
                    }
                }
            return
        } else {
            val (sSlug, eSlug) = getEpisodeSlug(season, episode)
            val stag = "Season $season|S$sSlug"
            val sep = "Ep$eSlug|Ep$episode"
            val entries = mainDoc.select("h5:matches((?i)$stag)")
            entries.safeAmap { entry ->
                val href = entry.nextElementSibling()?.selectFirst("a")?.attr("href") ?: ""

                if (href.isNotBlank()) {
                    val doc = app.get(href).document
                    val fEp = doc.selectFirst("h5:matches((?i)$sep)")
                    val linklist = mutableListOf<String>()
                    val source1 = fEp?.nextElementSibling()?.selectFirst("a")?.attr("href")
                    val source2 = fEp?.nextElementSibling()?.nextElementSibling()?.selectFirst("a")?.attr("href")
                    if (source1 != null) linklist.add(source1)
                    if (source2 != null) linklist.add(source2)

                    linklist.safeAmap { url ->
                        loadSourceNameExtractor(
                            "MoviesDrive",
                            url,
                            "",
                            subtitleCallback,
                            callback
                        )
                    }
                }
            }
        }
    }

    suspend fun invokeBollyflix(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val bollyflixAPI = getDomains()?.bollyflix ?: return
        val res1 = safeGet("$bollyflixAPI/search/$id", timeout = 10000).document

        res1.select("div > article > a").forEach {
            val url = it.attr("href")
            val res = safeGet(url).document
            val hTag = if (season == null) "h5" else "h4"
            val sTag = if (season == null) "" else "Season $season"
            val entries =
                res.select("div.thecontent.clearfix > $hTag:matches((?i)$sTag.*(480p|720p|1080p|2160p))")
                    .filter { element -> !element.text().contains("Download", true) }.takeLast(4)

            entries.forEach {
                var href = it.nextElementSibling()?.select("a")?.attr("href") ?: return@forEach

                if(href.contains("id=")) {
                    val token = href.substringAfter("id=")
                    val encodedurl =
                        app.get("https://web.sidexfee.com/?id=$token").text.substringAfter("link\":\"")
                            .substringBefore("\"};")
                    href = base64Decode(encodedurl)
                }

                if (season == null) {
                    loadSourceNameExtractor("Bollyflix", href , "", subtitleCallback, callback)
                } else {
                    val episodeText = "Episode " + episode.toString().padStart(2, '0')
                    val link =
                        app.get(href).document.selectFirst("article h3 a:contains($episodeText)")!!
                            .attr("href")
                    loadSourceNameExtractor("Bollyflix", link , "", subtitleCallback, callback)
                }
            }
        }
    }


    suspend fun invokeRiveStream(
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        val headers = mapOf("User-Agent" to USER_AGENT)

        suspend fun <T> retry(times: Int = 3, block: suspend () -> T): T? {
            repeat(times - 1) {
                try {
                    return block()
                } catch (_: Exception) {
                }
            }
            return try {
                block()
            } catch (_: Exception) {
                null
            }
        }

        val sourceApiUrl =
            "$RiveStreamAPI/api/backendfetch?requestID=VideoProviderServices&secretKey=rive"
        val sourceList = retry { safeGet(sourceApiUrl, headers).parsedSafe<RiveStreamSource>() }

        val document =
            retry { safeGet(RiveStreamAPI, headers, timeout = 20).document } ?: return
        val appScript = document.select("script")
            .firstOrNull { it.attr("src").contains("_app") }?.attr("src") ?: return

        val js = retry { safeGet("$RiveStreamAPI$appScript").text } ?: return
        val keyList = Regex("""let\s+c\s*=\s*(\[[^]]*])""")
            .findAll(js).firstOrNull { it.groupValues[1].length > 2 }?.groupValues?.get(1)
            ?.let { array ->
                Regex("\"([^\"]+)\"").findAll(array).map { it.groupValues[1] }.toList()
            } ?: emptyList()

        val secretKey = retry {
            safeGet(
                "https://rivestream.supe2372.workers.dev/?input=$id&cList=${keyList.joinToString(",")}"
            ).text
        } ?: return

        sourceList?.data?.safeAmap { source ->
            try {
                val streamUrl = if (season == null) {
                    "$RiveStreamAPI/api/backendfetch?requestID=movieVideoProvider&id=$id&service=$source&secretKey=$secretKey"
                } else {
                    "$RiveStreamAPI/api/backendfetch?requestID=tvVideoProvider&id=$id&season=$season&episode=$episode&service=$source&secretKey=$secretKey"
                }

                val responseString = retry {
                    safeGet(streamUrl, headers, timeout = 10).text
                } ?: return@safeAmap

                try {
                    val json = JSONObject(responseString)
                    val sourcesArray =
                        json.optJSONObject("data")?.optJSONArray("sources") ?: return@safeAmap

                    for (i in 0 until sourcesArray.length()) {
                        val src = sourcesArray.getJSONObject(i)
                        val label = if (src.optString("source")
                                .contains("AsiaCloud", ignoreCase = true)
                        ) "RiveStream ${src.optString("source")}[${src.optString("quality")}]" else "RiveStream ${
                            src.optString(
                                "source"
                            )
                        }"
                        val quality = Qualities.P1080.value
                        val url = src.optString("url")

                        try {
                            if (url.contains("proxy?url=")) {
                                try {
                                    val fullyDecoded = URLDecoder.decode(url, "UTF-8")

                                    val encodedUrl = fullyDecoded.substringAfter("proxy?url=")
                                        .substringBefore("&headers=")
                                    val decodedUrl = URLDecoder.decode(
                                        encodedUrl,
                                        "UTF-8"
                                    ) // decode again if needed

                                    val encodedHeaders = fullyDecoded.substringAfter("&headers=")
                                    val headersMap = try {
                                        val jsonStr = URLDecoder.decode(encodedHeaders, "UTF-8")
                                        JSONObject(jsonStr).let { json ->
                                            json.keys().asSequence()
                                                .associateWith { json.getString(it) }
                                        }
                                    } catch (_: Exception) {
                                        emptyMap()
                                    }

                                    val referer = headersMap["Referer"] ?: ""
                                    val origin = headersMap["Origin"] ?: ""
                                    val videoHeaders =
                                        mapOf("Referer" to referer, "Origin" to origin)

                                    val type = if (decodedUrl.contains(".m3u8", ignoreCase = true))
                                        ExtractorLinkType.M3U8 else INFER_TYPE

                                    callback.invoke(
                                        newExtractorLink(
                                            label,
                                            label,
                                            decodedUrl,
                                            type
                                        ) {
                                            this.quality = quality
                                            this.referer = referer
                                            this.headers = videoHeaders
                                        })
                                } catch (_: Exception) {
                                    Log.e(
                                        "RiveStreamSourceError",
                                        "Failed to decode proxy URL: $url"
                                    )
                                }
                            } else {
                                val type = if (url.contains(".m3u8", ignoreCase = true))
                                    ExtractorLinkType.M3U8 else INFER_TYPE

                                callback.invoke(
                                    newExtractorLink(
                                        "$label (VLC)",
                                        "$label (VLC)",
                                        url,
                                        type
                                    ) {
                                        this.referer = ""
                                        this.quality = quality
                                    })
                            }
                        } catch (e: Exception) {
                            Log.e("RiveStreamSourceError", "Source parse failed: $url $e")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RiveStreamParseError", "Failed to parse JSON for service $source: $e")
                }
            } catch (e: Exception) {
                Log.e("RiveStreamError", "Failed service: $source $e")
            }
        }
    }

    suspend fun invokeVidSrcXyz(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (season == null) {
            "$Vidsrcxyz/embed/movie?imdb=$id"
        } else {
            "$Vidsrcxyz/embed/tv?imdb=$id&season=$season&episode=$episode"
        }
        val iframeUrl = extractIframeUrl(url) ?: return
        val prorcpUrl = extractProrcpUrl(iframeUrl) ?: "Not Found 2"

        val decryptedSource = extractAndDecryptSource(prorcpUrl, iframeUrl) ?: return

        val referer = prorcpUrl.substringBefore("rcp")
        decryptedSource.forEach { (version, url) ->
            generateM3u8(
                "VidsrcXYZ Server ${version.capitalize()}",
                url,
                referer
            ).forEach(callback)
        }

    }

    private suspend fun extractIframeUrl(url: String): String? {
        return httpsify(
            safeGet(url).document.select("iframe").attr("src")
        ).takeIf { it.isNotEmpty() }
    }

    private suspend fun extractProrcpUrl(iframeUrl: String): String? {
        val doc = safeGet(iframeUrl, referer = iframeUrl).document
        val regex = Regex("src:\\s+'(.*?)'")
        val matchedSrc = regex.find(doc.html())?.groupValues?.get(1) ?: return null
        val host = getBaseUrl(iframeUrl)
        return host + matchedSrc
    }

    private suspend fun extractAndDecryptSource(
        prorcpUrl: String,
        referer: String
    ): List<Pair<String, String>>? {
        val responseText = safeGet(prorcpUrl, referer = referer).text
        val playerJsRegex = Regex("""Playerjs\(\{.*?file:"(.*?)".*?\}\)""")
        val temp = playerJsRegex.find(responseText)?.groupValues?.get(1)

        val encryptedURLNode = if (!temp.isNullOrEmpty()) {
            mapOf("id" to "playerjs", "content" to temp)
        } else {
            val document = Jsoup.parse(responseText)
            val reporting = document.selectFirst("#reporting_content") ?: return null
            val node = reporting.nextElementSibling() ?: return null
            mapOf("id" to node.attr("id"), "content" to node.text())
        }

        val id = encryptedURLNode["id"] ?: return null
        val content = encryptedURLNode["content"] ?: return null

        val decrypted = decryptMethods[id]?.invoke(content) ?: return null

        // Domain mapping
        val vSubs = mapOf(
            "v1" to "shadowlandschronicles.com",
            "v2" to "cloudnestra.com",
            "v3" to "thepixelpioneer.com",
            "v4" to "putgate.org",
            "v5" to ""
        )
        val placeholderRegex = "\\{(v\\d+)\\}".toRegex()
        val mirrors: List<Pair<String, String>> = decrypted
            .split(" or ")
            .map { it.trim() }
            .filter { it.startsWith("http") }
            .map { rawUrl ->
                val match = placeholderRegex.find(rawUrl)
                val version = match?.groupValues?.get(1) ?: ""   // v1, v2, v3 etc or "" if none
                val domain = vSubs[version] ?: ""

                // replace {vX} with actual domain
                val finalUrl = if (domain.isNotEmpty()) {
                    placeholderRegex.replace(rawUrl) { domain }
                } else {
                    rawUrl
                }

                version to finalUrl
            }

        return mirrors.ifEmpty { null }
    }


    suspend fun invokeSuperstream(
        token: String? = null,
        imdbId: String? = null,
        id: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        var success = false
        if (!imdbId.isNullOrBlank()) {
            try {
                val searchUrl = "$fourthAPI/search?keyword=$imdbId"

                val href: String? = app.get(searchUrl)
                    .document
                    .selectFirst("h2.film-name a")
                    ?.attr("href")
                    ?.let { fourthAPI + it }

                val mediaId: Int? = href?.let { url ->
                    app.get(url)
                        .document
                        .selectFirst("h2.heading-name a")
                        ?.attr("href")
                        ?.substringAfterLast("/")
                        ?.toIntOrNull()
                }

                if (mediaId != null) {
                    val seasonNumber = season ?: 1
                    invokeExternalSource(
                        mediaId,
                        seasonNumber,
                        season,
                        episode,
                        callback,
                        token
                    )
                    success = true
                }
            } catch (_: Exception) {
                // ignore and fallback
            }
        }

        if (success) return
        if (id == null || token.isNullOrBlank()) return

        val encodedToken = withContext(Dispatchers.IO) {
            URLEncoder.encode(token, "UTF-8")
        }

        val url = if (season == null) {
            "$NuvFeb/api/media/movie/$id?cookie=$encodedToken"
        } else {
            "$NuvFeb/api/media/tv/$id/$season/$episode?cookie=$encodedToken"
        }

        val parsed = retryFetch(url) ?: return

        parsed.versions.orEmpty().forEach { version ->
            val baseTitle = version.name
                ?.takeIf { it.isNotBlank() }
                ?.let(::cleanTitle)
                ?: "Stream"

            version.links.orEmpty().forEach { link ->
                val streamUrl = link.url ?: return@forEach
                val qualityName = link.quality.orEmpty()

                val name = if (qualityName.equals("ORG", true)) {
                    "SuperStream • $baseTitle • ORG"
                } else {
                    "SuperStream • $baseTitle"
                }

                callback(
                    newExtractorLink(
                        source = "SuperStream",
                        name = name,
                        url = streamUrl,
                        type = INFER_TYPE
                    ) {
                        quality = getQualityFromName(qualityName)
                    }
                )
            }
        }
    }
    private suspend fun retryFetch(url: String): FebResponse? {
        repeat(3) { _ ->
            val res = safeGet(url, timeout = 10000L)

            if (res.code == 500) {
                delay(2500L.milliseconds)
            } else {
                return res.parsedSafe()
            }
        }
        return null
    }


    suspend fun invoke4khdhub(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val domain = getDomains()?.n4khdhub ?: return
        val query = title?.takeIf { it.isNotBlank() } ?: return

        val searchDoc = safeGet("$domain/?s=${
            withContext(Dispatchers.IO) {
                URLEncoder.encode(query, "UTF-8")
            }
        }").document
        val normalizedTitle = query.lowercase().trim()
        val yearStr = year?.toString()

        val elements = searchDoc.select("div.card-grid > a.movie-card")

        fun extractContent(el: Element): String {
            return el.selectFirst("div.movie-card-content")
                ?.text()
                ?.lowercase()
                .orEmpty()
        }

        val matched = elements.firstOrNull { el ->
            val content = extractContent(el)
            content.contains(normalizedTitle) &&
                    (yearStr == null || content.contains(yearStr))
        } ?: elements.firstOrNull { el ->
            extractContent(el).contains(normalizedTitle)
        } ?: return

        val link = matched.attr("href")
        val url = if (link.startsWith("http")) link else "$domain$link"

        val doc = safeGet(url).document

        if (season == null) {
            doc.select("div.download-item a")
                .map { it.attr("href") }
                .distinct()
                .safeAmap { href ->
                    val source = getRedirectLinks(href) ?: href
                    loadSourceNameExtractor("4Khdhub", source, "", subtitleCallback, callback)
                }
            return
        }
        else
        {
            val seasonText = "S${season.toString().padStart(2, '0')}"
            val episodeText = episode?.let { "E${it.toString().padStart(2, '0')}" }

            doc.select("div.episode-download-item")
                .asSequence()
                .filter {
                    val text = it.text()
                    text.contains(seasonText, true) &&
                            (episodeText == null || text.contains(episodeText, true))
                }
                .flatMap { it.select("div.episode-links > a").asSequence() }
                .map { it.attr("href") }
                .distinct()
                .toList()
                .safeAmap { href ->
                    val source = getRedirectLinks(href) ?: href
                    loadSourceNameExtractor("4KHDHub", source, "", subtitleCallback, callback)
            }
        }
    }


    suspend fun invokehdhub4u(
        imdbId: String?,
        title: String?,
        year: Int?,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val baseUrl = getDomains()?.hdhub4u
        if (title.isNullOrBlank()) return

        val response = safeGet(
            "https://search.pingora.fyi/collections/post/documents/search" +
                    "?q=$title" +
                    "&query_by=post_title,category" +
                    "&query_by_weights=4,2" +
                    "&sort_by=sort_by_date:desc" +
                    "&limit=20" +
                    "&highlight_fields=none" +
                    "&use_cache=true" +
                    "&page=1",
            referer = baseUrl
        )

        val json = try {
            JSONObject(response.text)
        } catch (e: Exception) {
            Log.d("HDhub4u", "Failed to parse JSON: ${e.message}")
            return
        }
        val hits = json.optJSONArray("hits") ?: return

        val normalizedTitle = title.lowercase().replace(normalizeAlphaNumRegex, "")
        val seasonText = season?.let { "season $it" }

        val posts = mutableListOf<String>()

        for (i in 0 until hits.length()) {
            val document = hits.optJSONObject(i)
                ?.optJSONObject("document")
                ?: continue

            val postTitle = document.optString("post_title").lowercase()
            val rawPermalink = document.optString("permalink")

            val permalink = if (rawPermalink.startsWith("http", ignoreCase = true)) {
                rawPermalink
            } else {
                baseUrl + rawPermalink
            }

            if (postTitle.isBlank() || permalink.isBlank()) continue

            val cleanTitle = postTitle.replace(normalizeAlphaNumRegex, "")

            val matches = when {
                season != null ->
                    cleanTitle.contains(normalizedTitle) &&
                            postTitle.contains(seasonText!!, ignoreCase = true)

                year != null ->
                    cleanTitle.contains(normalizedTitle) &&
                            postTitle.contains(year.toString())

                else ->
                    cleanTitle.contains(normalizedTitle)
            }

            if (matches) {
                posts += permalink
            }
        }

        val matchedPosts = if (!imdbId.isNullOrBlank()) {
            val matched = posts.mapNotNull { postUrl ->
                val postDoc = safeGet(postUrl).document
                val imdbHref = postDoc
                    .selectFirst("""a[href*="imdb.com/title/$imdbId"]""")
                    ?.attr("href")
                    ?: return@mapNotNull null

                val foundImdbId = imdbHref
                    .substringAfter("/tt")
                    .substringBefore("/")
                    .let { "tt$it" }

                if (foundImdbId == imdbId) postUrl else null
            }

            matched.ifEmpty { posts }
        } else posts


        matchedPosts.safeAmap { el ->
            val doc = safeGet(el).document

            if (season == null) {
                val qualityLinks =
                    doc.select("h3 a:matches(480|720|1080|2160|4K), h4 a:matches(480|720|1080|2160|4K)")
                for (linkEl in qualityLinks) {
                    val resolvedLink = linkEl.attr("href")
                    val resolvedWatch = if ("id=" in resolvedLink) {
                        runCatching { getRedirectLinks(resolvedLink) ?: resolvedLink }.getOrDefault(resolvedLink)
                    } else resolvedLink
                    loadSourceNameExtractor(
                        "HDhub4u",
                        resolvedWatch,
                        "",
                        subtitleCallback,
                        callback
                    )
                }
            } else {
                val episodeRegex = Regex("episode\\s*(\\d+)", RegexOption.IGNORE_CASE)
                val h3s = doc.select("h3")

                for (h3 in h3s) {
                    val links = h3.select("a[href]")
                    val episodeLink = links.find { it.text().contains("episode", true) }
                    val watchLink = links.find { it.text().equals("watch", true) }

                    val episodeNum = episodeRegex.find(episodeLink?.text().orEmpty())
                        ?.groupValues?.getOrNull(1)?.toIntOrNull()

                    if (episodeNum != null && (episode == null || episode == episodeNum)) {
                        episodeLink?.absUrl("href")?.let { href ->
                            val resolved = if ("id=" in href) getRedirectLinks(href) else href
                            val episodeDoc =
                                runCatching { safeGet(resolved ?: href).document }.getOrNull()
                                    ?: return@let

                            episodeDoc.select("h3 a[href], h4 a[href], h5 a[href]")
                                .mapNotNull { it.absUrl("href").takeIf { url -> url.isNotBlank() } }
                                .forEach { resolvedLink ->
                                    val resolvedWatch = if ("id=" in resolvedLink) {
                                        runCatching { getRedirectLinks(resolvedLink) ?:resolvedLink }.getOrDefault(resolvedLink)
                                    } else resolvedLink
                                    loadSourceNameExtractor(
                                        "HDhub4u",
                                        resolvedWatch,
                                        "",
                                        subtitleCallback,
                                        callback
                                    )
                                }
                        }

                        watchLink?.absUrl("href")?.let { watchHref ->
                            val resolvedWatch =
                                if ("id=" in watchHref) getRedirectLinks(watchHref) ?: watchHref else watchHref
                            loadSourceNameExtractor(
                                "HDhub4u",
                                resolvedWatch,
                                "",
                                subtitleCallback,
                                callback
                            )
                        }
                    }
                }
            }
        }
    }

    suspend fun invokeHdmovie2(
        title: String? = null,
        year: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val hdmovie2API = getDomains()?.hdmovie2 ?: return
        val slug = title?.createSlug() ?: return
        val url = "$hdmovie2API/movies/$slug-$year"

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        )

        val document = safeGet(url, headers = headers, allowRedirects = true).document
        val ajaxUrl = "$hdmovie2API/wp-admin/admin-ajax.php"

        val commonHeaders = headers + mapOf(
            "Accept" to "*/*",
            "X-Requested-With" to "XMLHttpRequest"
        )

        fun String.getIframe(): String = Jsoup.parse(this).select("iframe").attr("src")

        suspend fun fetchSource(post: String, nume: String, type: String): String {
            val response = app.post(
                url = ajaxUrl,
                data = mapOf(
                    "action" to "doo_player_ajax",
                    "post" to post,
                    "nume" to nume,
                    "type" to type
                ),
                referer = hdmovie2API,
                headers = commonHeaders
            ).parsed<ResponseHash>()
            return response.embed_url.getIframe()
        }

        var link: String? = null

        if (episode != null) {
            document.select("ul#playeroptionsul > li").getOrNull(1)?.let { ep ->
                val post = ep.attr("data-post")
                val nume = (episode + 1).toString()
                link = fetchSource(post, nume, "movie")
            }
        } else {
            document.select("ul#playeroptionsul > li")
                .firstOrNull {
                    it.text().contains("v2", ignoreCase = true) || it.text()
                        .contains("v3", ignoreCase = true)
                }
                ?.let { mv ->
                    val post = mv.attr("data-post")
                    val nume = mv.attr("data-nume")
                    link = fetchSource(post, nume, "movie")
                }

        }

        // If ajax link failed, fallback to legacy anchors
        if (link.isNullOrEmpty()) {
            val type = if (episode != null) "(Combined)" else ""
            document.select("a[href*=dwo]").safeAmap { anchor ->
                val innerDoc = safeGet(anchor.attr("href")).document
                innerDoc.select("div > p > a").safeAmap {
                    val href = it.attr("href")
                    if (href.contains("GDFlix")) {
                        val redirectedUrl = (1..10).firstNotNullOfOrNull {
                            safeGet(href, allowRedirects = false).headers["location"]
                        } ?: href

                        loadSourceNameExtractor(
                            "Hdmovie2$type",
                            redirectedUrl,
                            "",
                            subtitleCallback,
                            callback
                        )
                    }
                }
            }
        } else {
            loadSourceNameExtractor(
                "Hdmovie2",
                link,
                hdmovie2API,
                subtitleCallback,
                callback
            )
        }
    }

    private val random = SecureRandom()

    fun generateDeviceId(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    val deviceId = generateDeviceId()
    suspend fun invokeMovieBox(
        title: String?,
        season: Int? = 0,
        episode: Int? = 0,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            if (title.isNullOrBlank()) return false

            val url = "$movieBox/wefeed-mobile-bff/subject-api/search/v2"
            val jsonBody = """{"page":1,"perPage":10,"keyword":"$title"}"""
            val xClientToken = generateXClientToken()
            val xTrSignature = generateXTrSignature(
                "POST", "application/json", "application/json; charset=utf-8", url, jsonBody
            )
            val headers = mapOf(
                "user-agent" to "com.community.oneroom/50020088 (Linux; U; Android 13; en_US; Subsystem for Android(TM); Build/TQ3A.230901.001; Cronet/145.0.7582.0)",
                "accept" to "application/json",
                "content-type" to "application/json",
                "x-client-token" to xClientToken,
                "x-tr-signature" to xTrSignature,
                "x-client-info" to """{"package_name":"com.community.oneroom","version_name":"3.0.13.0325.03","version_code":50020088,"os":"android","os_version":"13","install_ch":"ps","device_id":"$deviceId","install_store":"ps","gaid":"1b2212c1-dadf-43c3-a0c8-bd6ce48ae22d","brand":"Windows","model":"Subsystem for Android(TM)","system_language":"en","net":"NETWORK_WIFI","region":"US","timezone":"Asia/Calcutta","sp_code":"","X-Play-Mode":"1","X-Idle-Data":"1","X-Family-Mode":"0","X-Content-Mode":"0"}""".trimIndent(),
                "x-client-status" to "0"
            )

            val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
            val response = app.post(url, headers = headers, requestBody = requestBody)
            if (response.code != 200) return false

            val mapper = streamPlayExtractorMapper
            val root = mapper.readTree(response.text)
            val results = root["data"]?.get("results") ?: return false

            val matchingIds = mutableListOf<String>()
            for (result in results) {
                val subjects = result["subjects"] ?: continue
                for (subject in subjects) {
                    val name = subject["title"]?.asText() ?: continue
                    val id = subject["subjectId"]?.asText() ?: continue
                    val type = subject["subjectType"]?.asInt() ?: 0
                    if (name.contains(title, ignoreCase = true) && (type == 1 || type == 2)) {
                        matchingIds.add(id)
                    }
                }
            }
            if (matchingIds.isEmpty()) return false

            var foundLinks = false

            matchingIds.safeAmap { id ->
                try {
                    val subjectUrl = "$movieBox/wefeed-mobile-bff/subject-api/get?subjectId=$id"
                    val subjectXToken = generateXClientToken()
                    val subjectXSign = generateXTrSignature(
                        "GET",
                        "application/json",
                        "application/json",
                        subjectUrl
                    )
                    val subjectHeaders = headers + mapOf(
                        "x-client-token" to subjectXToken,
                        "x-tr-signature" to subjectXSign
                    )
                    val subjectRes = safeGet(subjectUrl, headers = subjectHeaders)

                    val xUserHeader = subjectRes.headers["x-user"]

                    var authtoken: String? = null

                    if (!xUserHeader.isNullOrBlank()) {
                        val xUserJson = mapper.readTree(xUserHeader)
                        authtoken = xUserJson["token"]?.asText()
                    }

                    if (subjectRes.code != 200) return@safeAmap

                    val subjectJson = mapper.readTree(subjectRes.text)
                    val subjectData = subjectJson["data"]
                    val subjectIds = mutableListOf<Pair<String, String>>()
                    var originalLanguageName = "Original"

                    // handle dubs
                    val dubs = subjectData?.get("dubs")
                    if (dubs != null && dubs.isArray) {
                        for (dub in dubs) {
                            val dubId = dub["subjectId"]?.asText()
                            val lanName = dub["lanName"]?.asText()
                            if (dubId != null && lanName != null) {
                                if (dubId == id) {
                                    originalLanguageName = lanName
                                } else {
                                    subjectIds.add(Pair(dubId, lanName))
                                }
                            }
                        }
                    }
                    subjectIds.add(0, Pair(id, originalLanguageName))

                    for ((subjectId, language) in subjectIds) {
                        val playUrl =
                            "$movieBox/wefeed-mobile-bff/subject-api/play-info?subjectId=$subjectId&se=${season ?: 0}&ep=${episode ?: 0}"
                        val token = generateXClientToken()
                        val sign = generateXTrSignature(
                            "GET",
                            "application/json",
                            "application/json",
                            playUrl
                        )
                        val playHeaders = headers + mapOf("x-client-token" to token, "x-tr-signature" to sign)

                        val playRes = safeGet(playUrl, headers = playHeaders)
                        if (playRes.code != 200) continue

                        val playRoot = mapper.readTree(playRes.text)
                        val streams = playRoot["data"]?.get("streams") ?: continue
                        if (!streams.isArray) continue

                        for (stream in streams) {
                            val streamId = stream["id"]?.asText() ?: "$subjectId|$season|$episode"
                            //val subjectTitle = subjectData?.get("title")?.asText() ?: "Unknown Title"
                            val format = stream["format"]?.asText() ?: ""
                            val signCookie =
                                stream["signCookie"]?.asText()?.takeIf { it.isNotEmpty() }

                            val resolutionNodes = stream["resolutionList"] ?: stream["resolutions"]

                            if (resolutionNodes != null && resolutionNodes.isArray) {
                                for (resNode in resolutionNodes) {
                                    val resUrl = resNode["resourceLink"]?.asText() ?: continue
                                    val quality = resNode["resolution"]?.asInt() ?: 0

                                    callback.invoke(
                                        newExtractorLink(
                                            source = "MovieBox ${language.replace("dub", "Audio")}",
                                            name = "MovieBox (${language.replace("dub", "Audio")})",
                                            url = resUrl,
                                            type = when {
                                                resUrl.startsWith(
                                                    "magnet:",
                                                    true
                                                ) -> ExtractorLinkType.MAGNET

                                                resUrl.endsWith(
                                                    ".mpd",
                                                    true
                                                ) -> ExtractorLinkType.DASH

                                                resUrl.endsWith(
                                                    ".torrent",
                                                    true
                                                ) -> ExtractorLinkType.TORRENT

                                                format.equals(
                                                    "HLS",
                                                    true
                                                ) || resUrl.endsWith(
                                                    ".m3u8",
                                                    true
                                                ) -> ExtractorLinkType.M3U8

                                                else -> INFER_TYPE
                                            }
                                        ) {
                                            this.headers = mapOf("Referer" to movieBox) +
                                                    (if (signCookie != null) mapOf("Cookie" to signCookie) else emptyMap())
                                            this.quality = getQualityFromName("$quality")
                                        }
                                    )
                                    foundLinks = true
                                }
                            } else {
                                // fallback single url
                                val singleUrl = stream["url"]?.asText() ?: continue
                                val resText = stream["resolutions"]?.asText() ?: ""

                                callback.invoke(
                                    newExtractorLink(
                                        source = "MovieBox ${language.replace("dub", "Audio")}",
                                        name = "MovieBox (${language.replace("dub", "Audio")})",
                                        url = singleUrl,
                                        type = when {
                                            singleUrl.startsWith(
                                                "magnet:",
                                                true
                                            ) -> ExtractorLinkType.MAGNET

                                            singleUrl.endsWith(
                                                ".mpd",
                                                true
                                            ) -> ExtractorLinkType.DASH

                                            singleUrl.endsWith(
                                                ".torrent",
                                                true
                                            ) -> ExtractorLinkType.TORRENT

                                            format.equals(
                                                "HLS",
                                                true
                                            ) || singleUrl.endsWith(
                                                ".m3u8",
                                                true
                                            ) -> ExtractorLinkType.M3U8

                                            else -> INFER_TYPE
                                        }
                                    ) {
                                        this.headers = mapOf("Referer" to movieBox) +
                                                (if (signCookie != null) mapOf("Cookie" to signCookie) else emptyMap())
                                        this.quality = getQualityFromName(resText)
                                    }
                                )
                                foundLinks = true
                            }

                            // subtitles
                            val subLinks = listOf(
                                "$movieBox/wefeed-mobile-bff/subject-api/get-stream-captions?subjectId=$subjectId&streamId=$streamId",
                                "$movieBox/wefeed-mobile-bff/subject-api/get-ext-captions?subjectId=$subjectId&resourceId=$streamId&episode=${episode ?: 0}"
                            )

                            for (subLink in subLinks) {
                                val subToken = generateXClientToken()
                                val subSign = generateXTrSignature("GET", "", "", subLink)

                                val subHeaders = mapOf(
                                    "User-Agent" to "com.community.mbox.in/50020042 (Linux; U; Android 16; en_IN; sdk_gphone64_x86_64; Build/BP22.250325.006; Cronet/133.0.6876.3)",
                                    "Accept" to "",
                                    "Content-Type" to "",
                                    "X-Client-Info" to """{"package_name":"com.community.mbox.in","version_name":"3.0.03.0529.03","version_code":50020042,"os":"android","os_version":"16","device_id":"da2b99c821e6ea023e4be55b54d5f7d8","install_store":"ps","gaid":"d7578036d13336cc","brand":"google","model":"sdk_gphone64_x86_64","system_language":"en","net":"NETWORK_WIFI","sp_code":""}""",
                                    "X-Client-Status" to "0",
                                    "x-client-token" to subToken,
                                    "x-tr-signature" to subSign
                                )

                                val subRes = safeGet(subLink, headers = subHeaders)
                                if (subRes.code != 200) continue

                                val subRoot = mapper.readTree(subRes.text)
                                val captions = subRoot["data"]?.get("extCaptions")
                                if (captions != null && captions.isArray) {
                                    for (caption in captions) {
                                        val captionUrl = caption["url"]?.asText() ?: continue
                                        val lang = caption["language"]?.asText()
                                            ?: caption["lanName"]?.asText()
                                            ?: caption["lan"]?.asText()
                                            ?: "Unknown"
                                        subtitleCallback.invoke(
                                            newSubtitleFile(
                                                url = captionUrl,
                                                lang = "$lang (${language.replace("dub", "Audio")})"
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    return@safeAmap
                }
            }

            return foundLinks
        } catch (_: Exception) {
            return false
        }
    }

    suspend fun invokevidrock(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val type = if (season == null) "movie" else "tv"
        val encoded = vidrockEncode(tmdbId, type, season, episode)
        val response = safeGet("$vidrock/api/$type/$encoded").text
        val sourcesJson = JSONObject(response)

        val vidrockHeaders = mapOf(
            "Origin" to vidrock
        )

        sourcesJson.keys().asSequence().toList().safeAmap { key ->
            val sourceObj = sourcesJson.optJSONObject(key) ?: return@safeAmap

            val rawUrl = sourceObj.optString("url", "")
            val lang = sourceObj.optString("language", "Unknown")
            if (rawUrl.isNullOrBlank() || rawUrl == "null") return@safeAmap

            val safeUrl = if (rawUrl.contains("%")) {
                URLDecoder.decode(rawUrl, "UTF-8")
            } else rawUrl

            val displayName = "Vidrock [$key] $lang"

            when {
                safeUrl.contains("/playlist/") -> {
                    val playlistResponse = safeGet(safeUrl, headers = vidrockHeaders).text
                    val playlistArray = JSONArray(playlistResponse)

                    for (j in 0 until playlistArray.length()) {
                        val item = playlistArray.optJSONObject(j) ?: continue
                        val itemUrl = item.optString("url", "") ?: continue
                        val res = item.optInt("resolution", 0)

                        callback.invoke(
                            newExtractorLink(
                                source = "Vidrock-$key",
                                name = displayName,
                                url = itemUrl,
                                type = INFER_TYPE
                            ) {
                                this.headers = vidrockHeaders
                                this.quality = getQualityFromName("$res")
                            }
                        )
                    }
                }

                safeUrl.contains(".mp4", ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink(
                            source = "Vidrock-$key",
                            name = "$displayName MP4",
                            url = safeUrl,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.headers = vidrockHeaders
                        }
                    )
                }

                safeUrl.contains(".m3u8", ignoreCase = true) -> {
                    generateM3u8(
                        source = "Vidrock-$key",
                        streamUrl = safeUrl,
                        referer = "",
                        quality = Qualities.P1080.value,
                        headers = vidrockHeaders
                    ).forEach(callback)
                }

                else -> {
                    callback.invoke(
                        newExtractorLink(
                            source = "Vidrock-$key",
                            name = displayName,
                            url = safeUrl,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.headers = vidrockHeaders
                        }
                    )
                }
            }
        }
    }

    suspend fun invokeVidlink(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        if (tmdbId == null) return

        val encUrl = "https://enc-dec.app/api/enc-vidlink?text=$tmdbId"
        val encResponse = runCatching { app.get(encUrl).text }.getOrNull() ?: return

        val encData = runCatching {
            JSONObject(encResponse).optString("result")
        }.getOrNull().takeIf { !it.isNullOrEmpty() } ?: return

        val base = vidlink

        val headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Connection" to "keep-alive",
            "Referer" to "$base/",
            "Origin" to base
        )

        val apiUrl = if (season == null) {
            "$base/api/b/movie/$encData"
        } else {
            if (episode == null) return
            "$base/api/b/tv/$encData/$season/$episode"
        }

        val epResponse = runCatching {
            app.get(apiUrl, headers = headers).text
        }.getOrNull() ?: return

        val data = runCatching {
            Gson().fromJson(epResponse, VidlinkResponse::class.java)
        }.getOrNull() ?: return

        val stream = data.stream
        val m3u8 = stream.playlist

        val headersJson = Regex("""[?&]headers=([^&]+)""")
            .find(m3u8)?.groupValues?.get(1)
            ?.let { URLDecoder.decode(it, "UTF-8") }

        var referer = "$base/"
        var origin  = base

        if (!headersJson.isNullOrBlank()) {
            runCatching {
                val obj = Gson().fromJson(headersJson, JsonObject::class.java)
                obj["referer"]?.asString?.let { referer = it }
                obj["origin"]?.asString?.let  { origin  = it }
            }
        }
        val m3u8url = m3u8.substringBefore("?")
        headersJson?.toJson()?.let { Log.d("Phisher",m3u8url) }
        generateM3u8(
            "Vidlink",
            m3u8url,
            referer = referer,
            headers = mapOf(
                "Origin"  to vidlink,
                "Referer" to "$vidlink/"
            )
        ).forEach(callback)
    }

    //Need Fix Encrypted Links
    suspend fun invokeVidFast(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        if (tmdbId == null) return

        val api = "https://enc-dec.app/api"
        val version = "1"

        val requestUrl = if (season == null) {
            "$vidfastProApi/movie/$tmdbId"
        } else {
            "$vidfastProApi/tv/$tmdbId/$season/$episode"
        }

        val baseHeaders = mutableMapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
            "Referer" to "$vidfastProApi/",
            "X-Requested-With" to "XMLHttpRequest"
        )

        val pageText = runCatching {
            safeGet(requestUrl, headers = baseHeaders).text
        }.getOrNull() ?: return

        val encodedText = Regex("""\\"en\\":\\"(.*?)\\"""")
            .find(pageText)
            ?.groupValues
            ?.getOrNull(1) ?: return

        val encJson = runCatching {
            safeGet("$api/enc-vidfast?text=$encodedText&version=$version")
                .parsedSafe<VidFastRes>()
        }.getOrNull() ?: return

        val result = encJson.result
        val serversUrl = result.servers
        val streamBase = result.stream
        val token = result.token

        if (serversUrl.isBlank() || streamBase.isBlank()) return

        baseHeaders["X-CSRF-Token"] = token

        val serversEncrypted = runCatching {
            app.post(serversUrl, headers = baseHeaders).text
        }.getOrNull() ?: return

        if (serversEncrypted.isBlank()) return

        val serversRoot = runCatching {
            app.post(
                "$api/dec-vidfast",
                json = mapOf("text" to serversEncrypted, "version" to version)
            ).parsedSafe<VidFastServers>()
        }.getOrNull() ?: return

        val serversList = serversRoot.result

        if (serversList.isEmpty()) return

        val quality = Qualities.P1080.value

        for ((index, server) in serversList.withIndex()) {

            val name = server.name.ifBlank { "Server ${index + 1}" }
            val data = server.data
            if (data.isBlank()) continue

            val streamUrl = "$streamBase/$data"
            val streamEncrypted = runCatching {
                app.post(streamUrl, headers = baseHeaders).text
            }.getOrNull()

            if (streamEncrypted.isNullOrBlank()) {
                continue
            }

            val streamRoot = runCatching {
                app.post(
                    "$api/dec-vidfast",
                    json = mapOf("text" to streamEncrypted, "version" to version)
                ).parsedSafe<VidFastServersStreamRoot>()
            }.getOrNull() ?: continue
            Log.d("Phisher",streamRoot.toString())
            val finalUrl = streamRoot.result.url
            if (finalUrl.isNullOrBlank()) continue

            val subtitles = mutableListOf<SubtitleFile>()
            val seen = mutableSetOf<String>()

            streamRoot.result.tracks?.forEach { track ->
                val file = track.file
                val label = track.label
                if (!file.isNullOrBlank() && !label.isNullOrBlank() && seen.add(file)) {
                    subtitles.add(newSubtitleFile(label, file))
                }
            }

            callback(
                newExtractorLink(
                    "VidFastPro",
                    "VidFastPro [$name]",
                    finalUrl
                ) {
                    this.referer = "$vidfastProApi/"
                    this.quality = quality
                }
            )
        }
    }

    suspend fun invokeMoviesApi(
        id: Int?,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        if (id == null) return

        val href =
            if (season == null) "$moviesClubApi/movie/$id" else "$moviesClubApi/tv/$id-$season-$episode"
        val pageDoc = runCatching { safeGet(href).document }.getOrNull() ?: return
        val iframeElement = pageDoc.selectFirst("iframe[src], iframe[data-src]") ?: return
        val iframeSrc = iframeElement.attr("src").ifEmpty { iframeElement.attr("data-src") }
        if (iframeSrc.isEmpty()) return
        val iframeDoc = runCatching { safeGet(iframeSrc).document }.getOrNull() ?: return
        val scriptData = iframeDoc.select("script")
            .firstOrNull { e ->
                val d = e.data()
                d.contains("function(p,a,c,k,e,d)")
            }?.data() ?: iframeDoc.selectFirst("script")?.data() ?: return
        val unPacked = runCatching { getAndUnpack(scriptData) }.getOrNull() ?: scriptData
        val m3u8 = Regex("sources:\\[\\{file:\"(.*?)\"").find(unPacked)?.groupValues?.get(1)

        if (m3u8 != null) {
            generateM3u8(
                "MoviesApi Club",
                m3u8,
                iframeSrc,
                headers = mapOf("Referer" to iframeSrc)
            ).forEach(callback)
        }
    }

    suspend fun invokecinemacity(
        imdbId: String?,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        if (imdbId.isNullOrBlank()) return

        val headers = mapOf(
            "Cookie" to base64Decode("ZGxlX3VzZXJfaWQ9MzI3Mjk7IGRsZV9wYXNzd29yZD04OTQxNzFjNmE4ZGFiMThlZTU5NGQ1YzY1MjAwOWEzNTs=")
        )

        val searchUrl = "$cinemacity/?do=search&subaction=search&search_start=0&full_search=0&story=$imdbId"

        val pageUrl = safeGet(searchUrl, headers, interceptor = CloudflareKiller())
            .document
            .selectFirst("div.dar-short_item > a")
            ?.attr("href")
            ?: return

        val script = safeGet(pageUrl, headers)
            .document
            .select("script:containsData(atob)")
            .getOrNull(1)
            ?.data()
            ?: return

        val playerJson = JSONObject(
            base64Decode(
                script.substringAfter("atob(\"").substringBefore("\")")
            ).substringAfter("new Playerjs(").substringBeforeLast(");")
        )


        val fileArray = JSONArray(playerJson.getString("file"))

        fun extractQuality(url: String): Int {
            return when {
                url.contains("2160p") -> Qualities.P2160.value
                url.contains("1440p") -> Qualities.P1440.value
                url.contains("1080p") -> Qualities.P1080.value
                url.contains("720p") -> Qualities.P720.value
                url.contains("480p") -> Qualities.P480.value
                url.contains("360p") -> Qualities.P360.value
                else -> Qualities.Unknown.value
            }
        }

        val subtitleTracks = cinemacityparseSubtitles(
            playerJson.optString("subtitle")
        )

        suspend fun emitExtractorLinks(files: String,seasonNum: Int? = null, episodeNum: Int? = null) {

            callback.invoke(
                newExtractorLink(
                    "CineCity",
                    "CineCity Multi-Audio",
                    files,
                    INFER_TYPE
                ) {
                    referer = pageUrl
                    quality = extractQuality(files)
                }
            )

            val parts = files.split(",")
            val audioFiles = parts.filter { it.endsWith(".m4a") }

            audioFiles.forEachIndexed { index, _ ->

                val downloads = cinemacitybuildDownloadLinks(
                    base = files,
                    subtitles = subtitleTracks,
                    selectedAudioIndex = index,
                    title = "CineCity",
                    season = seasonNum,
                    episode = episodeNum
                )

                downloads.forEach { (dlUrl, quality, lang) ->

                    callback.invoke(
                        newExtractorLink(
                            "CineCity",
                            "CineCity • $lang • Download",
                            dlUrl,
                            INFER_TYPE
                        ) {
                            referer = pageUrl
                            this.quality = quality
                        }
                    )
                }
            }
        }

        val first = fileArray.getJSONObject(0)

        // MOVIE
        if (!first.has("folder")) {
            emitExtractorLinks(
                files = first.getString("file"),
                seasonNum = null,
                episodeNum = null
            )
            return
        }

        // SERIES
        for (i in 0 until fileArray.length()) {
            val seasonJson = fileArray.getJSONObject(i)

            val seasonNumber = Regex("Season\\s*(\\d+)", RegexOption.IGNORE_CASE)
                .find(seasonJson.optString("title"))
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?: continue

            if (season != null && seasonNumber != season) continue

            val episodes = seasonJson.getJSONArray("folder")
            for (j in 0 until episodes.length()) {
                val epJson = episodes.getJSONObject(j)

                val episodeNumber = Regex("Episode\\s*(\\d+)", RegexOption.IGNORE_CASE)
                    .find(epJson.optString("title"))
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
                    ?: continue

                if (episode != null && episodeNumber != episode) continue

                emitExtractorLinks(
                    files = epJson.getString("file"),
                    seasonNum = seasonNumber,
                    episodeNum = episodeNumber
                )
            }
        }
    }

    suspend fun invokeHexa(
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        callback: (ExtractorLink) -> Unit
    ) {
        val key = generateHexKey32()

        val baseHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
            "Referer" to "https://hexa.su/",
            "Accept" to "text/plain",
            "X-Fingerprint-Lite" to "e9136c41504646444",
            "X-Api-Key" to key
        )

        val apiBase = "https://enc-dec.app/api"

        val token = safeGet("$apiBase/enc-hexa", headers = baseHeaders).parsedSafe<HexaEn>()?.result?.token ?: return

        val headers = baseHeaders + mapOf(
            "X-Cap-Token" to token
        )

        val url = if (season == null) {
            "$hexaSU/api/tmdb/movie/$tmdbId/images"
        } else {
            "$hexaSU/api/tmdb/tv/$tmdbId/season/$season/episode/$episode/images"
        }

        val encrypted = safeGet(url, headers).text
        if (encrypted.isEmpty()) return

        val jsonBody = """
        {
            "text": "$encrypted",
            "key": "$key"
        }
    """.trimIndent().toRequestBody("application/json".toMediaType())

        val decryptRes = app.post(
            "$apiBase/dec-hexa",
            headers = mapOf("Content-Type" to "application/json"),
            requestBody = jsonBody
        ).parsedSafe<HexaResponse>() ?: return

        if (decryptRes.status != 200) return

        val sources = decryptRes.result?.sources ?: return

        for (src in sources) {
            val server = src.server ?: continue
            val link = src.url ?: continue

            if (link.isEmpty()) continue

            val name = server.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }

            generateM3u8(
                "HexaSU $name",
                link,
                "https://hexa.su/"
            ).forEach(callback)
        }
    }

    suspend fun invokeHindmoviez(
        id: String?,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val api = getDomains()?.hindmoviez ?: return
        if (id.isNullOrBlank()) return
        val url = "$api/?s=$id"
        var response = safeGet(url, timeout = 5000L)
        if (response.text.contains("Just a moment", true)) {
            response = webMutex.withLock {
                safeGet(
                    url,
                    timeout = 5000L,
                    interceptor = cloudflareKiller
                )
            }
        }

        val searchDoc = response.document
        val entries = searchDoc.select("h2.entry-title > a")

        entries.safeAmap { entry ->
            val pageDoc = safeGet(entry.attr("href"), timeout = 5000L).document
            val buttons = pageDoc.select("a.maxbutton")

            if (episode == null) {
                // Movie
                buttons.safeAmap { btn ->
                    val intermediateDoc = safeGet(btn.attr("href"), timeout = 5000L).document
                    val link = intermediateDoc.selectFirst("a.get-link-btn")
                        ?.attr("href")
                        ?.takeIf { it.isNotBlank() }
                        ?.let { href ->
                            val baseurl=href.substringBefore("/?id=")
                            val rawId = href.substringAfter("id=")
                            hindmoviezsignHShare(rawId, baseurl)
                        }
                        ?: return@safeAmap
                    Log.d("Phisher 1",link)
                    getHindMoviezLinks("Hindmoviez", link, subtitleCallback, callback)
                }
            } else {
                // TV Episode
                buttons.safeAmap { btn ->
                    val headerText = btn.parent()
                        ?.parent()
                        ?.previousElementSibling()
                        ?.text()
                        .orEmpty()

                    if (!headerText.contains("Season $season", ignoreCase = true)) return@safeAmap

                    val episodeDoc = safeGet(btn.attr("href"), timeout = 5000L).document
                    val episodeLink = episodeDoc
                        .select("h3 > a")
                        .getOrNull(episode - 1)
                        ?.attr("href")
                        ?.takeIf { it.isNotBlank() }
                        ?.let { href ->
                            val baseurl = href.substringBefore("/?id=")
                            val rawId = href.substringAfter("id=")
                            hindmoviezsignHShare(rawId, baseurl)
                        }
                        ?: return@safeAmap

                    getHindMoviezLinks("Hindmoviez", episodeLink, subtitleCallback, callback)
                }
            }
        }
    }

    suspend fun invokeMovies4u(
        id: String? = null,
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val searchQuery = "$title $year".trim()
        val movies4uAPI = getDomains()?.movies4u ?: return
        val searchUrl = "$movies4uAPI/?s=$searchQuery"

        val searchDoc = safeGet(searchUrl).document
        val links = searchDoc.select("article h2 a,article h3 a")

        links.safeAmap { element ->
            val postUrl = element.attr("href")
            val postDoc = safeGet(postUrl).document
            val imdbId = postDoc.select("p a:contains(IMDb Rating)").attr("href")
                .substringAfter("title/").substringBefore("/")

            if (imdbId != id.toString()) {
                return@safeAmap
            }

            if (season == null) {
                val innerUrl = postDoc.select("div.download-links-div a.btn").attr("href")
                val innerDoc = safeGet(innerUrl).document
                val sourceButtons = innerDoc.select("div.downloads-btns-div a.btn")
                sourceButtons.safeAmap { sourceButton ->
                    val sourceLink = sourceButton.attr("href")
                    loadSourceNameExtractor(
                        "Movies4u",
                        sourceLink,
                        "",
                        subtitleCallback,
                        callback
                    )
                }
            } else {
                val seasonBlocks = postDoc.select("div.downloads-btns-div")
                seasonBlocks.safeAmap { block ->
                    val headerText = block.previousElementSibling()?.text().orEmpty()
                    if (headerText.contains("Season $season", ignoreCase = true)) {
                        val seasonLink = block.select("a.btn").firstOrNull { !it.text().contains("zip", true) }?.attr("href") ?: return@safeAmap

                        val episodeDoc = safeGet(seasonLink).document
                        val episodeBlocks = episodeDoc.select("div.downloads-btns-div")

                        if (episode != null && episode in 1..episodeBlocks.size) {
                            val episodeBlock = episodeBlocks[episode - 1]
                            val episodeLinks = episodeBlock.select("a.btn")

                            episodeLinks.safeAmap { epLink ->
                                val sourceLink = epLink.attr("href")
                                loadSourceNameExtractor(
                                    "Movies4u",
                                    sourceLink,
                                    "",
                                    subtitleCallback,
                                    callback
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun invokeM4uhd(
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val m4uhdAPI = getDomains()?.m4ufree ?: return

        try {
            val safeTitle = title?.fixTitle() ?: return

            val res = safeGet(
                "$m4uhdAPI/search/$safeTitle",
                timeout = 5000L
            ).document

            val scriptData = res.select("div.item").mapNotNull { element ->
                val href = element.selectFirst("a")?.attr("href")
                if (href.isNullOrBlank()) return@mapNotNull null

                Triple(
                    element.selectFirst("img.imagecover")?.attr("alt"),
                    element.selectFirst(".jt-info")?.text(),
                    href
                )
            }

            if (scriptData.isEmpty()) {
                Log.e("M4uhd", "No search results")
                return
            }

            val cleanTitle = title
                .lowercase()
                .replace(Regex("[^a-z0-9]"), "")

            val script = scriptData.firstOrNull { triple ->

                val siteTitle = triple.first
                    ?.lowercase()
                    ?.replace(Regex("[^a-z0-9]"), "")
                    ?: return@firstOrNull false

                siteTitle.contains(cleanTitle)

            } ?: scriptData.firstOrNull()

            val scriptUrl = script?.third
            if (scriptUrl.isNullOrBlank()) {
                Log.e("M4uhd", "Script URL null")
                return
            }

            val link = fixUrl(scriptUrl, m4uhdAPI)

            val request = safeGet(link)
            val doc = request.document

            // direct iframe
            val directIframe = doc
                .selectFirst("#myplayer iframe")
                ?.attr("src")

            if (!directIframe.isNullOrBlank()) {
                loadExtractor(
                    fixUrl(directIframe, link),
                    m4uhdAPI,
                    subtitleCallback,
                    callback
                )
                return
            }

            val cookies = request.cookies

            val token = doc
                .selectFirst("meta[name=csrf-token]")
                ?.attr("content")

            if (token.isNullOrBlank()) {
                Log.e("M4uhd", "Token missing")
                return
            }

            val m4uData = if (season == null) {

                doc.selectFirst("span.singlemv.active, span#fem")
                    ?.attr("data")

            } else {

                val epCode = "S%02d-E%02d".format(season, episode ?: return)

                val episodeBtn = doc.select("button.episode")
                    .firstOrNull {
                        it.text().trim().equals(epCode, true)
                    } ?: return

                val idepisode = episodeBtn.attr("idepisode")

                if (idepisode.isBlank()) return

                val embed = app.post(
                    "$m4uhdAPI/ajaxtv",
                    data = mapOf(
                        "idepisode" to idepisode,
                        "_token" to token
                    ),
                    referer = link,
                    headers = mapOf(
                        "X-Requested-With" to "XMLHttpRequest"
                    ),
                    cookies = cookies
                ).document

                embed.selectFirst("span.singlemv.active, span#fem")
                    ?.attr("data")
            }

            if (m4uData.isNullOrBlank()) {
                Log.e("M4uhd", "m4uData missing")
                return
            }

            val iframe = app.post(
                "$m4uhdAPI/ajax",
                data = mapOf(
                    "m4u" to m4uData,
                    "_token" to token
                ),
                referer = link,
                headers = mapOf(
                    "X-Requested-With" to "XMLHttpRequest"
                ),
                cookies = cookies
            ).document
                .selectFirst("iframe")
                ?.attr("src")

            if (iframe.isNullOrBlank()) {
                Log.e("M4uhd", "iframe missing")
                return
            }

            loadSourceNameExtractor(
                "M4uhd",
                fixUrl(iframe, link),
                m4uhdAPI,
                subtitleCallback,
                callback
            )

        } catch (_: Exception) {
            Log.e("M4uhd", "invokeM4uhd crash")
        }
    }

    suspend fun invokeCineVood(
        imdbId: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val baseUrl = getDomains()?.cinevood ?: return
        if (imdbId == null) return

        val searchRes = safeGet("$baseUrl/?s=$imdbId")

        val searchDoc = if (searchRes.text.contains("Just a moment", true)) {
            webMutex.withLock {
                safeGet("$baseUrl/?s=$imdbId", interceptor = cloudflareKiller)
            }.document
        } else {
            searchRes.document
        }

        searchDoc.select("article a[href]")
            .mapNotNull { it.attr("href").takeIf(String::isNotBlank) }
            .distinct()
            .safeAmap { postUrl ->

                val postRes = safeGet(postUrl)
                val postDoc = if (postRes.text.contains("Just a moment", true)) {
                    webMutex.withLock {
                        safeGet(postUrl, interceptor = cloudflareKiller)
                    }.document
                } else {
                    postRes.document
                }

                postDoc.select("a.maxbutton[href]")
                    .mapNotNull { it.attr("href").takeIf(String::isNotBlank) }
                    .safeAmap { link ->
                        loadSourceNameExtractor("CineVood", link, "", subtitleCallback, callback)
                    }
            }
    }


    suspend fun invokeFilmyfiy(
        title: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {

        fun cleanTitle(input: String): String {
            return input
                .lowercase()
                .replace(Regex("\\(.*?\\)"), "") // remove (2012), (Hindi...)
                .replace(Regex("[^a-z0-9 ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
        val baseUrl = getDomains()?.filmyfiy ?: return
        val rawQuery = title?.trim() ?: return
        val query = rawQuery.lowercase().replace(Regex("[^a-z0-9]"), "")
        val searchDoc = safeGet(
            "$baseUrl/site-1.html?to-search=${
                withContext(Dispatchers.IO) {
                    URLEncoder.encode(
                        rawQuery,
                        "UTF-8"
                    )
                }
            }"
        ).document

        val queryClean = cleanTitle(rawQuery)

        searchDoc.select("div.A2 > a:nth-child(2)[href]").mapNotNull { a ->
            val href = a.attr("href").takeIf(String::isNotBlank)
                ?.let { if (it.startsWith("http")) it else baseUrl + it }
                ?: return@mapNotNull null

            val titleText = cleanTitle(a.text())

            val isMatch = titleText == queryClean ||
                    titleText.startsWith("$queryClean ") ||
                    titleText.contains(" $queryClean ")

            if (isMatch) href else null
        }.distinct().safeAmap { postUrl ->
            val postDoc = safeGet(postUrl).document
            postDoc.select("div.dlbtn a[href]")
                .mapNotNull { it.absUrl("href").takeIf(String::isNotBlank) }.distinct()
                .safeAmap { dlBtnUrl ->
                    val dlDoc = safeGet(dlBtnUrl).document
                    dlDoc.select("div.dlink a[href]")
                        .mapNotNull { it.absUrl("href").takeIf(String::isNotBlank) }.distinct()
                        .safeAmap { finalUrl ->
                            loadExtractor(finalUrl, baseUrl, subtitleCallback, callback)
                        }
                }
        }
    }

    suspend fun invokeDooflix(
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        callback: (ExtractorLink) -> Unit
    ) {
        val baseApi = "https://panel.watchkaroabhi.com"
        val apiKey = "qNhKLJiZVyoKdi9NCQGz8CIGrpUijujE"

        if (tmdbId == null) return

        val requestUrl = if (season == null) {
            "$baseApi/api/3/movie/$tmdbId/links?api_key=$apiKey"
        } else {
            "$baseApi/api/3/tv/$tmdbId/season/$season/episode/$episode/links?api_key=$apiKey"
        }

        val headers = mapOf(
            base64Decode("WC1QYWNrYWdlLU5hbWU=") to base64Decode("Y29tLmtpbmcubW9qYQ=="),
            base64Decode("VXNlci1BZ2VudA==") to base64Decode("ZG9vZmxpeA=="),
            base64Decode("WC1BcHAtVmVyc2lvbg==") to base64Decode("MzA1")
        )

        val response = safeGet(requestUrl, headers).parsedSafe<Dooflix>() ?: return
        val links = response.links

        links.safeAmap { link ->
            val streamurl = safeGet(
                link.url,
                referer = "https://molop.art/",
                allowRedirects = false
            ).headers["location"] ?: return@safeAmap
            callback.invoke(
                newExtractorLink(
                    "Dooflix",
                    link.host,
                    streamurl
                )
                {
                    this.referer = "https://molop.art/"
                    this.quality = Qualities.P1080.value
                }
            )
        }
    }

    private fun normalizeTitleFast(title: String?): String {
        if (title == null) return ""
        val sb = StringBuilder(title.length)
        for (c in title.lowercase()) {
            if (c.isLetterOrDigit() || c == ' ') sb.append(c)
        }
        return sb.toString().trim()
    }

    suspend fun invokeXpass(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val baseRef = "$xpassAPI/"
        val embedUrl = if (season == null) {
            "$xpassAPI/e/movie/$tmdbId"
        } else {
            "$xpassAPI/e/tv/$tmdbId/$season/$episode"
        }

        val html = app.get(embedUrl, referer = baseRef).text
        val backups = extractXpassBackups(html)

        Log.d("Xpass", "backups: $backups")

        backups.safeAmap { (name, url) ->
            val fullUrl = if (url.startsWith("http")) url else xpassAPI + url
            Log.d("Xpass", "fullUrl: $fullUrl")

            val json = runCatching { app.get(fullUrl).text }.getOrNull() ?: return@safeAmap

            val sources = runCatching {
                JSONObject(json)
                    .optJSONArray("playlist")
                    ?.optJSONObject(0)
                    ?.optJSONArray("sources")
            }.getOrNull() ?: return@safeAmap

            val sourceCount = sources.length()

            for (i in 0 until sourceCount) {
                val source = sources.optJSONObject(i) ?: continue

                val file = source.optString("file").takeIf {
                    it.isNotBlank() && it.startsWith("http")
                } ?: continue

                val type = source.optString("type")
                val isM3u8 = type.contains("hls", true) || file.contains(".m3u8")

                if (isM3u8) {
                    generateM3u8(
                        "Xpass [$name]",
                        file,
                        baseRef
                    ).forEach(callback)
                } else {
                    callback(
                        newExtractorLink(
                            "Xpass [$name]",
                            "Xpass [$name]",
                            file
                        ) {
                            this.referer = baseRef
                        }
                    )
                }
            }
        }
    }

    suspend fun invokevaplayer(
        tmdb: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (season == null) {
            "$vaplayer/api.php?tmdb=$tmdb&type=movie"
        } else {
            "$vaplayer/api.php?tmdb=$tmdb&type=tv&season=$season&episode=$episode"
        }

        val refer = "https://nextgencloudfabric.com/"

        val response = app.get(url, referer = refer)
            .parsedSafe<Vaplayer>() ?: return

        val streamUrls = response.data?.streamUrls ?: return

        response.defaultSubs?.forEach { sub ->
            subtitleCallback.invoke(
                newSubtitleFile(
                    sub.lang ?: sub.code ?: "Unknown",
                    sub.url ?: return@forEach
                )
            )
        }

        streamUrls.forEachIndexed { index, streamUrl ->
            generateM3u8(
                "Vaplayer Server ${index + 1}",
                streamUrl,
                refer
            ).forEach(callback)
        }
    }

    suspend fun invokeDudefilms(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val dudefilmsAPI = getDomains()?.dudefilms ?: return
        if(imdbId == null) return
        val urls = app.get("$dudefilmsAPI/?s=$imdbId").document.select("a.simple-grid-grid-post-thumbnail-link")

        urls.safeAmap { it ->
            val url = it.attr("href")
            val doc = app.get(url).document

            if(season == null && episode == null) {
                doc.select("a.maxbutton").safeAmap { link ->
                    val href = link.attr("href")
                    val document = app.get(href).document
                    document.select("a.maxbutton").safeAmap { source ->
                        loadSourceNameExtractor("Dudefilms", source.attr("href"), "", subtitleCallback, callback)
                    }
                }
            } else {
                val matchingH4Tags = doc.select("h4").filter {
                    Regex("""Season\s*0*$season\b""", RegexOption.IGNORE_CASE).containsMatchIn(it.text())
                }

                if(matchingH4Tags.isEmpty()) return@safeAmap

                matchingH4Tags.safeAmap { h4Tag ->
                    var currentSibling = h4Tag.nextElementSibling()
                    while (currentSibling != null) {
                        val tagName = currentSibling.tagName()

                        if(tagName != "p") return@safeAmap

                        currentSibling.select("a").safeAmap{ aTag ->
                            val source = aTag.attr("href")
                            Log.d("Dudefilms", "source: $source")
                            val epSource = app.get(source).document
                                .select("a.maxbutton")
                                .find { Regex("""(?:Episode|Ep|E)\s*(\d+)""", RegexOption.IGNORE_CASE).find(it.text())?.groupValues?.getOrNull(1)?.toIntOrNull() == episode }
                                ?.attr("href") ?: return@safeAmap

                            loadSourceNameExtractor("Dudefilms", epSource, "", subtitleCallback, callback)
                        }

                        currentSibling = currentSibling.nextElementSibling()

                    }
                }
            }
        }
    }

    suspend fun invokeZinkmovies(
        title: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val domain = getDomains()?.zinkmovies ?: return
        val searchDoc = app.get("$domain/?s=${title} $year").document
        val typeSpan = if (season != null) "span.tvshows" else "span.movies"

        val matchUrls = searchDoc.select("div.result-item article")
            .filter { article ->
                article.selectFirst(typeSpan) != null &&
                        article.selectFirst("div.title a")
                            ?.text()
                            ?.replace(":", "")
                            ?.replace("-", " ")
                            ?.replace(Regex("\\s+"), " ")
                            ?.trim()
                            ?.contains(
                                title
                                    ?.replace(":", "")
                                    ?.replace("-", " ")
                                    ?.replace(Regex("\\s+"), " ")
                                    ?.trim() ?: "",
                                ignoreCase = true
                            ) == true &&
                        (
                                year == null ||
                                        article.selectFirst("span.year")
                                            ?.text()
                                            ?.trim() == year.toString()
                                )
            }
            .mapNotNull {
                it.selectFirst("div.title a")?.attr("href")
            }.distinct()

        if (matchUrls.isEmpty()) return

        matchUrls.safeAmap { matchUrl ->
            val detailDoc = app.get(matchUrl).document
            val content = detailDoc.selectFirst("div.wp-content") ?: return@safeAmap

            if (season != null && episode != null) {
                extractSeasonLinks(content, season).safeAmap { seasonBtnUrl ->

                    val episodeDoc = app.get(seasonBtnUrl).document
                    val episodeUrl = episodeDoc.select("a.maxbutton-download-now")
                        .firstOrNull { a ->
                            Regex("""EPISODE\s*-\s*0*(\d+)""", RegexOption.IGNORE_CASE)
                                .find(a.text())?.groupValues?.get(1)?.toIntOrNull() == episode
                        }?.attr("href") ?: return@safeAmap

                    getZinkLinks(episodeUrl, subtitleCallback, callback)
                }
            } else {
                content.select("div.movie-button-container a.movie-simple-button")
                    .mapNotNull { it.attr("href").takeIf(String::isNotBlank) }
                    .safeAmap {
                        getZinkLinks(it, subtitleCallback, callback)
                    }
            }
        }
    }


    fun extractSeasonLinks(content: Element, season: Int): List<String> {
        val links = mutableListOf<String>()
        var inTargetSeason = false
        content.children().forEach { child ->
            when {
                child.hasClass("lgtagmessage") -> {
                    inTargetSeason = Regex("""Season\s+0*$season\b""", RegexOption.IGNORE_CASE)
                        .containsMatchIn(child.text())
                }
                child.hasClass("movie-button-container") && inTargetSeason -> {
                    child.selectFirst("a.movie-simple-button")
                        ?.attr("href")
                        ?.takeIf(String::isNotBlank)
                        ?.let { links.add(it) }
                }
            }
        }
        return links
    }

    suspend fun generateZinkLinks(url: String): List<ZinkLink> {
        return runCatching {

            val firstDoc = app.get(url).document
            val title = firstDoc.select("h1.file-title").text()
            val firstHtml = firstDoc.html()

            val randomId = Regex("""generateDownloadLink\(['"]([^'"]+)""")
                .find(firstHtml)
                ?.groupValues
                ?.getOrNull(1)
                ?: return emptyList()

            val ajaxEndpoint = Regex("""https://[^"'\\s]+ajax_generate_token\.php""")
                .find(firstHtml)
                ?.value
                ?: return emptyList()

            val downloadBase = Regex("""https://[^"'\\s]+/dl/""")
                .find(firstHtml)
                ?.value
                ?: return emptyList()

            val token = retry  { app.post(
                url = "$ajaxEndpoint?random_id=$randomId",
                data = mapOf(
                    "random_id" to randomId
                ),
                headers = mapOf(
                    "X-Requested-With" to "XMLHttpRequest"
                )
            ).parsedSafe<ZinkTokenResponse>()
                ?.token

            } ?: return emptyList()

            val generatedUrl = downloadBase + token

            val generatedDoc = app.get(generatedUrl).document

            val results = generatedDoc
                .select("#mirror-buttons a[href]")
                .mapNotNull { element ->

                    val href = element.attr("href").trim()

                    if (href.isBlank()) return@mapNotNull null

                    ZinkLink(
                        name = element.text()
                            .replace("Generate", "", true)
                            .trim(),
                        url = href,
                        title = title,
                    )
                }
                .toMutableList()

            generatedDoc.selectFirst("#worker-btn")?.let { btn: Element ->

                val workerId = Regex("""handleServerRequest\(['"]worker['"]\s*,\s*['"]([^'"]+)""")
                    .find(btn.attr("onclick"))
                    ?.groupValues
                    ?.getOrNull(1)

                val serverHandler = Regex("""SERVER_HANDLER_URL\s*=\s*["']([^"']+)""")
                    .find(generatedDoc.html())
                    ?.groupValues
                    ?.getOrNull(1)

                if (
                    !workerId.isNullOrBlank() &&
                    !serverHandler.isNullOrBlank()
                ) {

                    runCatching {

                        val workerJson = JSONObject(
                            app.post(
                                url = serverHandler,
                                requestBody = """
                                {
                                    "server":"worker",
                                    "random_id":"$workerId"
                                }
                            """.trimIndent().toRequestBody(),
                                headers = mapOf(
                                    "X-Requested-With" to "XMLHttpRequest",
                                    "Content-Type" to "application/json",
                                    "Origin" to generatedUrl.substringBefore("/dl/"),
                                    "Referer" to generatedUrl
                                )
                            ).text
                        )

                        workerJson.optString("url")
                            .ifBlank {
                                workerJson.optString("download")
                            }
                            .takeIf { it.isNotBlank() }
                            ?.let {
                                results += ZinkLink(
                                    name = "WORKER",
                                    url = it,
                                    title = title,
                                )
                            }

                    }
                }
            }

            results.distinctBy { it.url }

        }.getOrElse {
            emptyList()
        }
    }


    suspend fun getZinkLinks(
        source: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        generateZinkLinks(source).safeAmap { link ->

            val simplifiedTitle = cleanTitle(link.title)

            if (link.name.contains("worker", true)) {
                callback(
                    newExtractorLink(
                        source = "Zinkmovies Worker",
                        name = "Zinkmovies Worker $simplifiedTitle",
                        url = link.url
                    ) {
                        this.quality = getIndexQuality(link.title)
                    }
                )
            } else {
                loadSourceNameExtractor(
                    "Zinkmovies",
                    link.url,
                    "",
                    subtitleCallback,
                    callback
                )
            }
        }
    }

    suspend fun invokePeachify(
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {

        data class ProxyData(
            val url: String,
            val headers: Map<String, String>
        )

        val requestHeaders = mapOf(
            "Accept"          to "*/*",
            "Accept-Language" to "en-US,en;q=0.5",
            "Origin"          to peachifyAPI,
            "Referer"         to "$peachifyAPI/",
            "Sec-Fetch-Dest"  to "empty",
            "Sec-Fetch-Mode"  to "cors",
            "Sec-Fetch-Site"  to "cross-site",
            "User-Agent"      to "Mozilla/5.0 (X11; Linux x86_64; rv:139.0) Gecko/20100101 Firefox/139.0"
        )

        val servers = listOf(
            "https://usa.eat-peach.sbs/holly",
            "https://usa.eat-peach.sbs/multi",
            "https://usa.eat-peach.sbs/ice",
            "https://usa.eat-peach.sbs/air",
            "https://usa.eat-peach.sbs/net",
            "https://uwu.peachify.top/moviebox"
        )

        servers.safeAmap { server ->

            val apiUrl = if (season == null) {
                "$server/movie/$tmdbId"
            } else {
                "$server/tv/$tmdbId/$season/$episode"
            }

            val text = app.get(
                apiUrl,
                headers = requestHeaders
            ).text

            val encrypt = JSONObject(text)
                .optString("data")
                .ifEmpty { return@safeAmap }

            val decrypted = peachifyDecrypt(encrypt)
                ?: return@safeAmap

            val json = JSONObject(decrypted)

            val provider = json.optString(
                "providerName",
                "Peachify"
            )

            val sources = json.optJSONArray("sources")
                ?: return@safeAmap

            for (i in 0 until sources.length()) {

                val src = sources.getJSONObject(i)

                val rawUrl = src
                    .optString("url")
                    .ifEmpty { continue }

                val dub = src.optString("dub", "")

                val srcType = src.optString(
                    "type",
                    "hls"
                )

                val quality = src.optInt(
                    "quality",
                    0
                )

                val srcHeaders = src.optJSONObject("headers")

                val isProxy =
                    rawUrl.contains("/m3u8-proxy") ||
                            rawUrl.contains("/mp4-proxy")

                val proxyData = if (isProxy) {

                    val query = rawUrl
                        .substringAfter("?", "")
                        .split("&")
                        .mapNotNull { param ->

                            val parts = param.split("=", limit = 2)

                            if (parts.size != 2) {
                                return@mapNotNull null
                            }

                            val key = runCatching {
                                URLDecoder.decode(parts[0], "UTF-8")
                            }.getOrNull() ?: return@mapNotNull null

                            val value = runCatching {
                                URLDecoder.decode(parts[1], "UTF-8")
                            }.getOrNull() ?: return@mapNotNull null

                            key to value
                        }
                        .toMap()

                    val finalUrl = query["url"] ?: rawUrl

                    val proxyHeaders = query["headers"]
                        ?.let { headersJson ->

                            runCatching {
                                tryParseJson<Map<String, String>>(headersJson)
                            }.getOrNull()

                        }
                        ?: emptyMap()

                    ProxyData(
                        url = finalUrl,
                        headers = proxyHeaders
                    )

                } else {

                    ProxyData(
                        url = rawUrl,
                        headers = buildMap {

                            srcHeaders?.keys()?.forEach { key ->

                                put(
                                    key,
                                    srcHeaders.optString(key)
                                )
                            }
                        }
                    )
                }

                val finalUrl = proxyData.url
                val proxyHeaders = proxyData.headers

                val finalReferer =
                    proxyHeaders["referer"]
                        ?: srcHeaders?.optString("referer")
                        ?: "$peachifyAPI/"

                val finalOrigin =
                    proxyHeaders["origin"]
                        ?: srcHeaders?.optString("origin")
                        ?: peachifyAPI

                val finalUA =
                    proxyHeaders["user-agent"]
                        ?: srcHeaders?.optString("user-agent")
                        ?: USER_AGENT

                val name = buildString {

                    append(
                        "Peachify [${provider.capitalize()}]"
                    )

                    if (dub.isNotEmpty()) {
                        append(" • $dub")
                    }
                }

                val type = if (srcType == "hls") {
                    ExtractorLinkType.M3U8
                } else {
                    INFER_TYPE
                }

                callback.invoke(
                    newExtractorLink(
                        source = "Peachify",
                        name = name,
                        url = finalUrl,
                        type = type
                    ) {

                        this.headers = mapOf(
                            "Origin"     to finalOrigin,
                            "Referer"    to finalReferer,
                            "User-Agent" to finalUA
                        )

                        this.quality = quality
                    }
                )
            }
        }
    }
    suspend fun invokeAnikage(
        anilistId: Int?,
        query: String?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        dubtype: String?,
    ) {
        if (query.isNullOrBlank()) return

        val searchUrl = "https://anikage.cc/api/media/anime/advanced-search?per_page=25&page=1&query=${query.replace(" ", "%20")}"
        val searchRes = tryParseJson<AnikageSearchResponse>(app.get(searchUrl).text) ?: return
        
        val results = searchRes.results ?: return
        var slug: String? = null
        for ((i, element) in results.withIndex()) {
            val res = element
            val id = res.id ?: res.anilistId
            if (anilistId != null && id == anilistId) {
                slug = res.slug
                break
            }
            if (anilistId == null && i == 0) {
                slug = res.slug
            }
        }
        
        if (slug == null) return

        val episodesUrl = "https://anikage.cc/api/media/anime/$slug/episodes"
        val episodesRaw = app.get(episodesUrl).text
        val episodesList = tryParseJson<AnikageEpisodesResponse>(episodesRaw)?.episodes 
                           ?: tryParseJson<List<AnikageEpisode>>(episodesRaw) 
                           ?: return
        
        var episodeNumberFound = false
        for (i in episodesList.indices) {
            val epObj = episodesList[i]
            val epNum = epObj.number ?: epObj.episode
            if (epNum == episode) {
                episodeNumberFound = true
                break
            }
        }
        
        if (!episodeNumberFound) return

        val lang = if (dubtype == "DUB") "dub" else "sub"
        val serverUrl = "https://anikage.cc/api/media/anime/$slug/episodes/$episode/servers?lang=$lang"
        val serverRes = tryParseJson<AnikageServersResponse>(app.get(serverUrl).text)
        val serversArr = serverRes?.servers
        
        val providerIds = mutableListOf<String>()
        if (serversArr != null) {
            for (i in serversArr.indices) {
                val sId = serversArr[i].id
                if (!sId.isNullOrBlank()) providerIds.add(sId)
            }
        }
        if (providerIds.isEmpty()) {
            providerIds.addAll(listOf("megg", "miko", "anya", "verse", "neko"))
        }

        coroutineScope {
            providerIds.map { provider ->
                async {
                    val sourceUrl = "https://anikage.cc/api/media/anime/$slug/episodes/$episode/sources?lang=$lang&provider=$provider"
                    val serverData = tryParseJson<AnikageSourcesResponse>(app.get(sourceUrl).text) ?: return@async
                    
                    val subtitles = serverData.subtitles
                    if (subtitles != null) {
                        for (i in subtitles.indices) {
                            val subObj = subtitles[i]
                            val label = subObj.label ?: lang
                            val file = subObj.file
                            if (file.isNullOrBlank()) continue
                            subtitleCallback(newSubtitleFile(label, "https://prox.anikage.cc/vtt/$file"))
                        }
                    }

                    val sources = serverData.sources
                    if (sources != null) {
                        val subTypeStr = if (lang == "sub") {
                            if (!subtitles.isNullOrEmpty()) "Softsub" else "Hardsub"
                        } else ""
                        val baseNameStr = "Anikage $subTypeStr".trim()

                        for (i in sources.indices) {
                            val srcObj = sources[i]
                            val isM3u8 = srcObj.isM3U8 ?: false
                            val srcUrl = srcObj.url
                            if (srcUrl.isNullOrBlank()) continue
                            
                            val qualityStr = srcObj.quality?.takeIf { it.isNotBlank() }
                            val typeStr = srcObj.type?.takeIf { it.isNotBlank() }

                            val videoUrl = "https://prox.anikage.cc/${if(isM3u8) "m3u8" else "stream"}/$srcUrl"
                            val nameStr = "$baseNameStr ${qualityStr?.replaceFirstChar { it.uppercase() } ?: typeStr?.replaceFirstChar { it.uppercase() } ?: ""}".trim()

                            callback(
                                newExtractorLink(
                                    "Anikage",
                                    nameStr,
                                    videoUrl,
                                    if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                ) {
                                    this.quality = getQualityFromName(qualityStr)
                                    this.referer = "https://anikage.cc/"
                                    this.headers = mapOf("Origin" to "https://anikage.cc/")
                                }
                            )
                        }
                    }

                    val embeds = serverData.embeds
                    if (embeds != null) {
                        for (i in embeds.indices) {
                            val embedObj = embeds[i]
                            val embedUrl = embedObj.url
                            if (embedUrl.isNullOrBlank()) continue
                            loadExtractor(embedUrl, "https://anikage.cc/", subtitleCallback, callback)
                        }
                    }
                }
            }.forEach { it.await() }
        }
    }

}