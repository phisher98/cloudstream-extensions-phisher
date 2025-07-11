package com.phisher98

import com.phisher98.UltimaMediaProvidersUtils.invokeExtractors
import com.phisher98.UltimaUtils.Category
import com.phisher98.UltimaUtils.LinkData
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.metaproviders.TmdbLink
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink

class Tmdb(val plugin: UltimaPlugin) : TmdbProvider() {
    override var name = "TMDB"
    override var mainUrl = "https://www.themoviedb.org"
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)
    override var lang = "en"
    override val hasMainPage = true
    override val hasQuickSearch = false
    override val useMetaLoadResponse = true
    private val apiUrl = "https://api.themoviedb.org"

    protected fun Any.toStringData(): String {
        return mapper.writeValueAsString(this)
    }

    private fun TmdbLink.toLinkData(): LinkData {
        return LinkData(
                imdbId = imdbID,
                tmdbId = tmdbID,
                title = movieName,
                season = season,
                episode = episode
        )
    }

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {
        val mediaData = AppUtils.parseJson<TmdbLink>(data).toLinkData()
        if (mediaData.isAnime)
                invokeExtractors(Category.ANIME, mediaData, subtitleCallback, callback)
        else invokeExtractors(Category.MEDIA, mediaData, subtitleCallback, callback)
        return true
    }
}
