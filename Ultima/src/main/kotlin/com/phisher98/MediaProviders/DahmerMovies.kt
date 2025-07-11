package com.phisher98

import com.phisher98.UltimaMediaProvidersUtils.ServerName
import com.phisher98.UltimaMediaProvidersUtils.encodeUrl
import com.phisher98.UltimaMediaProvidersUtils.getEpisodeSlug
import com.phisher98.UltimaMediaProvidersUtils.getIndexQuality
import com.phisher98.UltimaMediaProvidersUtils.getIndexQualityTags
import com.phisher98.UltimaUtils.Category
import com.phisher98.UltimaUtils.LinkData
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink

class DahmerMoviesMediaProvider : MediaProvider() {
    override val name = "DahmerMovies"
    override val domain = "https://a.111477.xyz"
    override val categories = listOf(Category.MEDIA)

    override suspend fun loadContent(
            url: String,
            data: LinkData,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val mediaUrl =
                if (data.season == null) {
                    "$url/movies/${data.title?.replace(":", "")} (${data.year})/"
                } else {
                    "$url/tvs/${data.title?.replace(":", " -")}/Season ${data.season}/"
                }

        val request = app.get(mediaUrl, timeout = 60L)
        if (!request.isSuccessful) return
        val paths =
                request.document
                        .select("a")
                        .map { it.text() to it.attr("href") }
                        .filter {
                            if (data.season == null) {
                                it.first.contains(Regex("(?i)(1080p|2160p)"))
                            } else {
                                val (seasonSlug, episodeSlug) =
                                        getEpisodeSlug(data.season, data.episode)
                                it.first.contains(Regex("(?i)S${seasonSlug}E${episodeSlug}"))
                            }
                        }
                        .ifEmpty {
                            return
                        }

        paths.map {
            val quality = getIndexQuality(it.first)
            val tag = getIndexQualityTags(it.first)
            UltimaMediaProvidersUtils.commonLinkLoader(
                name,
                ServerName.Custom,
                (mediaUrl + it.second).encodeUrl(),
                null,
                null,
                subtitleCallback,
                callback,
                quality,
                tag = tag
            )
        }
    }

    // #region - Encryption and Decryption handlers
    // #endregion - Encryption and Decryption handlers

    // #region - Data classes
    // #endregion - Data classes

}
