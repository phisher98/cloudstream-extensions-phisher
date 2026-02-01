package com.MovieBox

import android.annotation.SuppressLint
import android.net.Uri
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.max

class MovieBoxProvider : MainAPI() {
    override var mainUrl = "https://api.inmoviebox.com"
    override var name = "MovieBox"
    override val hasMainPage = true
    override var lang = "hi"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val secretKeyDefault = base64Decode("NzZpUmwwN3MweFNOOWpxbUVXQXQ3OUVCSlp1bElRSXNWNjRGWnIyTw==")
    private val secretKeyAlt = base64Decode("WHFuMm5uTzQxL0w5Mm8xaXVYaFNMSFRiWHZZNFo1Wlo2Mm04bVNMQQ==")

        private fun md5(input: ByteArray): String {
        return MessageDigest.getInstance("MD5").digest(input)
            .joinToString("") { "%02x".format(it) }
    }

    private fun reverseString(input: String): String = input.reversed()

    private fun generateXClientToken(hardcodedTimestamp: Long? = null): String {
        val timestamp = (hardcodedTimestamp ?: System.currentTimeMillis()).toString()
        val reversed = reverseString(timestamp)
        val hash = md5(reversed.toByteArray())
        return "$timestamp,$hash"
    }

    @SuppressLint("UseKtx")
    private fun buildCanonicalString(
        method: String,
        accept: String?,
        contentType: String?,
        url: String,
        body: String?,
        timestamp: Long
    ): String {
        val parsed = Uri.parse(url)
        val path = parsed.path ?: ""
        
        // Build query string with sorted parameters (if any)
        val query = if (parsed.queryParameterNames.isNotEmpty()) {
            parsed.queryParameterNames.sorted().joinToString("&") { key ->
                parsed.getQueryParameters(key).joinToString("&") { value ->
                    "$key=$value"  // Don't URL encode here - Python doesn't do it
                }
            }
        } else ""
        
        val canonicalUrl = if (query.isNotEmpty()) "$path?$query" else path

        val bodyBytes = body?.toByteArray(Charsets.UTF_8)
        val bodyHash = if (bodyBytes != null) {
            val trimmed = if (bodyBytes.size > 102400) bodyBytes.copyOfRange(0, 102400) else bodyBytes
            md5(trimmed)
        } else ""

        val bodyLength = bodyBytes?.size?.toString() ?: ""
        return "${method.uppercase()}\n" +
                "${accept ?: ""}\n" +
                "${contentType ?: ""}\n" +
                "$bodyLength\n" +
                "$timestamp\n" +
                "$bodyHash\n" +
                canonicalUrl
    }

    private fun generateXTrSignature(
        method: String,
        accept: String?,
        contentType: String?,
        url: String,
        body: String? = null,
        useAltKey: Boolean = false,
        hardcodedTimestamp: Long? = null
    ): String {
        val timestamp = hardcodedTimestamp ?: System.currentTimeMillis()
        val canonical = buildCanonicalString(method, accept, contentType, url, body, timestamp)
        val secret = if (useAltKey) secretKeyAlt else secretKeyDefault
        val secretBytes = base64DecodeArray(secret)

        val mac = Mac.getInstance("HmacMD5")
        mac.init(SecretKeySpec(secretBytes, "HmacMD5"))
        val signature = mac.doFinal(canonical.toByteArray(Charsets.UTF_8))
        val signatureB64 = base64Encode(signature)

        return "$timestamp|2|$signatureB64"
    }

    override val mainPage = mainPageOf(
        "4516404531735022304" to "Trending",
        "5692654647815587592" to "Trending in Cinema",
        "414907768299210008"  to "Bollywood",
        "3859721901924910512" to "South Indian",
        "8019599703232971616" to "Hollywood",
        "4741626294545400336" to "Top Series This Week",
        "8434602210994128512" to "Anime",
        "1255898847918934600" to "Reality TV",
        "4903182713986896328" to "Indian Drama",
        "7878715743607948784" to "Korean Drama",
        "8788126208987989488" to "Chinese Drama",
        "3910636007619709856" to "Western TV",
        "5177200225164885656" to "Turkish Drama",
        "1|1" to "Movies",
        "1|2" to "Series",
        "1|1006" to "Anime",
        "1|1;country=India" to "Indian (Movies)",
        "1|2;country=India" to "Indian (Series)",
        "1|1;classify=Hindi dub;country=United States" to "USA (Movies)",
        "1|2;classify=Hindi dub;country=United States" to "USA (Series)",
        "1|1;country=Japan" to "Japan (Movies)",
        "1|2;country=Japan" to "Japan (Series)",
        "1|1;country=China" to "China (Movies)",
        "1|2;country=China" to "China (Series)",
        "1|1;country=Philippines" to "Philippines (Movies)",
        "1|2;country=Philippines" to "Philippines (Series)",
        "1|1;country=Thailand" to "Thailand(Movies)",
        "1|2;country=Thailand" to "Thailand(Series)",
        "1|1;country=Nigeria" to "Nollywood (Movies)",
        "1|2;country=Nigeria" to "Nollywood (Series)",
        "1|1;country=Korea" to "South Korean (Movies)",
        "1|2;country=Korea" to "South Korean (Series)",
        "1|1;classify=Hindi dub;genre=Action" to "Action (Movies)",
        "1|1;classify=Hindi dub;genre=Crime" to "Crime (Movies)",
        "1|1;classify=Hindi dub;genre=Comedy" to "Comedy (Movies)",
        "1|1;classify=Hindi dub;genre=Romance" to "Romance (Movies)",
        "1|2;classify=Hindi dub;genre=Crime" to "Crime (Series)",
        "1|2;classify=Hindi dub;genre=Comedy" to "Comedy (Series)",
        "1|2;classify=Hindi dub;genre=Romance" to "Romance (Series)",
        )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val perPage = 15
        val url = if (request.data.contains("|")) "$mainUrl/wefeed-mobile-bff/subject-api/list" else "$mainUrl/wefeed-mobile-bff/tab/ranking-list?tabId=0&categoryType=${request.data}&page=$page&perPage=$perPage"

        val data1 = request.data

        val mainParts = data1.substringBefore(";").split("|")
        val pg = mainParts.getOrNull(0)?.toIntOrNull() ?: 1
        val channelId = mainParts.getOrNull(1)

        val options = mutableMapOf<String, String>()
        data1.substringAfter(";", "")
            .split(";")
            .forEach {
                val (k, v) = it.split("=").let { p ->
                    p.getOrNull(0) to p.getOrNull(1)
                }
                if (!k.isNullOrBlank() && !v.isNullOrBlank()) {
                    options[k] = v
                }
            }

        val classify = options["classify"] ?: "All"
        val country  = options["country"] ?: "All"
        val year     = options["year"] ?: "All"
        val genre    = options["genre"] ?: "All"
        val sort     = options["sort"] ?: "ForYou"

        val jsonBody = """{"page":$pg,"perPage":$perPage,"channelId":"$channelId","classify":"$classify","country":"$country","year":"$year","genre":"$genre","sort":"$sort"}"""

        // Use current timestamps instead of hardcoded ones
        val xClientToken = generateXClientToken()
        val xTrSignature = generateXTrSignature("POST", "application/json", "application/json; charset=utf-8", url , jsonBody)

        val getxTrSignature = generateXTrSignature("GET", "application/json", "application/json", url)

        val headers = mapOf(
            "user-agent" to "com.community.mbox.in/50020042 (Linux; U; Android 16; en_IN; sdk_gphone64_x86_64; Build/BP22.250325.006; Cronet/133.0.6876.3)",
            "accept" to "application/json",
            "content-type" to "application/json",
            "connection" to "keep-alive",
            "x-client-token" to xClientToken,
            "x-tr-signature" to xTrSignature,
            "x-client-info" to """{"package_name":"com.community.mbox.in","version_name":"3.0.03.0529.03","version_code":50020042,"os":"android","os_version":"16","device_id":"da2b99c821e6ea023e4be55b54d5f7d8","install_store":"ps","gaid":"d7578036d13336cc","brand":"google","model":"sdk_gphone64_x86_64","system_language":"en","net":"NETWORK_WIFI","region":"IN","timezone":"Asia/Calcutta","sp_code":""}""",
            "x-client-status" to "0",
            "x-play-mode" to "2" // Optional, if needed for specific API behavior
        )

        val getheaders = mapOf(
            "user-agent" to "com.community.mbox.in/50020042 (Linux; U; Android 16; en_IN; sdk_gphone64_x86_64; Build/BP22.250325.006; Cronet/133.0.6876.3)",
            "accept" to "application/json",
            "content-type" to "application/json",
            "connection" to "keep-alive",
            "x-client-token" to xClientToken,
            "x-tr-signature" to getxTrSignature,
            "x-client-info" to """{"package_name":"com.community.mbox.in","version_name":"3.0.03.0529.03","version_code":50020042,"os":"android","os_version":"16","device_id":"da2b99c821e6ea023e4be55b54d5f7d8","install_store":"ps","gaid":"d7578036d13336cc","brand":"google","model":"sdk_gphone64_x86_64","system_language":"en","net":"NETWORK_WIFI","region":"IN","timezone":"Asia/Calcutta","sp_code":""}""",
            "x-client-status" to "0",
        )

            val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
            val response = if (request.data.contains("|")) app.post(url, headers = headers, requestBody = requestBody) else app.get(url, headers = getheaders)

            val responseBody = response.body.string()
            // Use Jackson to parse the new API response structure
            val data = try {
                val mapper = jacksonObjectMapper()
                val root = mapper.readTree(responseBody)
                val items = root["data"]?.get("items") ?: root["data"]?.get("subjects") ?: return newHomePageResponse(emptyList())
                items.mapNotNull { item ->
                    val title = item["title"]?.asText()?.substringBefore("[") ?: return@mapNotNull null
                    val id = item["subjectId"]?.asText() ?: return@mapNotNull null
                    val coverImg = item["cover"]?.get("url")?.asText()
                    val subjectType = item["subjectType"]?.asInt() ?: 1
                    val type = when (subjectType) {
                        1 -> TvType.Movie
                        2 -> TvType.TvSeries
                        else -> TvType.Movie
                    }
                    newMovieSearchResponse(
                        name = title,
                        url = id,
                        type = type
                    ) {
                        this.posterUrl = coverImg
                        this.score = Score.from10(item["imdbRatingValue"]?.asText())
                    }
                }
            } catch (_: Exception) {
                null
            } ?: emptyList()

            return newHomePageResponse(
                listOf(
                    HomePageList(request.name, data)
                )
            )

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/wefeed-mobile-bff/subject-api/search/v2"
        val jsonBody = """{"page": 1, "perPage": 10, "keyword": "$query"}"""
        val xClientToken = generateXClientToken()
        val xTrSignature = generateXTrSignature("POST", "application/json", "application/json; charset=utf-8", url, jsonBody)
        val headers = mapOf(
            "user-agent" to "com.community.mbox.in/50020042 (Linux; U; Android 16; en_IN; sdk_gphone64_x86_64; Build/BP22.250325.006; Cronet/133.0.6876.3)",
            "accept" to "application/json",
            "content-type" to "application/json",
            "connection" to "keep-alive",
            "x-client-token" to xClientToken,
            "x-tr-signature" to xTrSignature,
            "x-client-info" to """{"package_name":"com.community.mbox.in","version_name":"3.0.03.0529.03","version_code":50020042,"os":"android","os_version":"16","device_id":"da2b99c821e6ea023e4be55b54d5f7d8","install_store":"ps","gaid":"d7578036d13336cc","brand":"google","model":"sdk_gphone64_x86_64","system_language":"en","net":"NETWORK_WIFI","region":"IN","timezone":"Asia/Calcutta","sp_code":""}""",
            "x-client-status" to "0"
        )
        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
        val response = app.post(
            url,
            headers = headers,
            requestBody = requestBody
        )

        val responseBody = response.body.string()
        val mapper = jacksonObjectMapper()
        val root = mapper.readTree(responseBody)
        val results = root["data"]?.get("results") ?: return emptyList()
        val searchList = mutableListOf<SearchResponse>()
        for (result in results) {
            val subjects = result["subjects"] ?: continue
            for (subject in subjects) {
            val title = subject["title"]?.asText() ?: continue
            val id = subject["subjectId"]?.asText() ?: continue
            val coverImg = subject["cover"]?.get("url")?.asText()
            val subjectType = subject["subjectType"]?.asInt() ?: 1
            val type = when (subjectType) {
                        1 -> TvType.Movie
                        2 -> TvType.TvSeries
                        else -> TvType.Movie
                }
            searchList.add(
                newMovieSearchResponse(
                name = title,
                url = id,
                type = type
                ) {
                    this.posterUrl = coverImg
                    this.score = Score.from10(subject["imdbRatingValue"]?.asText())
                }
            )
            }
        }
        return searchList
    }

    override suspend fun load(url: String): LoadResponse {

        val id = Regex("""subjectId=([^&]+)""")
            .find(url)
            ?.groupValues?.get(1)
            ?: url.substringAfterLast('/')


        val finalUrl = "$mainUrl/wefeed-mobile-bff/subject-api/get?subjectId=$id"
        val xClientToken = generateXClientToken()
        val xTrSignature = generateXTrSignature("GET", "application/json", "application/json", finalUrl)

        val headers = mapOf(
            "user-agent" to "com.community.mbox.in/50020042 (Linux; U; Android 16; en_IN; sdk_gphone64_x86_64; Build/BP22.250325.006; Cronet/133.0.6876.3)",
            "accept" to "application/json",
            "content-type" to "application/json",
            "connection" to "keep-alive",
            "x-client-token" to xClientToken,
            "x-tr-signature" to xTrSignature,
            "x-client-info" to """{"package_name":"com.community.mbox.in","version_name":"3.0.03.0529.03","version_code":50020042,"os":"android","os_version":"16","device_id":"da2b99c821e6ea023e4be55b54d5f7d8","install_store":"ps","gaid":"d7578036d13336cc","brand":"google","model":"sdk_gphone64_x86_64","system_language":"en","net":"NETWORK_WIFI","region":"IN","timezone":"Asia/Calcutta","sp_code":""}""",
            "x-client-status" to "0",
            "x-play-mode" to "2"
        )

        val response = app.get(finalUrl, headers = headers)
        if (response.code != 200) {
            throw ErrorLoadingException("Failed to load data: ${response.body.string()}")
        }

        val body = response.body.string()
        val mapper = jacksonObjectMapper()
        val root = mapper.readTree(body)
        val data = root["data"] ?: throw ErrorLoadingException("No data")

        val title = data["title"]?.asText()?.substringBefore("[") ?: throw ErrorLoadingException("No title found")
        val description = data["description"]?.asText()
        val releaseDate = data["releaseDate"]?.asText()
        val duration = data["duration"]?.asText()
        val genre = data["genre"]?.asText()
        val imdbRating = data["imdbRatingValue"]?.asText()?.toDoubleOrNull()?.times(10)?.toInt()
        val year = releaseDate?.substring(0, 4)?.toIntOrNull()

        val coverUrl = data["cover"]?.get("url")?.asText()
        val backgroundUrl = data["cover"]?.get("url")?.asText()

        val subjectType = data["subjectType"]?.asInt() ?: 1

        val actors = data["staffList"]
            ?.mapNotNull { staff ->
                val staffType = staff["staffType"]?.asInt()
                if (staffType == 1) {
                    val name = staff["name"]?.asText() ?: return@mapNotNull null
                    val character = staff["character"]?.asText()
                    val avatarUrl = staff["avatarUrl"]?.asText()
                    ActorData(
                        Actor(name, avatarUrl),
                        roleString = character
                    )
                } else null
            }
            ?.distinctBy { it.actor.name }
            ?: emptyList()


        val tags = genre?.split(",")?.map { it.trim() } ?: emptyList()

        val durationMinutes = duration?.let { dur ->
            val regex = """(\d+)h\s*(\d+)m""".toRegex()
            val m = regex.find(dur)
            if (m != null) {
                val h = m.groupValues[1].toIntOrNull() ?: 0
                val min = m.groupValues[2].toIntOrNull() ?: 0
                h * 60 + min
            } else dur.replace("m", "").toIntOrNull()
        }

        val type = when (subjectType) {
            1 -> TvType.Movie
            2 -> TvType.TvSeries
            else -> TvType.Movie
        }

        val (tmdbId, imdbId) = identifyID(
            title = title.substringBefore("(").substringBefore("["),
            year = releaseDate?.take(4)?.toIntOrNull(),
            imdbRatingValue = imdbRating?.toDouble(),
        )

        val logoUrl = fetchTmdbLogoUrl(
            tmdbAPI = "https://api.themoviedb.org/3",
            apiKey = "98ae14df2b8d8f8f8136499daf79f0e0",
            type = type,
            tmdbId = tmdbId,
            appLangCode = "en"
        )

        val meta = if (!imdbId.isNullOrBlank()) fetchMetaData(imdbId, type) else null
        val metaVideos = meta?.get("videos")?.toList() ?: emptyList()

        val Poster = meta?.get("poster")?.asText() ?: coverUrl
        val Background = meta?.get("background")?.asText() ?: backgroundUrl
        val Description = meta?.get("description")?.asText() ?: description
        val IMDBRating = meta?.get("imdbRating")?.asText()

        if (type == TvType.TvSeries) {

            val seasonUrl = "$mainUrl/wefeed-mobile-bff/subject-api/season-info?subjectId=$id"
            val seasonSig = generateXTrSignature("GET", "application/json", "application/json", seasonUrl)
            val seasonHeaders = headers.toMutableMap().apply {
                put("x-tr-signature", seasonSig)
            }

            val seasonResponse = app.get(seasonUrl, headers = seasonHeaders)
            val episodes = mutableListOf<Episode>()

            if (seasonResponse.code == 200) {
                val seasonBody = seasonResponse.body.string()
                val seasonRoot = mapper.readTree(seasonBody)
                val seasons = seasonRoot["data"]?.get("seasons")

                seasons?.forEach { season ->
                    val seasonNumber = season["se"]?.asInt() ?: 1
                    val maxEp = season["maxEp"]?.asInt() ?: 1

                    for (episodeNumber in 1..maxEp) {

                        val epMeta = metaVideos.firstOrNull {
                            it["season"]?.asInt() == seasonNumber &&
                                    it["episode"]?.asInt() == episodeNumber
                        }

                        val epName =
                            epMeta?.get("name")?.asText()?.takeIf { it.isNotBlank() }
                                ?: "S${seasonNumber}E${episodeNumber}"

                        val epDesc =
                            epMeta?.get("overview")?.asText()
                                ?: epMeta?.get("description")?.asText()
                                ?: "Season $seasonNumber Episode $episodeNumber"

                        val epThumb =
                            epMeta?.get("thumbnail")?.asText()?.takeIf { it.isNotBlank() }
                                ?: coverUrl

                        val aired =
                        epMeta?.get("firstAired")?.asText()?.takeIf { it.isNotBlank() }
                            ?: ""

                        episodes.add(
                            newEpisode("$id|$seasonNumber|$episodeNumber") {
                                this.name = epName
                                this.season = seasonNumber
                                this.episode = episodeNumber
                                this.posterUrl = epThumb
                                this.description = epDesc
                                addDate(aired)
                            }
                        )
                    }
                }
            }

            if (episodes.isEmpty()) {
                episodes.add(
                    newEpisode("$id|1|1") {
                        this.name = "Episode 1"
                        this.season = 1
                        this.episode = 1
                        this.posterUrl = Poster
                    }
                )
            }

            return newTvSeriesLoadResponse(title, finalUrl, type, episodes) {
                this.posterUrl =  coverUrl ?: Poster
                this.backgroundPosterUrl = Background ?: backgroundUrl
                try { this.logoUrl = logoUrl } catch(_:Throwable){}
                this.plot = Description ?: description
                this.year = year
                this.tags = tags
                this.actors = actors
                this.score = Score.from10(IMDBRating) ?: imdbRating?.let { Score.from10(it) }
                this.duration = durationMinutes
                addImdbId(imdbId)
                addTMDbId(tmdbId.toString())
            }
        }

        return newMovieLoadResponse(title, finalUrl, type, id) {
            this.posterUrl = coverUrl ?: Poster
            this.backgroundPosterUrl = Background ?: backgroundUrl
            try { this.logoUrl = logoUrl } catch(_:Throwable){}
            this.plot = Description ?: description
            this.year = year
            this.tags = tags
            this.actors = actors
            this.score = Score.from10(IMDBRating) ?:imdbRating?.let { Score.from10(it) }
            this.duration = durationMinutes
            addImdbId(imdbId)
            addTMDbId(tmdbId.toString())
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val parts = data.split("|")
            val originalSubjectId = when {
                parts[0].contains("get?subjectId") -> {
                    Regex("""subjectId=([^&]+)""")
                        .find(parts[0])
                        ?.groupValues?.get(1)
                        ?: parts[0].substringAfterLast('/')
                }
                parts[0].contains("/") -> {
                    parts[0].substringAfterLast('/')
                }
                else -> parts[0]
            }

            val season = if (parts.size > 1) parts[1].toIntOrNull() ?: 0 else 0
            val episode = if (parts.size > 2) parts[2].toIntOrNull() ?: 0 else 0
            val subjectUrl = "$mainUrl/wefeed-mobile-bff/subject-api/get?subjectId=$originalSubjectId"
            val subjectXClientToken = generateXClientToken()
            val subjectXTrSignature = generateXTrSignature("GET", "application/json", "application/json", subjectUrl)
            val subjectHeaders = mapOf(
                "user-agent" to "com.community.mbox.in/50020042 (Linux; U; Android 16; en_IN; sdk_gphone64_x86_64; Build/BP22.250325.006; Cronet/133.0.6876.3)",
                "accept" to "application/json",
                "content-type" to "application/json",
                "connection" to "keep-alive",
                "x-client-token" to subjectXClientToken,
                "x-tr-signature" to subjectXTrSignature,
                "x-client-info" to """{"package_name":"com.community.mbox.in","version_name":"3.0.03.0529.03","version_code":50020042,"os":"android","os_version":"16","device_id":"da2b99c821e6ea023e4be55b54d5f7d8","install_store":"ps","gaid":"d7578036d13336cc","brand":"google","model":"sdk_gphone64_x86_64","system_language":"en","net":"NETWORK_WIFI","region":"IN","timezone":"Asia/Calcutta","sp_code":""}""",
                "x-client-status" to "0"
            )
            
            val subjectResponse = app.get(subjectUrl, headers = subjectHeaders)
            val mapper = jacksonObjectMapper()
            val subjectIds = mutableListOf<Pair<String, String>>() // Pair of (subjectId, language)
            var originalLanguageName = "Original"
            if (subjectResponse.code == 200) {
                val subjectResponseBody = subjectResponse.body.string()
                val subjectRoot = mapper.readTree(subjectResponseBody)
                val subjectData = subjectRoot["data"]
                val dubs = subjectData?.get("dubs")
                if (dubs != null && dubs.isArray) {
                    for (dub in dubs) {
                        val dubSubjectId = dub["subjectId"]?.asText()
                        val lanName = dub["lanName"]?.asText()
                        if (dubSubjectId != null && lanName != null) {
                            if (dubSubjectId == originalSubjectId) {
                                originalLanguageName = lanName
                            } else {
                                subjectIds.add(Pair(dubSubjectId, lanName))
                            }
                        }
                    }
                }
            }
            
            // Always add the original subject ID first as the default source with proper language name
            subjectIds.add(0, Pair(originalSubjectId, originalLanguageName))
            
            //var hasAnyLinks = false
            
            // Process each subjectId (including dubs)
            for ((subjectId, language) in subjectIds) {
                try {
                    val url = "$mainUrl/wefeed-mobile-bff/subject-api/play-info?subjectId=$subjectId&se=$season&ep=$episode"
                    
                    val xClientToken = generateXClientToken()
                    val xTrSignature = generateXTrSignature("GET", "application/json", "application/json", url)
                    val headers = mapOf(
                        "user-agent" to "com.community.mbox.in/50020042 (Linux; U; Android 16; en_IN; sdk_gphone64_x86_64; Build/BP22.250325.006; Cronet/133.0.6876.3)",
                        "accept" to "application/json",
                        "content-type" to "application/json",
                        "connection" to "keep-alive",
                        "x-client-token" to xClientToken,
                        "x-tr-signature" to xTrSignature,
                        "x-client-info" to """{"package_name":"com.community.mbox.in","version_name":"3.0.03.0529.03","version_code":50020042,"os":"android","os_version":"16","device_id":"da2b99c821e6ea023e4be55b54d5f7d8","install_store":"ps","gaid":"d7578036d13336cc","brand":"google","model":"sdk_gphone64_x86_64","system_language":"en","net":"NETWORK_WIFI","region":"IN","timezone":"Asia/Calcutta","sp_code":""}""",
                        "x-client-status" to "0"
                    )
                    
                    val response = app.get(url, headers = headers)
                    if (response.code == 200) {
                        val responseBody = response.body.string()
                        val root = mapper.readTree(responseBody)
                        val playData = root["data"]
                        // Handle the new API response format with streams
                        val streams = playData?.get("streams")
                        if (streams != null && streams.isArray) {
                            for (stream in streams) {
                                val streamUrl = stream["url"]?.asText() ?: continue
                                val format = stream["format"]?.asText() ?: ""
                                val resolutions = stream["resolutions"]?.asText() ?: ""
                                //val codecName = stream["codecName"]?.asText() ?: "h264"
                                val signCookieRaw = stream["signCookie"]?.asText()
                                val signCookie = if (signCookieRaw.isNullOrEmpty()) null else signCookieRaw
                                //val duration = stream["duration"]?.asInt()
                                val id = stream["id"]?.asText() ?: "$subjectId|$season|$episode"
                                val quality = getHighestQuality(resolutions)
                                callback.invoke(
                                    newExtractorLink(
                                        source = "$name $language",
                                        name = "$name ($language)",
                                        url = streamUrl,
                                        type = when {
                                            streamUrl.startsWith("magnet:", ignoreCase = true) -> ExtractorLinkType.MAGNET
                                            streamUrl.contains(".mpd", ignoreCase = true) -> ExtractorLinkType.DASH
                                            streamUrl.substringAfterLast('.', "").equals("torrent", ignoreCase = true) -> ExtractorLinkType.TORRENT
                                            format.equals("HLS", ignoreCase = true) || streamUrl.substringAfterLast('.', "").equals("m3u8", ignoreCase = true) -> ExtractorLinkType.M3U8
                                            streamUrl.contains(".mp4", ignoreCase = true) || streamUrl.contains(".mkv", ignoreCase = true) -> ExtractorLinkType.VIDEO
                                            else -> INFER_TYPE
                                        }
                                    ) {
                                        this.headers = mapOf("Referer" to mainUrl)
                                        if (quality != null) {
                                            this.quality = quality
                                        }
                                        if (signCookie != null) {
                                            this.headers += mapOf("Cookie" to signCookie)
                                        }
                                    }
                                )
                                val subLink = "$mainUrl/wefeed-mobile-bff/subject-api/get-stream-captions?subjectId=$subjectId&streamId=$id"
                                val xClientToken = generateXClientToken()
                                val xTrSignature = generateXTrSignature("GET", "", "", subLink)
                                val headers = mapOf(
                                    "User-Agent" to "com.community.mbox.in/50020042 (Linux; U; Android 16; en_IN; sdk_gphone64_x86_64; Build/BP22.250325.006; Cronet/133.0.6876.3)",
                                    "Accept" to "",
                                    "X-Client-Info" to """{"package_name":"com.community.mbox.in","version_name":"3.0.03.0529.03","version_code":50020042,"os":"android","os_version":"16","device_id":"da2b99c821e6ea023e4be55b54d5f7d8","install_store":"ps","gaid":"d7578036d13336cc","brand":"google","model":"sdk_gphone64_x86_64","system_language":"en","net":"NETWORK_WIFI","region":"IN","timezone":"Asia/Calcutta","sp_code":""}""",
                                    "X-Client-Status" to "0",
                                    "Content-Type" to "",
                                    "X-Client-Token" to xClientToken,
                                    "x-tr-signature" to xTrSignature,
                                )
                                val subResponse = app.get(subLink, headers = headers)
                                val subRoot = mapper.readTree(subResponse.toString())
                                val extCaptions = subRoot["data"]?.get("extCaptions")
                                if (extCaptions != null && extCaptions.isArray) {
                                    for (caption in extCaptions) {
                                        val captionUrl = caption["url"]?.asText() ?: continue
                                        val lang = caption["language"]?.asText()
                                            ?: caption["lanName"]?.asText()
                                            ?: caption["lan"]?.asText()
                                            ?: "Unknown"
                                        subtitleCallback.invoke(
                                            newSubtitleFile(
                                                url = captionUrl,
                                                lang = "$lang ($language)"
                                            )
                                        )
                                    }
                                }

                                val subLink1 = "$mainUrl/wefeed-mobile-bff/subject-api/get-ext-captions?subjectId=$subjectId&resourceId=$id&episode=0"
                                val xClientToken1 = generateXClientToken()
                                val xTrSignature1 = generateXTrSignature("GET", "", "", subLink1)
                                val headers1 = mapOf(
                                    "User-Agent" to "com.community.mbox.in/50020042 (Linux; U; Android 16; en_IN; sdk_gphone64_x86_64; Build/BP22.250325.006; Cronet/133.0.6876.3)",
                                    "Accept" to "",
                                    "X-Client-Info" to """{"package_name":"com.community.mbox.in","version_name":"3.0.03.0529.03","version_code":50020042,"os":"android","os_version":"16","device_id":"da2b99c821e6ea023e4be55b54d5f7d8","install_store":"ps","gaid":"d7578036d13336cc","brand":"google","model":"sdk_gphone64_x86_64","system_language":"en","net":"NETWORK_WIFI","region":"IN","timezone":"Asia/Calcutta","sp_code":""}""",
                                    "X-Client-Status" to "0",
                                    "Content-Type" to "",
                                    "X-Client-Token" to xClientToken1,
                                    "x-tr-signature" to xTrSignature1,
                                )
                                val subResponse1 = app.get(subLink1, headers = headers1)

                                val subRoot1 = mapper.readTree(subResponse1.toString())
                                val extCaptions1 = subRoot1["data"]?.get("extCaptions")
                                if (extCaptions1 != null && extCaptions1.isArray) {
                                    for (caption in extCaptions1) {
                                        val captionUrl = caption["url"]?.asText() ?: continue
                                        val lang = caption["lan"]?.asText()
                                            ?: caption["lanName"]?.asText()
                                            ?: caption["language"]?.asText()
                                            ?: "Unknown"
                                        subtitleCallback.invoke(
                                            newSubtitleFile(
                                                url = captionUrl,
                                                lang = "$lang ($language)"
                                            )
                                        )
                                    }
                                }


                                //hasAnyLinks = true
                            }
                        }
                    }
                } catch (_: Exception) {
                    continue
                }
            }
            
            return true
              
        } catch (_: Exception) {
            return false
        }
    }
}

fun getHighestQuality(input: String): Int? {
    val qualities = listOf(
        "2160" to Qualities.P2160.value,
        "1440" to Qualities.P1440.value,
        "1080" to Qualities.P1080.value,
        "720"  to Qualities.P720.value,
        "480"  to Qualities.P480.value,
        "360"  to Qualities.P360.value,
        "240"  to Qualities.P240.value
    )

    for ((label, mappedValue) in qualities) {
        if (input.contains(label, ignoreCase = true)) {
            return mappedValue
        }
    }
    return null
}

private suspend fun identifyID(
    title: String,
    year: Int?,
    imdbRatingValue: Double?
): Pair<Int?, String?> {
    val normTitle = normalize(title)

    // try multi -> tv -> movie (with year)
    val tryOrder = listOf("multi", "tv", "movie")
    for (type in tryOrder) {
        val res = searchAndPick(normTitle, year, imdbRatingValue)
        if (res.first != null) return res
    }

    // retry without year (often helpful for dubbed/localized titles)
    if (year != null) {
        for (type in tryOrder) {
            val res = searchAndPick(normTitle, null, imdbRatingValue)
            if (res.first != null) return res
        }
    }

    val stripped = normTitle
        .replace("\\b(hindi|tamil|telugu|dub|dubbed|dubbed audio|dual audio|dubbed version)\\b".toRegex(RegexOption.IGNORE_CASE), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()
    if (stripped.isNotBlank() && stripped != normTitle) {
        for (type in tryOrder) {
            val res = searchAndPick(stripped, year, imdbRatingValue)
            if (res.first != null) return res
        }
        if (year != null) {
            for (type in tryOrder) {
                val res = searchAndPick(stripped, null, imdbRatingValue)
                if (res.first != null) return res
            }
        }
    }

    return Pair(null, null)
}

private suspend fun searchAndPick(
    normTitle: String,
    year: Int?,
    imdbRatingValue: Double?,
): Pair<Int?, String?> {

    suspend fun doSearch(endpoint: String, extraParams: String = ""): org.json.JSONArray? {
        val url = buildString {
            append("https://api.themoviedb.org/3/").append(endpoint)
            append("?api_key=").append("1865f43a0549ca50d341dd9ab8b29f49")
            append(extraParams)
            append("&include_adult=false&page=1")
        }
        val text = app.get(url).text
        return JSONObject(text).optJSONArray("results")
    }

    val multiResults = doSearch("search/multi", "&query=${URLEncoder.encode(normTitle, "UTF-8")}" + (if (year != null) "&year=$year" else ""))
    val searchQueues: List<Pair<String, org.json.JSONArray?>> = listOf(
        "multi" to multiResults,
        "tv" to doSearch("search/tv", "&query=${URLEncoder.encode(normTitle, "UTF-8")}" + (if (year != null) "&first_air_date_year=$year" else "")),
        "movie" to doSearch("search/movie", "&query=${URLEncoder.encode(normTitle, "UTF-8")}" + (if (year != null) "&year=$year" else ""))
    )

    var bestId: Int? = null
    var bestScore = -1.0
    var bestIsTv = false

    for ((sourceType, results) in searchQueues) {
        if (results == null) continue
        for (i in 0 until results.length()) {
            val o = results.getJSONObject(i)

            val mediaType = when (sourceType) {
                "multi" -> o.optString("media_type", "")
                "tv" -> "tv"
                else -> "movie"
            }

            val candidateId = o.optInt("id", -1)
            if (candidateId == -1) continue

            val candTitle = when (mediaType) {
                "tv" -> listOf(o.optString("name", ""), o.optString("original_name", "")).firstOrNull { it.isNotBlank() }?.lowercase() ?: ""
                "movie" -> listOf(o.optString("title", ""), o.optString("original_title", "")).firstOrNull { it.isNotBlank() }?.lowercase() ?: ""
                else -> listOf(o.optString("title", ""), o.optString("name", ""), o.optString("original_title", ""), o.optString("original_name", "")).firstOrNull { it.isNotBlank() }?.lowercase() ?: ""
            }

            val candDate = when (mediaType) {
                "tv" -> o.optString("first_air_date", "")
                else -> o.optString("release_date", "")
            }
            val candYear = candDate.take(4).toIntOrNull()
            val candRating = o.optDouble("vote_average", Double.NaN)

            // scoring
            var score = 0.0
            if (tokenEquals(candTitle, normTitle)) score += 50.0
            else if (candTitle.contains(normTitle) || normTitle.contains(candTitle)) score += 15.0

            if (candYear != null && year != null && candYear == year) score += 35.0

            if (imdbRatingValue != null && !candRating.isNaN()) {
                val diff = kotlin.math.abs(candRating - imdbRatingValue)
                if (diff <= 0.5) score += 10.0 else if (diff <= 1.0) score += 5.0
            }

            if (o.has("popularity")) score += (o.optDouble("popularity", 0.0) / 100.0).coerceAtMost(5.0)

            if (score > bestScore) {
                bestScore = score
                bestId = candidateId
                bestIsTv = (mediaType == "tv")
            }
        }

        if (bestScore >= 45) break
    }

    if (bestId == null || bestScore < 40.0) return Pair(null, null)

    // fetch details for external_ids
    val detailKind = if (bestIsTv) "tv" else "movie"
    val detailUrl = "https://api.themoviedb.org/3/$detailKind/$bestId?api_key=1865f43a0549ca50d341dd9ab8b29f49&append_to_response=external_ids"
    val detailText = app.get(detailUrl).text
    val detailJson = JSONObject(detailText)
    val imdbId = detailJson.optJSONObject("external_ids")?.optString("imdb_id")

    return Pair(bestId, imdbId)
}

private fun tokenEquals(a: String, b: String): Boolean {
    val sa = a.split("\\s+".toRegex()).filter { it.isNotBlank() }.toSet()
    val sb = b.split("\\s+".toRegex()).filter { it.isNotBlank() }.toSet()
    if (sa.isEmpty() || sb.isEmpty()) return false
    val inter = sa.intersect(sb).size
    return inter >= max(1, minOf(sa.size, sb.size) * 3 / 4)
}

private fun normalize(s: String): String {
    val t = s.replace("\\[.*?]".toRegex(), " ")
        .replace("\\(.*?\\)".toRegex(), " ")
        .replace("(?i)\\b(dub|dubbed|hd|4k|hindi|tamil|telugu|dual audio)\\b".toRegex(), " ")
        .trim()
        .lowercase()
        .replace(":", " ")
        .replace("\\p{Punct}".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
    return t
}

private suspend fun fetchMetaData(imdbId: String?, type: TvType): JsonNode? {
    if (imdbId.isNullOrBlank()) return null

    val metaType = if (type == TvType.TvSeries) "series" else "movie"
    val url = "https://aiometadata.elfhosted.com/stremio/b7cb164b-074b-41d5-b458-b3a834e197bb/meta/$metaType/$imdbId.json"

    return try {
        val resp = app.get(url).text
        mapper.readTree(resp)["meta"]
    } catch (_: Exception) {
        null
    }
}

suspend fun fetchTmdbLogoUrl(
    tmdbAPI: String,
    apiKey: String,
    type: TvType,
    tmdbId: Int?,
    appLangCode: String?
): String? {
    if (tmdbId == null) return null

    val appLang = appLangCode?.substringBefore("-")?.lowercase()
    val url = if (type == TvType.Movie) {
        "$tmdbAPI/movie/$tmdbId/images?api_key=$apiKey"
    } else {
        "$tmdbAPI/tv/$tmdbId/images?api_key=$apiKey"
    }

    val json = runCatching { JSONObject(app.get(url).text) }.getOrNull() ?: return null
    val logos = json.optJSONArray("logos") ?: return null
    if (logos.length() == 0) return null

    fun logoUrlAt(i: Int): String = "https://image.tmdb.org/t/p/w500${logos.getJSONObject(i).optString("file_path")}"
    fun isSvg(i: Int): Boolean = logos.getJSONObject(i).optString("file_path").endsWith(".svg", ignoreCase = true)

    if (!appLang.isNullOrBlank()) {
        var svgFallback: String? = null
        for (i in 0 until logos.length()) {
            val logo = logos.optJSONObject(i) ?: continue
            if (logo.optString("iso_639_1") == appLang) {
                if (isSvg(i)) {
                    if (svgFallback == null) svgFallback = logoUrlAt(i)
                } else {
                    return logoUrlAt(i)
                }
            }
        }
        if (svgFallback != null) return svgFallback
    }

    var enSvgFallback: String? = null
    for (i in 0 until logos.length()) {
        val logo = logos.optJSONObject(i) ?: continue
        if (logo.optString("iso_639_1") == "en") {
            if (isSvg(i)) {
                if (enSvgFallback == null) enSvgFallback = logoUrlAt(i)
            } else {
                return logoUrlAt(i)
            }
        }
    }
    if (enSvgFallback != null) return enSvgFallback

    for (i in 0 until logos.length()) {
        if (!isSvg(i)) return logoUrlAt(i)
    }

    return logoUrlAt(0)
}
