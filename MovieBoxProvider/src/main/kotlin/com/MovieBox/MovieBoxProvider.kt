package com.MovieBox

import android.annotation.SuppressLint
import android.net.Uri
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.*
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
                "$canonicalUrl"
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

        val jsonBody =
            """{"page":$pg,"perPage":$perPage,"channelId":"$channelId","classify":"$classify","country":"$country","year":"$year","genre":"$genre","sort":"$sort"}"""



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
                    val title = item["title"]?.asText() ?: return@mapNotNull null
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
        val responseCode = response.code
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
                posterUrl = coverImg
                }
            )
            }
        }
        return searchList
    }

    override suspend fun load(url: String): LoadResponse? {
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
            "x-play-mode" to "2" // Optional, if needed for specific API behavior
        )
        val response = app.get(finalUrl, headers = headers)
        if (response.code != 200) {
            throw ErrorLoadingException("Failed to load data: ${response.body?.string()}")
        }
        val responseBody = response.body?.string() ?: throw ErrorLoadingException("Empty response body")
        val mapper = jacksonObjectMapper()
        val root = mapper.readTree(responseBody)
        val data = root["data"] ?: throw ErrorLoadingException("No data in response")

        val title = data["title"]?.asText() ?: throw ErrorLoadingException("No title found")
        val description = data["description"]?.asText()
        val releaseDate = data["releaseDate"]?.asText()
        val duration = data["duration"]?.asText()
        val genre = data["genre"]?.asText()
        val imdbRating = data["imdbRatingValue"]?.asText()?.toDoubleOrNull()?.times(10)?.toInt()
        val year = releaseDate?.substring(0, 4)?.toIntOrNull()
        val coverUrl = data["cover"]?.get("url")?.asText()
        val backgroundUrl = data["cover"]?.get("url")?.asText()
        val subjectType = data["subjectType"]?.asInt() ?: 1
        val countryName = data["countryName"]?.asText()

        // Parse cast information
        val actors = data["staffList"]?.mapNotNull { staff ->
            val staffType = staff["staffType"]?.asInt()
            if (staffType == 1) { // Actor
                val name = staff["name"]?.asText() ?: return@mapNotNull null
                val character = staff["character"]?.asText()
                val avatarUrl = staff["avatarUrl"]?.asText()
                ActorData(
                    Actor(name, avatarUrl),
                    roleString = character
                )
            } else null
        } ?: emptyList()

        // Parse tags/genres
        val tags = genre?.split(",")?.map { it.trim() } ?: emptyList()

        // Parse duration to minutes
        val durationMinutes = duration?.let { dur ->
            val regex = """(\d+)h\s*(\d+)m""".toRegex()
            val match = regex.find(dur)
            if (match != null) {
                val hours = match.groupValues[1].toIntOrNull() ?: 0
                val minutes = match.groupValues[2].toIntOrNull() ?: 0
                hours * 60 + minutes
            } else {
                dur.replace("m", "").toIntOrNull()
            }
        }

        val type = when (subjectType) {
            1 -> TvType.Movie
            2 -> TvType.TvSeries
            else -> TvType.Movie
        }

        if (type == TvType.TvSeries) {
            // For TV series, get season and episode information
            val seasonUrl = "$mainUrl/wefeed-mobile-bff/subject-api/season-info?subjectId=$id"
            val seasonXClientToken = generateXClientToken()
            val seasonXTrSignature = generateXTrSignature("GET", "application/json", "application/json", seasonUrl)
            val seasonHeaders = mapOf(
                "user-agent" to "com.community.mbox.in/50020042 (Linux; U; Android 16; en_IN; sdk_gphone64_x86_64; Build/BP22.250325.006; Cronet/133.0.6876.3)",
                "accept" to "application/json",
                "content-type" to "application/json",
                "connection" to "keep-alive",
                "x-client-token" to seasonXClientToken,
                "x-tr-signature" to seasonXTrSignature,
                "x-client-info" to """{"package_name":"com.community.mbox.in","version_name":"3.0.03.0529.03","version_code":50020042,"os":"android","os_version":"16","device_id":"da2b99c821e6ea023e4be55b54d5f7d8","install_store":"ps","gaid":"d7578036d13336cc","brand":"google","model":"sdk_gphone64_x86_64","system_language":"en","net":"NETWORK_WIFI","region":"IN","timezone":"Asia/Calcutta","sp_code":""}""",
                "x-client-status" to "0"
            )
            
            val seasonResponse = app.get(seasonUrl, headers = seasonHeaders)
            val episodes = mutableListOf<Episode>()
            
            if (seasonResponse.code == 200) {
                val seasonResponseBody = seasonResponse.body?.string()
                if (seasonResponseBody != null) {
                    val seasonRoot = mapper.readTree(seasonResponseBody)
                    val seasonData = seasonRoot["data"]
                    val seasons = seasonData?.get("seasons")
                    
                    seasons?.forEach { season ->
                        val seasonNumber = season["se"]?.asInt() ?: 1
                        val maxEpisodes = season["maxEp"]?.asInt() ?: 1
                        for (episodeNumber in 1..maxEpisodes) {
                            episodes.add(
                                newEpisode("$id|$seasonNumber|$episodeNumber") {
                                    this.name = "S${seasonNumber}E${episodeNumber}"
                                    this.season = seasonNumber
                                    this.episode = episodeNumber
                                    this.posterUrl = coverUrl
                                    this.description = "Season $seasonNumber Episode $episodeNumber"
                                }
                            )
                        }
                    }
                }
            }
            
            // If no episodes were found, add a fallback episode
            if (episodes.isEmpty()) {
                episodes.add(
                    newEpisode("$id|1|1") {
                        this.name = "Episode 1"
                        this.season = 1
                        this.episode = 1
                        this.posterUrl = coverUrl
                    }
                )
            }
            
            return newTvSeriesLoadResponse(title, finalUrl, type, episodes) {
                this.posterUrl = coverUrl
                this.backgroundPosterUrl = backgroundUrl
                this.plot = description
                this.year = year
                this.tags = tags
                this.actors = actors
                this.score = imdbRating?.let { Score.from10(it.toDouble()) }
                this.duration = durationMinutes
            }
        } else {
            return newMovieLoadResponse(title, finalUrl, type, id) {
                this.posterUrl = coverUrl
                this.backgroundPosterUrl = backgroundUrl
                this.plot = description
                this.year = year
                this.tags = tags
                this.actors = actors
                this.score = imdbRating?.let { Score.from10(it.toDouble()) }
                this.duration = durationMinutes
            }
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
                        val responseBody = response.body?.string()
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
                                    
                                    callback.invoke(
                                        newExtractorLink(
                                            source = name,
                                            name = "$name ($language - $resolutions)",
                                            url = streamUrl,
                                            type = when {
                                                streamUrl.startsWith("magnet:", ignoreCase = true) -> ExtractorLinkType.MAGNET
                                                streamUrl.substringAfterLast('.', "").equals("mpd", ignoreCase = true) -> ExtractorLinkType.DASH
                                                streamUrl.substringAfterLast('.', "").equals("torrent", ignoreCase = true) -> ExtractorLinkType.TORRENT
                                                format.equals("HLS", ignoreCase = true) || streamUrl.substringAfterLast('.', "").equals("m3u8", ignoreCase = true) -> ExtractorLinkType.M3U8
                                                else -> ExtractorLinkType.VIDEO
                                            }
                                        ) {
                                            this.headers = mapOf("Referer" to mainUrl)
                                            this.quality = Qualities.Unknown.value
                                            if (signCookie != null) {
                                                this.headers = this.headers + mapOf("Cookie" to signCookie)
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
                                                        SubtitleFile(
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
                                                        SubtitleFile(
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
