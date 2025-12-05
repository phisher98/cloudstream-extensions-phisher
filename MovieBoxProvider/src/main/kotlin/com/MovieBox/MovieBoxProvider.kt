package com.MovieBox

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import com.fasterxml.jackson.databind.JsonNode
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.max
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.utils.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

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
        "1|1" to "Trending Movies",
        "1|2" to "Trending Series",
        "1|1006" to "Trending Anime",
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
        val url = "$mainUrl/wefeed-mobile-bff/subject-api/list"
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
            // Use Jackson to parse the new API response structure
            val data = try {
                val mapper = jacksonObjectMapper()
                val root = mapper.readTree(responseBody)
                val items = root["data"]?.get("items") ?: return newHomePageResponse(emptyList())
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
                        posterUrl = coverImg
                    }
                }
            } catch (e: Exception) {
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
            val title = subject["title"]?.asText()?.substringBefore("[") ?: continue
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
                posterUrl = coverImg
                }
            )
            }
        }
        return searchList
    }

    override suspend fun load(url: String): LoadResponse {

        // ------------------ PARSE ID ------------------
        val id = if (url.contains("get?subjectId")) {
            Uri.parse(url).getQueryParameter("subjectId") ?: url.substringAfterLast('/')
        } else {
            url.substringAfterLast('/')
        }

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
            throw ErrorLoadingException("Failed to load data: ${response.body?.string()}")
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
                this.posterUrl = Poster ?: coverUrl
                this.backgroundPosterUrl = Background ?: backgroundUrl
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
            this.posterUrl = Poster ?: coverUrl
            this.backgroundPosterUrl = Background ?: backgroundUrl
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
            val originalSubjectId = if (parts[0].contains("get?subjectId")) {
                Uri.parse(parts[0]).getQueryParameter("subjectId") ?: parts[0].substringAfterLast('/')
            } else if(parts[0].contains("/")) {
                parts[0].substringAfterLast('/')
            }
            else {
                parts[0]
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
                val subjectResponseBody = subjectResponse.body?.string()
                if (subjectResponseBody != null) {
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
            }
            
            // Always add the original subject ID first as the default source with proper language name
            subjectIds.add(0, Pair(originalSubjectId, originalLanguageName))
            
            var hasAnyLinks = false
            
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
                        if (responseBody != null) {
                            val root = mapper.readTree(responseBody)
                            val playData = root["data"]
                            // Handle the new API response format with streams
                            val streams = playData?.get("streams")
                            if (streams != null && streams.isArray) {
                                for (stream in streams) {
                                    val streamUrl = stream["url"]?.asText() ?: continue
                                    val format = stream["format"]?.asText() ?: ""
                                    val resolutions = stream["resolutions"]?.asText() ?: ""
                                    val codecName = stream["codecName"]?.asText() ?: "h264"
                                    val signCookieRaw = stream["signCookie"]?.asText()
                                    val signCookie = if (signCookieRaw.isNullOrEmpty()) null else signCookieRaw
                                    val duration = stream["duration"]?.asInt()
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
                                        if (subResponse != null) {
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
                                                            lang = "$lang ($language - $resolutions)"
                                                        )
                                                    )
                                                }
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

                                        if (subResponse1 != null) {
                                            val subRoot = mapper.readTree(subResponse1.toString())
                                            val extCaptions = subRoot["data"]?.get("extCaptions")
                                            if (extCaptions != null && extCaptions.isArray) {
                                                for (caption in extCaptions) {
                                                    val captionUrl = caption["url"]?.asText() ?: continue
                                                    val lang = caption["lan"]?.asText()
                                                        ?: caption["lanName"]?.asText()
                                                        ?: caption["language"]?.asText()
                                                        ?: "Unknown"
                                                    subtitleCallback.invoke(
                                                        newSubtitleFile(
                                                            url = captionUrl,
                                                            lang = "$lang ($language - $resolutions)"
                                                        )
                                                    )
                                                }
                                            }
                                        }


                                    hasAnyLinks = true
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    continue
                }
            }
            
            return true
              
        } catch (e: Exception) {
            return false
        }
    }
}

data class MovieBoxMainResponse(
    val code: Int? = null,
    val message: String? = null,
    val data: MovieBoxData? = null
)

data class MovieBoxData(
    val subjectId: String? = null,
    val subjectType: Int? = null,
    val title: String? = null,
    val description: String? = null,
    val releaseDate: String? = null,
    val duration: String? = null,
    val genre: String? = null,
    val cover: MovieBoxCover? = null,
    val countryName: String? = null,
    val language: String? = null,
    val imdbRatingValue: String? = null,
    val staffList: List<MovieBoxStaff>? = null,
    val hasResource: Boolean? = null,
    val resourceDetectors: List<MovieBoxResourceDetector>? = null,
    val year: Int? = null,
    val durationSeconds: Int? = null,
    val dubs: List<MovieBoxDub>? = null
)

data class MovieBoxCover(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val size: Int? = null,
    val format: String? = null
)

data class MovieBoxStaff(
    val staffId: String? = null,
    val staffType: Int? = null,
    val name: String? = null,
    val character: String? = null,
    val avatarUrl: String? = null
)

data class MovieBoxResourceDetector(
    val type: Int? = null,
    val totalEpisode: Int? = null,
    val totalSize: String? = null,
    val uploadTime: String? = null,
    val uploadBy: String? = null,
    val resourceLink: String? = null,
    val downloadUrl: String? = null,
    val source: String? = null,
    val firstSize: String? = null,
    val resourceId: String? = null,
    val postId: String? = null,
    val extCaptions: List<MovieBoxCaption>? = null,
    val resolutionList: List<MovieBoxResolution>? = null,
    val subjectId: String? = null,
    val codecName: String? = null
)

data class MovieBoxResolution(
    val episode: Int? = null,
    val title: String? = null,
    val resourceLink: String? = null,
    val linkType: Int? = null,
    val size: String? = null,
    val uploadBy: String? = null,
    val resourceId: String? = null,
    val postId: String? = null,
    val extCaptions: List<MovieBoxCaption>? = null,
    val se: Int? = null,
    val ep: Int? = null,
    val sourceUrl: String? = null,
    val resolution: Int? = null,
    val codecName: String? = null,
    val duration: Int? = null,
    val requireMemberType: Int? = null,
    val memberIcon: String? = null
)

data class MovieBoxCaption(
    val url: String? = null,
    val label: String? = null,
    val language: String? = null
)

data class MovieBoxSeasonResponse(
    val code: Int? = null,
    val message: String? = null,
    val data: MovieBoxSeasonData? = null
)

data class MovieBoxSeasonData(
    val subjectId: String? = null,
    val subjectType: Int? = null,
    val seasons: List<MovieBoxSeason>? = null
)

data class MovieBoxSeason(
    val se: Int? = null,
    val maxEp: Int? = null,
    val allEp: String? = null,
    val resolutions: List<MovieBoxSeasonResolution>? = null
)

data class MovieBoxSeasonResolution(
    val resolution: Int? = null,
    val epNum: Int? = null
)

data class MovieBoxStreamResponse(
    val code: Int? = null,
    val message: String? = null,
    val data: MovieBoxStreamData? = null
)

data class MovieBoxStreamData(
    val streams: List<MovieBoxStream>? = null,
    val title: String? = null
)

data class MovieBoxStream(
    val format: String? = null,
    val id: String? = null,
    val url: String? = null,
    val resolutions: String? = null,
    val size: String? = null,
    val duration: Int? = null,
    val codecName: String? = null,
    val signCookie: String? = null
)

data class MovieBoxDub(
    val subjectId: String? = null,
    val lanName: String? = null,
    val lanCode: String? = null,
    val original: Boolean? = null,
    val type: Int? = null
)

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
        val res = searchAndPick(type, normTitle, year, imdbRatingValue)
        if (res.first != null) return res
    }

    // retry without year (often helpful for dubbed/localized titles)
    if (year != null) {
        for (type in tryOrder) {
            val res = searchAndPick(type, normTitle, null, imdbRatingValue)
            if (res.first != null) return res
        }
    }

    val stripped = normTitle
        .replace("\\b(hindi|tamil|telugu|dub|dubbed|dubbed audio|dual audio|dubbed version)\\b".toRegex(RegexOption.IGNORE_CASE), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()
    if (stripped.isNotBlank() && stripped != normTitle) {
        for (type in tryOrder) {
            val res = searchAndPick(type, stripped, year, imdbRatingValue)
            if (res.first != null) return res
        }
        if (year != null) {
            for (type in tryOrder) {
                val res = searchAndPick(type, stripped, null, imdbRatingValue)
                if (res.first != null) return res
            }
        }
    }

    return Pair(null, null)
}

private suspend fun searchAndPick(
    typeHint: String,
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
    val detailJson = org.json.JSONObject(detailText)
    val imdbId = detailJson.optJSONObject("external_ids")?.optString("imdb_id", null)

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
    val url = "https://v3-cinemeta.strem.io/meta/$metaType/$imdbId.json"

    return try {
        val resp = app.get(url).text
        mapper.readTree(resp)["meta"]
    } catch (e: Exception) {
        null
    }
}
