package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import java.util.Calendar

open class Hdmovie2 : Movierulzhd() {

    override var mainUrl = "https://hdmovie2.property"
    override var name = "Hdmovie2"
    override val mainPage = mainPageOf(
        "trending" to "Trending",
        "release/${Calendar.getInstance().get(Calendar.YEAR)}" to "Latest",
        "movies" to "Movies",
        "genre/hindi-webseries" to "Hindi Web Series",
        "genre/netflix" to "Netflix",
        "genre/zee5" to "Zee5",
        "genre/hindi-dubbed" to "Hindi Dubbed",
        "genre/comedy" to "Comedy",
        "genre/science-fiction" to "Science Fiction"
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val ajaxUrl = "$directUrl/wp-admin/admin-ajax.php"
        val commonHeaders = mapOf(
            "Accept" to "*/*",
            "X-Requested-With" to "XMLHttpRequest"
        )

        suspend fun fetchSource(post: String, nume: String, type: String): String {
            val response = app.post(
                url = ajaxUrl,
                data = mapOf(
                    "action" to "doo_player_ajax",
                    "post" to post,
                    "nume" to nume,
                    "type" to type
                ),
                referer = data,
                headers = commonHeaders
            ).parsed<ResponseHash>()
            return response.embed_url.getIframe()
        }

        if (data.startsWith("{")) {
            val loadData = tryParseJson<LinkData>(data) ?: return false
            val source = fetchSource(
                loadData.post.orEmpty(),
                loadData.nume.orEmpty(),
                loadData.type.orEmpty()
            )

            when {
                !source.contains("youtube") -> {
                    loadExtractor(source, "$directUrl/", subtitleCallback, callback)
                }
            }
        } else {
            val document = app.get(data).document
            val id = document.selectFirst("ul#playeroptionsul > li")?.attr("data-post") ?: return false
            val type = if (data.contains("/movies/")) "movie" else "tv"

            document.select("ul#playeroptionsul > li")
                .map { it.attr("data-nume") }
                .amap { nume ->
                    val source = fetchSource(id, nume, type)
                    Log.d("Phisher source 2",source)

                    when {
                        source.contains("ok.ru") -> {
                            loadExtractor("https:$source", "$directUrl/", subtitleCallback, callback)
                        }
                        !source.contains("youtube") -> {
                            loadExtractor(source, "$directUrl/", subtitleCallback, callback)
                        }

                        else -> {
                            Log.d("Error:","Not Found")

                        }
                    }
                }
        }

        if (data.contains("hdmovie2")) {
            val directLinks = app.get(data).document.selectFirst("p > a")?.attr("href")
            directLinks?.let {
                val doc = app.get(it).document
                doc.select("p > a").forEach { element ->
                    val label = element.selectFirst("button")?.text()?.trim() ?: return@forEach
                    val href = element.attr("href")
                    if (label.contains("GDFlix", ignoreCase = true)) {
                        val redirectedurl= app.get(href, allowRedirects = false).headers["location"] ?:""
                        loadExtractor(redirectedurl,name,subtitleCallback, callback)
                    }
                }
            }
            return true
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
