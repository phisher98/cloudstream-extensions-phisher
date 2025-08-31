package com.phisher98.MediaProviders

import android.os.Build
import androidx.annotation.RequiresApi
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.phisher98.MediaProvider
import com.phisher98.UltimaMediaProvidersUtils.ServerName.Vcloud
import com.phisher98.UltimaMediaProvidersUtils.commonLinkLoader
import com.phisher98.UltimaUtils.Category
import com.phisher98.UltimaUtils.LinkData
import com.phisher98.getDomains
import kotlinx.coroutines.delay


class VegaMoviesProvider : MediaProvider() {
    override val name = "VegaMovies"
    override val domain = "https://vegamovies.moi"
    override val categories = listOf(Category.MEDIA)

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun loadContent(
        url: String,
        data: LinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val vegaMoviesAPI = getDomains()?.vegamovies ?: return
        val title=data.title
        val season=data.season
        val year=data.year
        val imdbId=data.imdbId
        val cfInterceptor = CloudflareKiller()
        val fixtitle =
            title?.substringBefore("-")?.substringBefore(":")?.replace("&", " ")?.trim().orEmpty()
        val query = if (season == null) "$fixtitle $year" else "$fixtitle season $season $year"
        val primaryUrl = "$vegaMoviesAPI/?s=$query"
        val secondaryUrl = "$vegaMoviesAPI/?search=$query"
        val excludedButtonTexts = setOf("Filepress", "GDToT", "DropGalaxy")

        val searchDoc = retry { app.get(primaryUrl, interceptor = cfInterceptor).document }
            ?: retry { app.get(secondaryUrl, interceptor = cfInterceptor).document }
            ?: return
        val articles = searchDoc.select("article h2")
        if (articles.isEmpty()) return

        var foundLinks = false

        for (article in articles) {
            val hrefpattern = article.selectFirst("a")?.attr("href").orEmpty()
            if (hrefpattern.isBlank()) continue

            val doc = retry { app.get(hrefpattern).document } ?: continue

            val imdbAnchor =
                doc.selectFirst("div.entry-inner p strong a[href*=\"imdb.com/title/tt\"]")
            val imdbHref = imdbAnchor?.attr("href")?.lowercase()

            if (imdbId != null && (imdbHref == null || !imdbHref.contains(imdbId.lowercase()))) {
                Log.i("Skip", "IMDb ID mismatch: $imdbHref != $imdbId")
                continue
            }

            if (season == null) {
                // Movie Mode
                val btnLinks = doc.select("button.dwd-button")
                    .filterNot { btn ->
                        excludedButtonTexts.any {
                            btn.text().contains(it, ignoreCase = true)
                        }
                    }
                    .mapNotNull {
                        it.closest("a")?.attr("href")?.takeIf { link -> link.isNotBlank() }
                    }

                if (btnLinks.isEmpty()) continue

                for (detailUrl in btnLinks) {
                    val detailDoc = retry { app.get(detailUrl).document } ?: continue

                    val streamingLinks = detailDoc.select("button.btn.btn-sm.btn-outline")
                        .filterNot { btn ->
                            excludedButtonTexts.any {
                                btn.text().contains(it, ignoreCase = true)
                            }
                        }
                        .mapNotNull {
                            it.closest("a")?.attr("href")?.takeIf { link -> link.isNotBlank() }
                        }

                    if (streamingLinks.isEmpty()) continue

                    for (streamingUrl in streamingLinks) {
                        commonLinkLoader(
                            name,
                            Vcloud,
                            streamingUrl,
                            null,
                            null,
                            subtitleCallback,
                            callback
                        )
                        foundLinks = true
                    }
                }

            } else {
                // TV Show Mode
                val seasonPattern = "(?i)(Season $season)"
                val episodePattern = "(?i)(V-Cloud|Single|Episode|G-Direct)"

                val seasonElements =
                    doc.select("h4:matches($seasonPattern), h3:matches($seasonPattern)")

                if (seasonElements.isEmpty()) continue

                for (seasonElement in seasonElements) {
                    val episodeLinks = seasonElement.nextElementSibling()
                        ?.select("a:matches($episodePattern)")
                        ?.mapNotNull { it.attr("href").takeIf { link -> link.isNotBlank() } }
                        ?: continue

                    if (episodeLinks.isEmpty()) continue

                    for (episodeUrl in episodeLinks) {
                        val episodeDoc = retry { app.get(episodeUrl).document } ?: continue

                        val matchBlock =
                            episodeDoc.selectFirst("h4:contains(Episodes):contains(${data.episode})")
                                ?.nextElementSibling()
                                ?.select("a:matches((?i)(V-Cloud|G-Direct|OxxFile))")
                                ?.mapNotNull {
                                    it.attr("href").takeIf { link -> link.isNotBlank() }
                                }

                        if (matchBlock.isNullOrEmpty()) continue

                        for (streamingUrl in matchBlock) {
                            commonLinkLoader(
                                name,
                                Vcloud,
                                streamingUrl,
                                null,
                                null,
                                subtitleCallback,
                                callback
                            )
                            foundLinks = true
                        }
                    }
                }
            }
        }

        if (!foundLinks) {
            Log.d("VegaMovies", "No valid streaming links found for: $title")
            return
        }
    }
}

private suspend fun <T> retry(
    times: Int = 3,
    delayMillis: Long = 1000,
    block: suspend () -> T
): T? {
    repeat(times - 1) {
        runCatching { return block() }.onFailure { delay(delayMillis) }
    }
    return runCatching { block() }.getOrNull()
}