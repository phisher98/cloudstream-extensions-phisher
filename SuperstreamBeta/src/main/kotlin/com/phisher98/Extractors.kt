package com.phisher98

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson


object Extractors : Superstream(null) {

    suspend fun invokeInternalSource(
        id: Int? = null,
        type: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        uitoken: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        suspend fun LinkList.toExtractorLink(): ExtractorLink? {
            val quality=this.quality
            if (this.path.isNullOrBlank()) return null
            return newExtractorLink(
                "Internal",
                "Internal [${this.size}]",
                this.path.replace("\\/", ""),
                INFER_TYPE
            )
            {
                this.quality=getQualityFromName(quality)
            }
        }

        val query = if (type == ResponseTypes.Movies.value) {
            """{"childmode":"0","uid":"","app_version":"11.5","appid":"$appId","module":"Movie_downloadurl_v3","channel":"Website","mid":"$id","lang":"","expired_date":"${getExpiryDate()}","platform":"android","oss":"1","uid":"eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9eyJpYXQiOjE3NTYzNzczMTIsIm5iZiI6MTc1NjM3NzMxMiwiZXhwIjoxNzg3NDgxMzMyLCJkYXRhIjp7InVpZCI6NjQyNDQ1LCJ0b2tlbiI6ImRhYzc1MWIwZmUyZjEzYmNjNmU5OTNhNjhlZjcxYzRjIiwicGxhdGZvcm0iOiJhbmRyb2lkIn19DL0wK6kr5HCw2oQz8xk95dMviHt2oFOafGNwKggrsys=","open_udid":"59e139fd173d9045a2b5fc13b40dfd87","group":""}"""
        } else {
            """{"childmode":"0","app_version":"11.5","module":"TV_downloadurl_v3","channel":"Website","episode":"$episode","expired_date":"${getExpiryDate()}","platform":"android","tid":"$id","oss":"1","uid":"eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9eyJpYXQiOjE3NTYzNzczMTIsIm5iZiI6MTc1NjM3NzMxMiwiZXhwIjoxNzg3NDgxMzMyLCJkYXRhIjp7InVpZCI6NjQyNDQ1LCJ0b2tlbiI6ImRhYzc1MWIwZmUyZjEzYmNjNmU5OTNhNjhlZjcxYzRjIiwicGxhdGZvcm0iOiJhbmRyb2lkIn19DL0wK6kr5HCw2oQz8xk95dMviHt2oFOafGNwKggrsys=","appid":"$appId","season":"$season","lang":"en","group":""}"""
        }
        val linkData = queryApiParsed<LinkDataProp>(query, false)
        linkData.data?.list?.forEach { link ->
            val extractorLink = link.toExtractorLink() ?: return@forEach
            callback.invoke(extractorLink)
        }

        val fid = linkData.data?.list?.firstOrNull { it.fid != null }?.fid

        val subtitleQuery = if (type == ResponseTypes.Movies.value) {
            """{"childmode":"0","fid":"$fid","uid":"","app_version":"11.5","appid":"$appId","module":"Movie_srt_list_v2","channel":"Website","mid":"$id","lang":"en","expired_date":"${getExpiryDate()}","platform":"android"}"""
        } else {
            """{"childmode":"0","fid":"$fid","app_version":"11.5","module":"TV_srt_list_v2","channel":"Website","episode":"$episode","expired_date":"${getExpiryDate()}","platform":"android","tid":"$id","uid":"","appid":"$appId","season":"$season","lang":"en"}"""
        }

        val subtitles = queryApiParsed<SubtitleDataProp>(subtitleQuery).data
        subtitles?.list?.forEach { subs ->
            val sub = subs.subtitles.maxByOrNull { it.support_total ?: 0 }
            subtitleCallback.invoke(
                SubtitleFile(
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
        Log.d("Phisher","$season $episode")
        val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)
        val shareKey = app.get("$thirdAPI/mbp/to_share_page?box_type=${type}&mid=$mediaId&json=1")
            .parsedSafe<ExternalResponse>()?.data?.link
            ?: app.get("$thirdAPI/mbp/to_share_page?box_type=${type}&mid=$mediaId&json=1")
                .parsedSafe<ExternalResponse>()?.data?.shareLink
                ?.substringAfterLast("/") ?: return
        Log.d("Phisher",shareKey)
        val headers = mapOf("Accept-Language" to "en")
        val shareRes =
            app.get("$thirdAPI/file/file_share_list?share_key=$shareKey", headers = headers)
                .parsedSafe<ExternalResponse>()?.data ?: return
        Log.d("Phisher",shareRes.toString())

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
            val cookieheaders =
                mapOf("Cookie" to "ui=$uitoken")
            val player = app.get(
                "$thirdAPI/file/player?fid=${fileList.fid}&share_key=$shareKey",
                headers = cookieheaders
            ).text
            val sourcesJson = "sources\\s*=\\s*(\\[[\\s\\S]*?]);".toRegex(RegexOption.DOT_MATCHES_ALL)
                .find(player)?.groupValues?.get(1)

            AppUtils.tryParseJson<ArrayList<ExternalSources>>(sourcesJson)?.forEachIndexed { index, source ->
                val format = if (source.type == "video/mp4") ExtractorLinkType.VIDEO else ExtractorLinkType.M3U8
                val label = source.label

                callback.invoke(
                    ExtractorLink(
                        "External",
                        "External [Server ${index + 1}]",
                        (source.m3u8_url ?: source.file)?.replace("\\/", "/") ?: return@forEachIndexed,
                        "",
                        getQualityFromName(label),
                        type = format
                    )
                )
            }
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
                    SubtitleFile(
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
                SubtitleFile(
                    SubtitleHelper.fromThreeLettersToLanguage(sub.lang ?: "") ?: sub.lang
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