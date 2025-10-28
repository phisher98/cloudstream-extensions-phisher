package com.OneTouchTV

import com.google.gson.Gson
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

class OneTouchTV : MainAPI() {
    override var mainUrl = base64Decode("aHR0cHM6Ly9hcGkzLmRldmNvcnAubWU=")
    override var name = "OneTouchTV"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.AsianDrama,
        TvType.Anime
    )

    override val mainPage = mainPageOf(
        "vod/home" to "Home",
    )

    override suspend fun search(query: String,page: Int): SearchResponseList? {
        val url = "$mainUrl/vod/search?page=$page&keyword=$query"
        val responseText = try {
            app.get(url, referer = "$mainUrl/").text
        } catch (e: Exception) {
            throw ErrorLoadingException("Failed to fetch search data: ${e.message}")
        }

        val decryptedJson = try {
            decryptString(responseText)
        } catch (e: Exception) {
            throw ErrorLoadingException("Failed to decrypt response: ${e.message}")
        }

        val gson = Gson()
        val results: List<SearchResult> = try {
            if (decryptedJson.trim().startsWith("[")) {
                gson.fromJson(decryptedJson, Array<SearchResult>::class.java).toList()
            } else {
                val parsed = gson.fromJson(decryptedJson, Search::class.java)
                parsed.result
            }
        } catch (e: Exception) {
            throw ErrorLoadingException("Failed to parse decrypted JSON: ${e.message}")
        }

        if (results.isEmpty()) {
            throw ErrorLoadingException("No search results found")
        }

        return results.map { result ->
            newTvSeriesSearchResponse(
                result.title,
                "$mainUrl/vod/${result.id}/detail",
                if (result.type.equals("movie", true)) TvType.Movie else TvType.TvSeries
            ) {
                posterUrl = result.image
            }
        }.toNewSearchResponseList()
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val rawResponse = try {
            app.get("$mainUrl/${request.data}").text
        } catch (e: Exception) {
            throw ErrorLoadingException("Failed to fetch raw response: ${e.message}")
        }

        val decryptedJson = try {
            decryptString(rawResponse)
        } catch (e: Exception) {
            throw ErrorLoadingException("Failed to decrypt response: ${e.message}")
        }

        val parser = try {
            Gson().fromJson(decryptedJson, MediaResult::class.java)
        } catch (e: Exception) {
            throw ErrorLoadingException("Failed to parse decrypted JSON: ${e.message}")
        }

        val allRawMedia = buildList {
            addAll(parser.randomSlideShow?.map { it.toCleanMedia() } ?: emptyList())
            addAll(parser.recents?.map { it.toCleanMedia() } ?: emptyList())
        }

        val uniqueMedia = allRawMedia.distinctBy { it.id ?: it.title }

        val filteredMedia = uniqueMedia.filter { media ->
            settingsForProvider.enableAdult || !(media.type?.contains("RAW", ignoreCase = true) ?: false)
        }

        val groupedByCountry = filteredMedia.groupBy { it.country?.trim()?.lowercase() ?: "unknown" }

        val homeLists = groupedByCountry.mapNotNull { (country, items) ->
            if (items.size > 4) {
                HomePageList(
                    name = country.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                    list = items.map { it.toSearchResponse(mainUrl) },
                    isHorizontalImages = false
                )
            } else null
        }

        return newHomePageResponse(list = homeLists, hasNext = false)
    }

    private fun OneTouchTVParser.Day.toMedia() = OneTouchMedia(
        title = title ?: "Unknown Title",
        id = id ?: "0",
        image = image,
        type = type,
        country = country,
        year = year,
        status = status,
        isSub = isSub
    )

    private fun OneTouchTVParser.Week.toMedia() = OneTouchMedia(
        title = title ?: "Unknown Title",
        id = id ?: "0",
        image = image,
        type = type,
        country = country,
        year = year,
        status = status,
        isSub = isSub
    )

    private fun OneTouchTVParser.Month.toMedia() = OneTouchMedia(
        title = title ?: "Unknown Title",
        id = id ?: "0",
        image = image,
        type = type,
        country = country,
        year = year,
        status = status,
        isSub = isSub
    )

    private fun OneTouchMedia.toSearchResponse(): SearchResponse {
        return newTvSeriesSearchResponse(title, "$mainUrl/vod/${id}/detail", TvType.Movie) {
            this.posterUrl = image
        }
    }

    data class OneTouchMedia(
        val title: String = "Unknown Title",
        val id: String? = "0",
        val image: String? = null,
        val type: String? = null,
        val country: String? = null,
        val year: String? = null,
        val status: String? = null,
        val isSub: Boolean = false
    )

    private fun RandomSlideShow.toCleanMedia() = CleanMedia(
        id = id2 ?: id,
        title = title,
        image = image,
        country = country,
        type = type,
        year = year,
        status = status,
        isSub = isSub ?: false
    )

    private fun Recent.toCleanMedia() = CleanMedia(
        id = id2 ?: id,
        title = title,
        image = image,
        country = country,
        type = type,
        year = year,
        status = status,
        isSub = isSub ?: false
    )

    private fun CleanMedia.toSearchResponse(mainUrl: String): SearchResponse {
        return newTvSeriesSearchResponse(
            title ?: "Unknown Title",
            "$mainUrl/vod/${id ?: ""}/detail",
            TvType.Movie
        ) {
            this.posterUrl = image
        }
    }


    override suspend fun load(url: String): LoadResponse {
        val rawResponse = try {
            app.get(url).text
        } catch (e: Exception) {
            throw ErrorLoadingException("Failed to fetch raw response: ${e.message}")
        }

        val decryptedJson = try {
            val raw = decryptString(rawResponse)
            raw
        } catch (e: Exception) {
            throw ErrorLoadingException("Failed to decrypt response: ${e.message}")
        }

        val obj = JSONObject(decryptedJson)
        val title = obj.optString("title", "Unknown Title")
        val poster = obj.optString("image", "null")
        val backgroundposter = obj.optString("poster", "")
            .replace("image-7wk.pages.dev","image-v1.pages.dev")
            .takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("image", "")
        val description = obj.optString("description", "")
        val year = obj.optString("year", "").toIntOrNull()
        val status = getStatus(obj.optString("status", ""))

        val actorsJsonArray = obj.optJSONArray("actors")
        val actors = mutableListOf<ActorData>()

        if (actorsJsonArray != null) {
            for (i in 0 until actorsJsonArray.length()) {
                val actorObj = actorsJsonArray.getJSONObject(i)
                val actorName = actorObj.optString("name", "")
                val actorImage = actorObj.optString("image", "")

                actors.add(
                    ActorData(
                        Actor(
                            actorName,
                            actorImage,
                        )
                    )
                )
            }
        }

        val tags = mutableListOf<String>()
        val tagsJson = obj.optJSONArray("genres")
        if (tagsJson != null) {
            for (i in 0 until tagsJson.length()) {
                tags.add(tagsJson.optString(i).capitalize())
            }
        }

        val episodes = mutableListOf<Episode>()
        val episodesJson = obj.optJSONArray("episodes")

        if (episodesJson != null) {
            for (i in 0 until episodesJson.length()) {
                val ep = episodesJson.getJSONObject(i)
                val epStr = ep.optString("episode", "?")
                val identifier = ep.optString("identifier", "")
                val playId = ep.optString("playId", "")
                episodes.add(
                    newEpisode("$mainUrl/vod/$identifier/episode/$playId") {
                        name = "Episode $epStr"
                    }
                )
            }
        }

        val recommendation: List<SearchResponse> = try {
            val rawResponse = app.get("$mainUrl/vod/top").text

            val decryptedJson = try {
                decryptString(rawResponse)
            } catch (e: Exception) {
                throw ErrorLoadingException("Failed to decrypt response: ${e.message}")
            }

            val gson = Gson()
            val parser = try {
                gson.fromJson(decryptedJson, OneTouchTVParser::class.java)
            } catch (e: Exception) {
                throw ErrorLoadingException("Failed to parse decrypted JSON: ${e.message}")
            }

            val allMedia = buildList {
                parser.day?.forEach { add(it.toMedia()) }
                parser.week?.forEach { add(it.toMedia()) }
                parser.month?.forEach { add(it.toMedia()) }
            }

            allMedia.map { it.toSearchResponse() }

        } catch (e: Exception) {
            throw ErrorLoadingException("Failed to load recommendations: ${e.message}")
        }



        return newTvSeriesLoadResponse(title,url, type = TvType.TvSeries, episodes = episodes.reversed()) {
            this.backgroundPosterUrl = backgroundposter
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
            this.showStatus = status
            this.year = year
            this.actors = actors
            this.recommendations =recommendation
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean = coroutineScope {
        val rawResponse = try {
            app.get(data).text
        } catch (e: Exception) {
            throw ErrorLoadingException("Failed to fetch raw response: ${e.message}")
        }
        val decryptedJson = try {
            decryptString(rawResponse)
        } catch (e: Exception) {
            throw ErrorLoadingException("Failed to decrypt response: ${e.message}")
        }

        val (sources, tracks) = parseSourcesAndTracks(decryptedJson)

        launch {
            for (track in tracks) {
                subtitleCallback(
                    newSubtitleFile(
                        track.name ?: "Unknown",
                        track.file ?: continue)
                )
            }
        }

        launch {
            for (src in sources) {
                callback(
                    newExtractorLink(
                        src.name?.capitalize() ?: "Source",
                        src.name?.capitalize() ?: "Source",
                        src.url ?: continue,
                        INFER_TYPE
                    )
                    {
                        this.quality = getQualityFromName(src.quality ?: "")
                        this.headers = src.headers
                    }
                )
            }
        }
        true
    }

    private fun getStatus(t: String): ShowStatus {
        return when (t) {
            "Finished Airing" -> ShowStatus.Completed
            "ongoing" -> ShowStatus.Ongoing
            else -> ShowStatus.Completed
        }
    }
}
