package com.phisher98

import com.phisher98.UltimaMediaProvidersUtils.ServerName
import com.phisher98.UltimaMediaProvidersUtils.getEpisodeSlug
import com.phisher98.UltimaUtils.Category
import com.phisher98.UltimaUtils.LinkData
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink

class NowTvMediaProvider : MediaProvider() {
    override val name = "NowTv"
    override val domain = "https://myfilestorage.xyz"
    override val categories = listOf(Category.MEDIA)
    private val referer = "https://w1.nites.is/"

    override suspend fun loadContent(
            url: String,
            data: LinkData,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        suspend fun String.isSuccess(): Boolean {
            return app.get(this, referer = referer).isSuccessful
        }

        val slug = getEpisodeSlug(data.season, data.episode)
        var mediaUrl =
                if (data.season == null) "$url/${data.tmdbId}.mp4"
                else "$url/tv/${data.tmdbId}/s${data.season}e${slug.second}.mp4"
        if (!mediaUrl.isSuccess()) {
            mediaUrl =
                    if (data.season == null) {
                        val temp = "$url/${data.imdbId}.mp4"
                        if (temp.isSuccess()) temp else "$url/${data.tmdbId}-1.mp4"
                    } else {
                        "$url/tv/${data.imdbId}/s${data.season}e${slug.second}.mp4"
                    }
            if (!app.get(mediaUrl, referer = referer).isSuccessful) return
        }
        UltimaMediaProvidersUtils.commonLinkLoader(
            name,
            ServerName.Custom,
            mediaUrl,
            url,
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
