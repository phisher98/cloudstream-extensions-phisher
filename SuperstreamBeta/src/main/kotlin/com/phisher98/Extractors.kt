package com.phisher98

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document


object SuperStreamExtractor : Superstream() {

    suspend fun invokeInternalSource(
        id: Int? = null,
        type: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        superToken: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {

        val videoheaders = mapOf(
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.8",
            "Connection" to "keep-alive",
            "Range" to "bytes=0-",
            "Referer" to thirdAPI,
            "Sec-Fetch-Dest" to "video",
            "Sec-Fetch-Mode" to "no-cors",
            "Sec-Fetch-Site" to "cross-site",
            "Sec-Fetch-Storage-Access" to "none",
            "Sec-GPC" to "1",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36",
            "sec-ch-ua" to "\"Not;A=Brand\";v=\"99\", \"Brave\";v=\"139\", \"Chromium\";v=\"139\"",
            "sec-ch-ua-mobile" to "?0",
            "sec-ch-ua-platform" to "\"Windows\""
        )
        suspend fun LinkList.toExtractorLink(): ExtractorLink? {
            val quality = this.quality
            if (this.path.isNullOrBlank()) return null
            return newExtractorLink(
                "⌜ SuperStream ⌟ Internal",
                "⌜ SuperStream ⌟ Internal [${this.size}]",
                this.path.replace("\\/", ""),
                INFER_TYPE,
            )
            {
                this.quality = getQualityFromName(quality)
                this.headers=videoheaders
            }
        }
        val query = if (type == ResponseTypes.Movies.value) {
            """{"childmode":"0","uid":"","app_version":"11.5","appid":"$appId","module":"Movie_downloadurl_v3","channel":"Website","mid":"$id","lang":"","expired_date":"${getExpiryDate()}","platform":"android","oss":"1","uid":"$superToken","open_udid":"59e139fd173d9045a2b5fc13b40dfd87","group":""}"""
        } else {
            """{"childmode":"0","app_version":"11.5","module":"TV_downloadurl_v3","channel":"Website","episode":"$episode","expired_date":"${getExpiryDate()}","platform":"android","tid":"$id","oss":"1","uid":"$superToken","open_udid":"59e139fd173d9045a2b5fc13b40dfd87","appid":"$appId","season":"$season","lang":"en","group":""}"""
        }

        val linkData = queryApiParsed<LinkDataProp>(query)

        linkData.data?.list?.forEach { link ->
            val extractorLink = link.toExtractorLink() ?: return@forEach
            callback.invoke(extractorLink)
        }

        val fid = linkData.data?.list?.firstOrNull { it.fid != null }?.fid

        val subtitleQuery = if (type == ResponseTypes.Movies.value) {
            """{"childmode":"0","fid":"$fid","uid":"","app_version":"11.5","appid":"$appId","module":"Movie_srt_list_v2","channel":"Website","mid":"$id","lang":"en","uid":"$superToken","open_udid":"59e139fd173d9045a2b5fc13b40dfd87","expired_date":"${getExpiryDate()}","platform":"android"}"""
        } else {
            """{"childmode":"0","fid":"$fid","app_version":"11.5","module":"TV_srt_list_v2","channel":"Website","episode":"$episode","expired_date":"${getExpiryDate()}","platform":"android","tid":"$id","uid":"$superToken","open_udid":"59e139fd173d9045a2b5fc13b40dfd87","appid":"$appId","season":"$season","lang":"en"}"""
        }

        val subtitles = queryApiParsed<SubtitleDataProp>(subtitleQuery).data
        subtitles?.list?.forEach { subs ->
            val sub = subs.subtitles.maxByOrNull { it.support_total ?: 0 }
            subtitleCallback.invoke(
                newSubtitleFile(
                    sub?.language ?: sub?.lang ?: return@forEach,
                    sub?.filePath ?: return@forEach
                )
            )
        }
    }


    suspend fun invokeExternalSource(
        mediaId: Int? = null,
        type: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        uitoken: String?,
        callback: (ExtractorLink) -> Unit,
    ) {
        val videoheaders = mapOf(
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.8",
            "Connection" to "keep-alive",
            "Range" to "bytes=0-",
            "Referer" to thirdAPI,
            "Sec-Fetch-Dest" to "video",
            "Sec-Fetch-Mode" to "no-cors",
            "Sec-Fetch-Site" to "cross-site",
            "Sec-Fetch-Storage-Access" to "none",
            "Sec-GPC" to "1",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36",
            "sec-ch-ua" to "\"Not;A=Brand\";v=\"99\", \"Brave\";v=\"139\", \"Chromium\";v=\"139\"",
            "sec-ch-ua-mobile" to "?0",
            "sec-ch-ua-platform" to "\"Windows\""
        )
        val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)
        val shareKey = app.get("$thirdAPI/mbp/to_share_page?box_type=${type}&mid=$mediaId&json=1")
            .parsedSafe<ExternalResponse>()?.data?.link
            ?: app.get("$thirdAPI/mbp/to_share_page?box_type=${type}&mid=$mediaId&json=1")
                .parsedSafe<ExternalResponse>()?.data?.shareLink
                ?.substringAfterLast("/") ?: return
        val headers = mapOf("Accept-Language" to "en")
        val shareRes =
            app.get("$thirdAPI/file/file_share_list?share_key=$shareKey", headers = headers)
                .parsedSafe<ExternalResponse>()?.data ?: return

        val fids = if (season == null) {
            shareRes.file_list
        } else {
            val parentId =
                shareRes.file_list?.find { it.file_name.equals("season $season", true) }?.fid
            app.get(
                "$thirdAPI/file/file_share_list?share_key=$shareKey&parent_id=$parentId&page=1",
                headers = headers
            )
                .parsedSafe<ExternalResponse>()?.data?.file_list?.filter {
                    it.file_name?.contains("s${seasonSlug}e${episodeSlug}", true) == true
                }
        } ?: return

        fids.amapIndexed { index, fileList ->
            val superToken = uitoken?.let {
                if (it.startsWith("ui=")) it else "ui=$it"
            } ?: ""
            val player = app.get(
                "$thirdAPI/console/video_quality_list?fid=${fileList.fid}&share_key=$shareKey",
                headers = mapOf("Cookie" to superToken)
            ).text
            val json = try {
                JSONObject(player)
            } catch (e: Exception) {
                Log.e("Error:", "Invalid JSON response $e")
                return@amapIndexed
            }
            val htmlContent = json.optString("html", "")
            if (htmlContent.isEmpty()) return@amapIndexed

            val document: Document = Jsoup.parse(htmlContent)
            val sourcesWithQualities =
                mutableListOf<Triple<String, String, String>>() // url, quality, size

            document.select("div.file_quality").forEach { element ->
                val url = element.attr("data-url").takeIf { it.isNotEmpty() } ?: return@forEach
                val qualityAttr = element.attr("data-quality").takeIf { it.isNotEmpty() }
                val size = element.selectFirst(".size")?.text()?.takeIf { it.isNotEmpty() }
                    ?: return@forEach

                val quality = if (qualityAttr.equals("ORG", ignoreCase = true)) {
                    Regex("""(\d{3,4}p)""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)
                        ?: "2160p"
                } else {
                    qualityAttr ?: return@forEach
                }

                sourcesWithQualities.add(Triple(url, quality, size))
            }

            val sourcesJsonArray = JSONArray().apply {
                sourcesWithQualities.forEach { (url, quality, size) ->
                    put(JSONObject().apply {
                        put("file", url)
                        put("label", quality)
                        put("type", "video/mp4")
                        put("size", size)
                    })
                }
            }
            val jsonObject = JSONObject().put("sources", sourcesJsonArray)
            listOf(jsonObject.toString()).forEach {
                val parsedSources =
                    tryParseJson<ExternalSourcesWrapper>(it)?.sources ?: return@forEach
                parsedSources.forEach org@{ source ->
                    val format =
                        if (source.type == "video/mp4") ExtractorLinkType.VIDEO else ExtractorLinkType.M3U8
                    if (!(source.label == "AUTO" || format == ExtractorLinkType.VIDEO)) return@org
                    callback.invoke(
                        newExtractorLink(
                            "⌜ SuperStream ⌟ External",
                            "⌜ SuperStream ⌟ External [Server ${index + 1}] ${source.size}",
                            source.file?.replace("\\/", "/") ?: return@org,
                            INFER_TYPE
                        )
                        {
                            this.headers=videoheaders
                            this.quality=getIndexQuality(if (format == ExtractorLinkType.M3U8) fileList.file_name else source.label)
                        }
                    )
                }
            }
        }
    }

    suspend fun invokeExternalM3u8Source(
        mediaId: Int? = null,
        type: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        uitoken: String?,
        callback: (ExtractorLink) -> Unit,
    ) {

        val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)
        val shareKey = app.get("$thirdAPI/mbp/to_share_page?box_type=${type}&mid=$mediaId&json=1")
            .parsedSafe<ExternalResponse>()?.data?.link
            ?: app.get("$thirdAPI/mbp/to_share_page?box_type=${type}&mid=$mediaId&json=1")
                .parsedSafe<ExternalResponse>()?.data?.shareLink
                ?.substringAfterLast("/") ?: return
        val headers = mapOf("Accept-Language" to "en")
        val shareRes =
            app.get("$thirdAPI/file/file_share_list?share_key=$shareKey", headers = headers)
                .parsedSafe<ExternalResponse>()?.data ?: return

        val fids = if (season == null) {
            shareRes.file_list
        } else {
            val parentId =
                shareRes.file_list?.find { it.file_name.equals("season $season", true) }?.fid
            app.get(
                "$thirdAPI/file/file_share_list?share_key=$shareKey&parent_id=$parentId&page=1",
                headers = headers
            )
                .parsedSafe<ExternalResponse>()?.data?.file_list?.filter {
                    it.file_name?.contains("s${seasonSlug}e${episodeSlug}", true) == true
                }
        } ?: return

        fids.amapIndexed { index, fileList ->
            val superToken = uitoken?.let {
                if (it.startsWith("ui=")) it else "ui=$it"
            } ?: ""
            val mediaType = "application/x-www-form-urlencoded; charset=UTF-8".toMediaType()
            val body = """fid=${fileList.fid}&share_key=$shareKey""".trimIndent().toRequestBody(mediaType)
            val player = app.post(
                "$thirdAPI/file/player",
                requestBody = body,
                headers = mapOf(
                    "Cookie" to superToken,
                    "content-type" to "application/x-www-form-urlencoded; charset=UTF-8"
                )
            ).text

            val document = Jsoup.parse(player)

            val scriptText = document.select("script")
                .map { it.data() }
                .firstOrNull { it.contains("var sources") }
                ?: return@amapIndexed

            val sourcesJson = Regex("""var\s+sources\s*=\s*(\[[\s\S]*?]);""")
                .find(scriptText)
                ?.groupValues
                ?.get(1)
                ?: return@amapIndexed
            val urls = mutableListOf<String>()

            val jsonArray = org.json.JSONArray(sourcesJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val fileUrl = obj.optString("file")
                if (fileUrl.isNotEmpty()) {
                    urls.add(fileUrl)
                }
            }
            /*
            urls.forEach {
                M3u8Helper.generateM3u8(
                    "⌜ SuperStream ⌟ External HLS [Server ${index + 1}]",
                    it,
                    ""
                ).forEach(callback)
            }
            */
        }
    }

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
            ), headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).parsedSafe<WatchsomuchResponses>()?.movie?.torrents?.let { eps ->
            if (season == null) {
                eps.firstOrNull()?.id
            } else {
                eps.find { it.episode == episode && it.season == season }?.id
            }
        } ?: return

        val (seasonSlug, episodeSlug) = getEpisodeSlug(
            season,
            episode
        )

        val subUrl = if (season == null) {
            "$watchSomuchAPI/Watch/ajMovieSubtitles.aspx?mid=$id&tid=$epsId&part="
        } else {
            "$watchSomuchAPI/Watch/ajMovieSubtitles.aspx?mid=$id&tid=$epsId&part=S${seasonSlug}E${episodeSlug}"
        }

        app.get(subUrl)
            .parsedSafe<WatchsomuchSubResponses>()?.subtitles
            ?.map { sub ->
                subtitleCallback.invoke(
                    newSubtitleFile(
                        sub.label ?: "",
                        fixUrl(sub.url ?: return@map null, watchSomuchAPI)
                    )
                )
            }


    }

    suspend fun invokeOpenSubs(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        val slug = if (season == null) {
            "movie/$imdbId"
        } else {
            "series/$imdbId:$season:$episode"
        }
        app.get("${openSubAPI}/subtitles/$slug.json", timeout = 120L)
            .parsedSafe<OsResult>()?.subtitles?.map { sub ->
                subtitleCallback.invoke(
                    newSubtitleFile(
                        SubtitleHelper.fromTagToEnglishLanguageName(sub.lang ?: "") ?: sub.lang
                        ?: return@map,
                        sub.url ?: return@map
                    )
                )
            }
    }

    private fun fixUrl(url: String, domain: String): String {
        if (url.startsWith("http")) {
            return url
        }
        if (url.isEmpty()) {
            return ""
        }

        val startsWithNoHttp = url.startsWith("//")
        if (startsWithNoHttp) {
            return "https:$url"
        } else {
            if (url.startsWith('/')) {
                return domain + url
            }
            return "$domain/$url"
        }
    }

    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }

    private fun getEpisodeSlug(
        season: Int? = null,
        episode: Int? = null,
    ): Pair<String, String> {
        return if (season == null && episode == null) {
            "" to ""
        } else {
            (if (season!! < 10) "0$season" else "$season") to (if (episode!! < 10) "0$episode" else "$episode")
        }
    }
}
