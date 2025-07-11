package com.phisher98

import android.os.Build
import androidx.annotation.RequiresApi
import com.lagradost.api.Log
import com.phisher98.UltimaMediaProvidersUtils.ServerName.*
import com.phisher98.UltimaUtils.Category
import com.phisher98.UltimaUtils.LinkData
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.phisher98.UltimaMediaProvidersUtils.commonLinkLoader

class MoviesDriveProvider : MediaProvider() {
    override val name = "MoviesDrive"
    override val domain = "https://moviesdrive.design"
    override val categories = listOf(Category.MEDIA)

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun loadContent(
            url: String,
            data: LinkData,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val movieDriveAPI = getDomains()?.moviesdrive ?: return ?: domain
        val cleanTitle = data.title.orEmpty()
        val season =data.season
        val episode=data.episode
        val year= data.year
        val searchUrl = buildString {
            append("$movieDriveAPI/?s=$cleanTitle")
            if (season != null && !cleanTitle.contains(season.toString(), ignoreCase = true)) {
                append(" $season")
            } else if (season == null && year != null) {
                append(" $year")
            }
        }

        val figures = retry {
            val allFigures =
                app.get(searchUrl, interceptor = CloudflareKiller()).document.select("figure")
            if (season == null) {
                allFigures
            } else {
                val seasonPattern = Regex("""season\s*${season}\b""", RegexOption.IGNORE_CASE)
                allFigures.filter { figure ->
                    val img = figure.selectFirst("img")
                    val alt = img?.attr("alt").orEmpty()
                    val titleAttr = img?.attr("title").orEmpty()
                    seasonPattern.containsMatchIn(alt) || seasonPattern.containsMatchIn(titleAttr)
                }
            }
        } ?: return

        for (figure in figures) {
            val detailUrl = figure.selectFirst("a[href]")?.attr("href").orEmpty()
            if (detailUrl.isBlank()) continue

            val detailDoc = retry {
                app.get(detailUrl, interceptor = CloudflareKiller()).document
            } ?: continue

            val imdbId = detailDoc
                .select("a[href*=\"imdb.com/title/\"]")
                .firstOrNull()
                ?.attr("href")
                ?.substringAfter("title/")
                ?.substringBefore("/")
                ?.takeIf { it.isNotBlank() } ?: continue

            val titleMatch = imdbId == data.imdbId.orEmpty() || detailDoc
                .select("main > p:nth-child(10)")
                .firstOrNull()
                ?.text()
                ?.contains(cleanTitle, ignoreCase = true) == true

            if (!titleMatch) continue

            if (season == null) {
                val links = detailDoc.select("h5 a")
                for (element in links) {
                    val urls = retry { extractMdrive(element.attr("href")) } ?: continue
                    for (serverUrl in urls) {
                        processMoviesdriveUrl(serverUrl, subtitleCallback, callback)
                    }
                }
            } else {
                val seasonPattern = "(?i)Season\\s*0?$season\\b|S0?$season\\b"
                val episodePattern =
                    "(?i)Ep\\s?0?$episode\\b|Episode\\s+0?$episode\\b|V-Cloud|G-Direct|OXXFile"

                val seasonElements = detailDoc.select("h5:matches($seasonPattern)")
                if (seasonElements.isEmpty()) continue

                val allLinks = mutableListOf<String>()

                for (seasonElement in seasonElements) {
                    val seasonHref = seasonElement.nextElementSibling()
                        ?.selectFirst("a")
                        ?.attr("href")
                        ?.takeIf { it.isNotBlank() } ?: continue

                    val episodeDoc = retry { app.get(seasonHref).document } ?: continue
                    val episodeHeaders = episodeDoc.select("h5:matches($episodePattern)")

                    for (header in episodeHeaders) {

                        val siblingLinks =
                            generateSequence(header.nextElementSibling()) { it.nextElementSibling() }
                                .takeWhile { it.tagName() != "hr" }
                                .filter { it.tagName() == "h5" }
                                .mapNotNull { h5 ->
                                    h5.selectFirst("a")?.takeIf { a ->
                                        !a.text()
                                            .contains("Zip", ignoreCase = true) && a.hasAttr("href")
                                    }?.attr("href")
                                }.toList()

                        allLinks.addAll(siblingLinks)
                    }
                }
                if (allLinks.isNotEmpty()) {
                    for (serverUrl in allLinks) {
                        processMoviesdriveUrl(serverUrl, subtitleCallback, callback)
                    }
                } else {
                    detailDoc.select("h5 a:contains(HubCloud)")
                        .mapNotNull { it.attr("href").takeIf { href -> href.isNotBlank() } }
                        .forEach { fallbackUrl ->
                            processMoviesdriveUrl(fallbackUrl, subtitleCallback, callback)
                        }
                }
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun processMoviesdriveUrl(
        serverUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        when {
            serverUrl.contains("hubcloud", ignoreCase = true) -> {
                commonLinkLoader(
                    name,
                    Hubcloud,
                    serverUrl,
                    null,
                    null,
                    subtitleCallback,
                    callback
                )
            }

            serverUrl.contains("gdlink", ignoreCase = true) -> {
                commonLinkLoader(
                    name,
                    GDFlix,
                    serverUrl,
                    null,
                    null,
                    subtitleCallback,
                    callback
                )
            }
            else -> {
                loadExtractor(serverUrl, referer = "MoviesDrive", subtitleCallback, callback)
            }
        }
    }
    // Extractor



    private suspend fun extractMdrive(url: String): List<String> {
        val regex = Regex("hubcloud|gdflix|gdlink", RegexOption.IGNORE_CASE)

        return try {
            app.get(url).document
                .select("a[href]")
                .mapNotNull { element ->
                    val href = element.attr("href")
                    if (regex.containsMatchIn(href)) {
                        href
                    } else {
                        null
                    }
                }
        } catch (e: Exception) {
            Log.e("Error Mdrive", "Error extracting links: ${e.localizedMessage}")
            emptyList()
        }
    }
}
