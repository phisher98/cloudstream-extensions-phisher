package com.phisher98

import android.os.Build
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.phisher98.UltimaMediaProvidersUtils.ServerName
import com.phisher98.UltimaMediaProvidersUtils.commonLinkLoader
import com.phisher98.UltimaUtils.Category
import com.phisher98.UltimaUtils.LinkData
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import kotlin.math.max

class AnimeKaiMediaProvider : MediaProvider() {
    override val name = "AnimeKai"
    override val domain = "https://animekai.bz"
    override val categories = listOf(Category.ANIME)

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun loadContent(
        url: String,
        data: LinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val jptitle = data.jpTitle
        val title = data.title
        if (jptitle.isNullOrBlank() || title.isNullOrBlank()) return

        try {
            // Perform the search requests sequentially but avoid redundant requests
            val searchEnglish = app.get("$domain/ajax/anime/search?keyword=$title").body.string()
            val searchRomaji = app.get("$domain/ajax/anime/search?keyword=$jptitle").body.string()

            val resultsEng = parseAnimeKaiResults(searchEnglish)
            val resultsRom = parseAnimeKaiResults(searchRomaji)

            val combined = (resultsEng + resultsRom).distinctBy { it.id }

            // Find the best match based on title similarity
            var bestMatch: AnimeKaiSearchResult? = null
            var highestScore = 0.0

            for (result in combined) {
                val engScore = similarity(title, result.title)
                val romScore = similarity(jptitle, result.japaneseTitle ?: "")
                val score = max(engScore, romScore)

                if (score > highestScore) {
                    highestScore = score
                    bestMatch = result
                }
            }

            bestMatch?.let { match ->
                val matchedId = match.id
                val href = "$domain/watch/$matchedId"

                // Fetch anime details and episode list
                val animeId = app.get(href).document.selectFirst("div.rate-box")?.attr("data-id")
                val decoded = app.get("${BuildConfig.KAISVA}/?f=e&d=$animeId")
                val epRes = app.get("$domain/ajax/episodes/list?ani_id=$animeId&_=$decoded")
                    .parsedSafe<AnimeKaiResponse>()?.getDocument()

                epRes?.select("div.eplist a")?.forEach { ep ->
                    val epNum = ep.attr("num").toIntOrNull()
                    if (epNum == data.episode) {
                        val token = ep.attr("token")

                        // Fetch episode links for this episode
                        val decodedtoken = app.get("${BuildConfig.KAISVA}/?f=e&d=$token")
                        val document =
                            app.get("$domain/ajax/links/list?token=$token&_=$decodedtoken")
                                .parsed<AnimeKaiResponse>()
                                .getDocument()

                        val types = listOf("sub", "softsub", "dub")
                        val servers = types.flatMap { type ->
                            document.select("div.server-items[data-id=$type] span.server[data-lid]")
                                .map { server ->
                                    val lid = server.attr("data-lid")
                                    val serverName = server.text()
                                    Triple(type, lid, serverName)
                                }
                        }

                        // Process each server sequentially
                        for ((type, lid, serverName) in servers) {
                            val decodelid = app.get("${BuildConfig.KAISVA}/?f=e&d=$lid")
                            val result = app.get("$domain/ajax/links/view?id=$lid&_=$decodelid")
                                .parsed<AnimeKaiResponse>().result
                            val decodeiframe = app.get("${BuildConfig.KAISVA}/?f=d&d=$result").text
                            val iframe = extractVideoUrlFromJsonAnimekai(decodeiframe)

                            val nameSuffix = when {
                                type.contains("soft", ignoreCase = true) -> " [Soft Sub]"
                                type.contains("sub", ignoreCase = true) -> " [Sub]"
                                type.contains("dub", ignoreCase = true) -> " [Dub]"
                                else -> ""
                            }

                            val name = "⌜ AnimeKai ⌟  |  $serverName  | $nameSuffix"
                            commonLinkLoader(
                                name,
                                ServerName.Megacc,
                                iframe,
                                null,
                                null,
                                subtitleCallback,
                                callback
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun extractVideoUrlFromJsonAnimekai(jsonData: String): String {
    val jsonObject = JSONObject(jsonData)
    return jsonObject.getString("url")
}

data class AnimeKaiM3U8(
    val sources: List<AnimekaiSource>,
    val tracks: List<AnimekaiTrack>,
    val download: String,
)
data class AnimekaiSource(
    val file: String,
)

data class AnimekaiTrack(
    val file: String,
    val label: String?,
    val kind: String,
    val default: Boolean?,
)

data class AnimeKaiResponse(
    @JsonProperty("status") val status: Boolean,
    @JsonProperty("result") val result: String
) {
    fun getDocument(): Document {
        return Jsoup.parse(result)
    }
}

data class VideoData(
    val url: String,
    val skip: Skip,
)

data class Skip(
    val intro: List<Long>,
    val outro: List<Long>,
)

fun similarity(a: String?, b: String?): Double {
    if (a.isNullOrBlank() || b.isNullOrBlank()) return 0.0
    val tokensA = a.lowercase().split(Regex("\\W+")).toSet()
    val tokensB = b.lowercase().split(Regex("\\W+")).toSet()
    if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0
    val intersection = tokensA.intersect(tokensB).size
    return intersection.toDouble() / max(tokensA.size, tokensB.size)
}

data class AnimeKaiSearchResult(
    val id: String,
    val title: String,
    val japaneseTitle: String? = null
)


private fun parseAnimeKaiResults(jsonResponse: String): List<AnimeKaiSearchResult> {
    val results = mutableListOf<AnimeKaiSearchResult>()
    val html =
        JSONObject(jsonResponse).optJSONObject("result")?.optString("html")
            ?: return results
    val doc = Jsoup.parse(html)

    for (element in doc.select("a.aitem")) {
        val href = element.attr("href").substringAfterLast("/")
        val titleElem = element.selectFirst("h6.title") ?: continue
        val title = titleElem.text().trim()
        val jpTitle = titleElem.attr("data-jp").trim().takeIf { it.isNotBlank() }

        results.add(AnimeKaiSearchResult(href, title, jpTitle))
    }

    return results
}