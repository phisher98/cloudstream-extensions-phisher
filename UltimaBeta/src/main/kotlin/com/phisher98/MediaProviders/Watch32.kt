package com.phisher98

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.phisher98.UltimaMediaProvidersUtils.commonLinkLoader
import com.phisher98.UltimaMediaProvidersUtils.ServerName
import com.phisher98.UltimaUtils.Category
import com.phisher98.UltimaUtils.LinkData

class Watch32Provider : MediaProvider() {
    override val name = "Watch32"
    override val domain = "https://watch32.sx"
    override val categories = listOf(Category.MEDIA)

    override suspend fun loadContent(
        url: String,
        data: LinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        if (data.title.isNullOrBlank()) return

        val type = if (data.season == null) "Movie" else "TV"
        val searchUrl = "$domain/search/${data.title.trim().replace(" ", "-")}"

        val matchedElement = runCatching {
            val doc = app.get(searchUrl, timeout = 120L).document
            val results = doc.select("div.flw-item")

            results.firstOrNull { item ->
                val titleElement = item.selectFirst("h2.film-name a")
                val typeElement = item.selectFirst("span.fdi-type")
                val name = titleElement?.text()?.trim() ?: return@firstOrNull false
                val mediaType = typeElement?.text()?.trim() ?: return@firstOrNull false

                name.contains(data.title, ignoreCase = true) && mediaType.equals(type, ignoreCase = true)
            }?.selectFirst("h2.film-name a")
        }.getOrNull() ?: return
        val detailUrl = domain+matchedElement.attr("href")
        val typee=if (type=="Movie") TvType.Movie else TvType.TvSeries
        val infoId=detailUrl.substringAfterLast("-")

        if (typee == TvType.TvSeries) {
            val seasonLinks = runCatching {
                app.get("$domain/ajax/season/list/$infoId").document.select("div.dropdown-menu a")
            }.getOrNull() ?: return

            val matchedSeason = seasonLinks.firstOrNull {
                it.text().contains("Season ${data.season}", ignoreCase = true)
            } ?: return

            val seasonId = matchedSeason.attr("data-id")

            val episodeLinks = runCatching {
                app.get("$domain/ajax/season/episodes/$seasonId").document.select("li.nav-item a")
            }.getOrNull() ?: return

            val matchedEpisode = episodeLinks.firstOrNull {
                it.text().contains("Eps ${data.episode}:", ignoreCase = true)
            } ?: return

            val dataId = matchedEpisode.attr("data-id")

            val serverDoc = runCatching {
                app.get("$domain/ajax/episode/servers/$dataId").document
            }.getOrNull() ?: return

            val sourceButtons = serverDoc.select("li.nav-item a")
            for (source in sourceButtons) {
                val sourceId = source.attr("data-id") ?: continue

                val iframeUrl = runCatching {
                    app.get("$domain/ajax/episode/sources/$sourceId")
                        .parsedSafe<Watch32>()?.link
                }.getOrNull() ?: continue
                commonLinkLoader(name, ServerName.Videostr, iframeUrl, null, null, subtitleCallback, callback)
            }
        }
        else
        {
            val episodeLinks = runCatching {
                app.get("$domain/ajax/episode/list/$infoId").document.select("li.nav-item a")
            }.getOrNull() ?: return

            episodeLinks.forEach { ep ->
                val dataId = ep.attr("data-id")
                val iframeUrl = runCatching {
                    app.get("$domain/ajax/episode/sources/$dataId")
                        .parsedSafe<Watch32>()?.link
                }.getOrNull() ?: return@forEach
                commonLinkLoader(name, ServerName.Videostr, iframeUrl, null, null, subtitleCallback, callback)
            }
        }
    }

    data class Watch32(
        val type: String,
        val link: String,
    )
}