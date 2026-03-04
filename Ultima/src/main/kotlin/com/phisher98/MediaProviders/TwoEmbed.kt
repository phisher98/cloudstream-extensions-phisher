package com.phisher98

import com.phisher98.UltimaMediaProvidersUtils.ServerName
import com.phisher98.UltimaMediaProvidersUtils.getBaseUrl
import com.phisher98.UltimaUtils.Category
import com.phisher98.UltimaUtils.LinkData
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink

class TwoEmbedMediaProvider : MediaProvider() {
    override val name = "2Embed"
    override val domain = "https://www.2embed.cc"
    override val categories = listOf(Category.MEDIA)

    override suspend fun loadContent(
            url: String,
            data: LinkData,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val mediaUrl =
                if (data.season == null) {
                    "$url/embed/${data.imdbId}"
                } else {
                    "$url/embedtv/${data.imdbId}&s=${data.season}&e=${data.episode}"
                }
        val framesrc =
                app.get(mediaUrl).document.selectFirst("iframe#iframesrc")?.attr("data-src")
                        ?: return
        val referer = getBaseUrl(framesrc) + "/"
        val id = framesrc.substringAfter("id=").substringBefore("&")
        val finalUrl = "https://uqloads.xyz/e/$id"
        UltimaMediaProvidersUtils.commonLinkLoader(
            name,
            ServerName.Uqload,
            finalUrl,
            referer,
            null,
            subtitleCallback,
            callback
        )
    }

    // #region - Encryption and Decryption handlers
    // #endregion - Encryption and Decryption handlers

    // #region - Data classes
    // #endregion - Data classes

}
