package com.phisher98

import com.phisher98.UltimaMediaProvidersUtils.ServerName
import com.phisher98.UltimaMediaProvidersUtils.createSlug
import com.phisher98.UltimaUtils.Category
import com.phisher98.UltimaUtils.LinkData
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import kotlinx.coroutines.delay

class NoverseMediaProvider : MediaProvider() {
    override val name = "Noverse"
    override val domain = "https://www.thenollyverse.com"
    override val categories = listOf(Category.MEDIA)

    override suspend fun loadContent(
            url: String,
            data: LinkData,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val fixTitle = data.title.createSlug()
        val mediaUrl =
                if (data.season == null) {
                    "$url/movie/$fixTitle/download/"
                } else {
                    "$url/serie/$fixTitle/season-${data.season}"
                }

        val doc = app.get(mediaUrl).document
        val links =
                if (data.season == null) {
                    doc.select("div.section-row table.table-striped tbody tr").map {
                        it.select("a").attr("href") to it.selectFirst("td")?.text()
                    }
                } else {
                    doc.select("div.section-row table.table-striped tbody tr")
                            .find { it.text().contains("Episode ${data.episode}") }
                            ?.select("td")
                            ?.map { it.select("a").attr("href") to it.select("a").text() }
                }
                        ?: return

        delay(4000)
        links.map { (link, quality) ->
            // if (quality.equals("Subtitles")) return@mapNotNull
            val tag = quality?.split(".")?.getOrNull(1)
            UltimaMediaProvidersUtils.commonLinkLoader(
                name,
                ServerName.Custom,
                link,
                null,
                null,
                subtitleCallback,
                callback,
                getQualityFromName("${quality?.substringBefore("p")?.trim()}p"),
                tag = tag,
            )
        }
    }

    // #region - Encryption and Decryption handlers
    // #endregion - Encryption and Decryption handlers

    // #region - Data classes
    // #endregion - Data classes

}
