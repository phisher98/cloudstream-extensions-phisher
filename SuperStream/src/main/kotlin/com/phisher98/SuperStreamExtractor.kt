package com.phisher98

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.amapIndexed
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.phisher98.BuildConfig.SUPERSTREAM_FOURTH_API
import com.phisher98.BuildConfig.SUPERSTREAM_THIRD_API
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.Locale

object SuperStreamExtractor : SuperStream() {

    suspend fun invokeSuperstream(
        token: String? = null,
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val searchUrl = "$SUPERSTREAM_FOURTH_API/search?keyword=$imdbId"
        val href = app.get(searchUrl).documentLarge.selectFirst("h2.film-name a")?.attr("href")
            ?.let { SUPERSTREAM_FOURTH_API + it }
        val mediaId = href?.let {
            app.get(it).documentLarge.selectFirst("h2.heading-name a")?.attr("href")
                ?.substringAfterLast("/")?.toIntOrNull()
        }
        mediaId?.let {
            invokeExternalSource(it, if (season == null) 1 else 2, season, episode, callback, token)
        }
    }

    private suspend fun invokeExternalSource(
        mediaId: Int? = null,
        type: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit,
        token: String? = null
    ) {
        val thirdAPI = SUPERSTREAM_THIRD_API
        val fourthAPI = SUPERSTREAM_FOURTH_API
        val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)
        val headers = mapOf("Accept-Language" to "en")
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

        val shareKey =
            app.get("$fourthAPI/index/share_link?id=${mediaId}&type=$type", headers = headers)
                .parsedSafe<ER>()?.data?.link?.substringAfterLast("/") ?: return

        val shareRes =
            app.get("$thirdAPI/file/file_share_list?share_key=$shareKey", headers = headers)
                .parsedSafe<ExternalResponse>()?.data ?: return
        val fids = if (season == null) {
            shareRes.fileList
        } else {
            shareRes.fileList?.find {
                it.fileName.equals(
                    "season $season",
                    true
                )
            }?.fid?.let { parentId ->
                app.get(
                    "$thirdAPI/file/file_share_list?share_key=$shareKey&parent_id=$parentId&page=1",
                    headers = headers
                )
                    .parsedSafe<ExternalResponse>()?.data?.fileList?.filter {
                        it.fileName?.contains("s${seasonSlug}e${episodeSlug}", true) == true
                    }
            }
        } ?: return

        fids.amapIndexed { index, fileList ->
            val superToken = token?.let {
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
            val sourcesWithQualities = mutableListOf<Triple<String, String, String>>() // url, quality, size

            document.select("div.file_quality").forEach { element ->
                val url = element.attr("data-url").takeIf { it.isNotEmpty() } ?: return@forEach
                val qualityAttr = element.attr("data-quality").takeIf { it.isNotEmpty() }
                val size = element.selectFirst(".size")?.text()?.takeIf { it.isNotEmpty() } ?: return@forEach

                val quality = if (qualityAttr.equals("ORG", ignoreCase = true)) {
                    Regex("""(\d{3,4}p)""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1) ?: "2160p"
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
                            "⌜ SuperStream ⌟",
                            "⌜ SuperStream ⌟ [Server ${index + 1}] ${source.size}",
                            source.file?.replace("\\/", "/") ?: return@org,
                            format
                        )
                        {
                            this.quality = getIndexQuality(if (format == ExtractorLinkType.M3U8) fileList.fileName else source.label)
                            this.headers = videoheaders
                        }
                    )
                }
            }
        }
    }

    suspend fun invokeSubtitleAPI(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        val url = if (season == null) {
            "https://opensubtitles-v3.strem.io/subtitles/movie/$id.json"
        } else {
            "https://opensubtitles-v3.strem.io/subtitles/series/$id:$season:$episode.json"
        }
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        )
        app.get(url, headers = headers, timeout = 100L)
            .parsedSafe<SubtitlesAPI>()?.subtitles?.amap {
                val lan = getLanguage(it.lang) ?: "Unknown"
                val suburl = it.url
                subtitleCallback.invoke(
                    newSubtitleFile(
                        lan.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },  // Use label for the name
                        suburl     // Use extracted URL
                    )
                )
            }
    }


    suspend fun invokeWyZIESUBAPI(
        id: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        val WyZIESUBAPI = "https://sub.wyzie.ru"
        val url = if (season == null) {
            "$WyZIESUBAPI/search?id=$id"
        } else {
            "$WyZIESUBAPI/search?id=$id&season=$season&episode=$episode"
        }

        val res = app.get(url).toString()
        val gson = Gson()
        val listType = object : TypeToken<List<WyZIESUB>>() {}.type
        val subtitles: List<WyZIESUB> = gson.fromJson(res, listType)
        subtitles.map {
            val lan = it.display
            val suburl = it.url
            subtitleCallback.invoke(
                newSubtitleFile(
                    lan.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },  // Use label for the name
                    suburl     // Use extracted URL
                )
            )
        }
    }
}
