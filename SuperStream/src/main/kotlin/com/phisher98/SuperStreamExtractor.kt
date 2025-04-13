@file:Suppress("NAME_SHADOWING")

package com.phisher98

import com.phisher98.BuildConfig.SUPERSTREAM_FOURTH_API
import com.phisher98.BuildConfig.SUPERSTREAM_THIRD_API
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lagradost.api.Log
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
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
        val href = app.get(searchUrl).document.selectFirst("h2.film-name a")?.attr("href")
            ?.let { SUPERSTREAM_FOURTH_API + it }
        val mediaId = href?.let {
            app.get(it).document.selectFirst("h2.heading-name a")?.attr("href")
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
            val superToken = token ?: ""
            val fallbackUrl = "$thirdAPI/console/video_quality_list?fid=${fileList.fid}&share_key=$shareKey"
            val headers = mapOf("Cookie" to superToken)
            val player: String = try {
                val primaryUrl = "https://febbox.vercel.app/api/video-quality?fid=${fileList.fid}"
                val primaryResponse = app.get(primaryUrl)

                if (primaryResponse.code == 200 && primaryResponse.text.isNotBlank()) {
                    primaryResponse.text
                } else {
                    throw Exception("Primary API failed or returned empty")
                }
            } catch (e: Exception) {
                val fallbackResponse = app.get(fallbackUrl, headers = headers).text
                fallbackResponse
            }

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
                        ExtractorLink(
                            "SuperStream ${source.size}",
                            "SuperStream [Server ${index + 1}] ${source.size}",
                            source.file?.replace("\\/", "/") ?: return@org,
                            "",
                            getIndexQuality(if (format == ExtractorLinkType.M3U8) fileList.fileName else source.label),
                            type = format,
                        )
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
                    SubtitleFile(
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
                SubtitleFile(
                    lan.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },  // Use label for the name
                    suburl     // Use extracted URL
                )
            )
        }
    }

    suspend fun invokecatflix(
        id: Int? = null,
        epid: Int? = null,
        title: String? = null,
        episode: Int? = null,
        season: Int? = null,
        callback: (ExtractorLink) -> Unit,
    ) {
        try {
            val fixtitle = title?.createSlug() ?: return

            val Juicykey = try {
                app.get(BuildConfig.CatflixAPI, referer = Catflix)
                    .parsedSafe<CatflixJuicy>()?.juice
            } catch (e: Exception) {
                null
            }

            val href = if (season == null) {
                "$Catflix/movie/$fixtitle-$id"
            } else {
                "$Catflix/episode/${fixtitle}-season-${season}-episode-${episode}/eid-$epid"
            }

            val res = try {
                app.get(href, referer = Catflix).toString()
            } catch (e: Exception) {
                return
            }

            val iframe = try {
                val encoded = Regex("""(?:const|let)\s+main_origin\s*=\s*"(.*)";""").find(res)?.groupValues?.get(1)
                base64Decode(encoded ?: return)
            } catch (e: Exception) {
                return
            }

            val iframedata = try {
                app.get(iframe, referer = Catflix).toString()
            } catch (e: Exception) {
                return
            }

            val apkey = Regex("""apkey\s*=\s*['"](.*?)["']""").find(iframedata)?.groupValues?.getOrNull(1)
            val xxid = Regex("""xxid\s*=\s*['"](.*?)["']""").find(iframedata)?.groupValues?.getOrNull(1)

            if (apkey == null || xxid == null) return

            val theJuiceData = "https://turbovid.eu/api/cucked/the_juice/?$apkey=$xxid"

            val Juicedata = try {
                app.get(
                    theJuiceData,
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                    referer = theJuiceData
                ).parsedSafe<CatflixJuicydata>()?.data
            } catch (e: Exception) {
                null
            }

            if (Juicedata != null && Juicykey != null) {
                val url = try {
                    CatdecryptHexWithKey(Juicedata, Juicykey)
                } catch (e: Exception) {
                    return
                }

                val headers = mapOf("Origin" to "https://turbovid.eu/", "Connection" to "keep-alive")
                callback.invoke(
                    newExtractorLink(
                        "Catflix",
                        "Catflix",
                        url = url,
                        INFER_TYPE
                    ) {
                        this.referer = "https://turbovid.eu/"
                        this.quality = Qualities.P1080.value
                        this.headers = headers
                    }
                )
            }

        } catch (e: Exception) {
            // Optional: Log the error if needed
        }
    }


}
