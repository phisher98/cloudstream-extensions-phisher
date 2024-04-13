package com.hexated

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup

class Hdmovie2 : Movierulzhd() {

    override var mainUrl = "https://hdmovie2.chat"
    override var name = "Hdmovie2"
    override val mainPage = mainPageOf(
        "trending" to "Trending",
        "movies" to "Movies",
        "genre/tv-series" to "TV Shows",
        "genre/netflix" to "Netflix",
        "genre/zee5-tv-series" to "Zee5",
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.startsWith("{")) {
            val loadData = tryParseJson<LinkData>(data)
            val source = app.post(
                url = "$directUrl/wp-admin/admin-ajax.php", data = mapOf(
                    "action" to "doo_player_ajax", "post" to "${loadData?.post}", "nume" to "${loadData?.nume}", "type" to "${loadData?.type}"
                ), referer = data, headers = mapOf("Accept" to "*/*", "X-Requested-With" to "XMLHttpRequest"
                )).parsed<ResponseHash>().embed_url.getIframe()
            if (!source.contains("youtube")) loadExtractor(
                source,
                "$directUrl/",
                subtitleCallback,
                callback
            )
        } else {
            val document = app.get(data).document
            val id = document.select("meta#dooplay-ajax-counter").attr("data-postid")
            val type = if (data.contains("/movies/")) "movie" else "tv"

            document.select("ul#playeroptionsul > li").map {
                it.attr("data-nume")
            }.apmap { nume ->
                val source = app.post(
                    url = "$directUrl/wp-admin/admin-ajax.php", data = mapOf(
                        "action" to "doo_player_ajax", "post" to id, "nume" to nume, "type" to type
                    ), referer = data, headers = mapOf("Accept" to "*/*", "X-Requested-With" to "XMLHttpRequest")
                ).parsed<ResponseHash>().embed_url.getIframe()
                when {
                    !source.contains("youtube") -> loadExtractor(
                        source,
                        "$directUrl/",
                        subtitleCallback,
                        callback
                    )
                    else -> return@apmap
                }
            }
        }
        return true
    }

    private fun String.getIframe(): String {
        return Jsoup.parse(this).select("iframe").attr("src")
    }

    data class LinkData(
        val type: String? = null,
        val post: String? = null,
        val nume: String? = null,
    )

    data class ResponseHash(
        @JsonProperty("embed_url") val embed_url: String,
        @JsonProperty("type") val type: String?,
    )
}
