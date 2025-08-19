package recloudstream

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.StringUtils.encodeUri
import com.lagradost.cloudstream3.utils.loadExtractor

class DailymotionProvider : MainAPI() {

    data class VideoSearchResponse(
        @JsonProperty("list") val list: List<VideoItem>
    )

    data class VideoItem(
        @JsonProperty("id") val id: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("thumbnail_360_url") val thumbnail360Url: String
    )

    data class VideoDetailResponse(
        @JsonProperty("id") val id: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("description") val description: String,
        @JsonProperty("thumbnail_720_url") val thumbnail720Url: String
    )

    override var mainUrl = "https://api.dailymotion.com"
    override var name = "Dailymotion"
    override val supportedTypes = setOf(TvType.Others)

    override val mainPage = mainPageOf(
        "videos?fields=id,title,thumbnail_360_url&limit=26" to "Popular",
        "videos?fields=id,title,thumbnail_360_url&languages=en&limit=15&list=what-to-watch" to "Featured",
        "videos?fields=id,thumbnail_360_url,title,url,&live=1&sort=recent" to "Live",
        "videos?fields=description,duration,embed_html,embed_url,id,likes_total,thumbnail_360_url,title,&channel=news&country=pk&page=1&limit=100" to "News",
        "videos?fields=description,duration,embed_html,embed_url,id,likes_total,thumbnail_360_url,title,&channel=sport&country=pk&page=1&limit=100" to "Sports",
        "videos?fields=description,duration,embed_html,embed_url,id,thumbnail_360_url,title,&page=1&limit=100" to "Drama",
        "videos?fields=description,embed_url,id,thumbnail_360_url,title,&tags=Movies&page=1&limit=100" to "Movies",
        "videos?fields=description,duration,embed_url,id,likes_total,thumbnail_360_url,title,&channel=music&search=songs&page=1&limit=100" to "Music"
    )

    override var lang = "en"

    override val hasMainPage = true

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get("$mainUrl/${request.data}").text
        val popular = tryParseJson<VideoSearchResponse>(response)?.list ?: emptyList()

        return newHomePageResponse(
            listOf(
                HomePageList(
                    request.name,
                    popular.map { it.toSearchResponse(this) },
                    true
                ),
            ),
            false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("$mainUrl/videos?fields=id,title,thumbnail_360_url&limit=10&search=${query.encodeUri()}").text
        val searchResults = tryParseJson<VideoSearchResponse>(response)?.list ?: return emptyList()
        return searchResults.map { it.toSearchResponse(this) }
    }

    override suspend fun load(url: String): LoadResponse? {
        val videoId = Regex("dailymotion.com/video/([a-zA-Z0-9]+)").find(url)?.groups?.get(1)?.value
        val response = app.get("$mainUrl/video/$videoId?fields=id,title,description,thumbnail_720_url").text
        val videoDetail = tryParseJson<VideoDetailResponse>(response) ?: return null
        return videoDetail.toLoadResponse(this)
    }

    private fun VideoItem.toSearchResponse(provider: DailymotionProvider): SearchResponse {
        return provider.newMovieSearchResponse(
            this.title,
            "https://www.dailymotion.com/video/${this.id}",
            TvType.Movie
        ) {
            this.posterUrl = thumbnail360Url
        }
    }

    private suspend fun VideoDetailResponse.toLoadResponse(provider: DailymotionProvider): LoadResponse {
        return provider.newMovieLoadResponse(
            this.title,
            "https://www.dailymotion.com/video/${this.id}",
            TvType.Movie,
            this.id
        ) {
            plot = description
            posterUrl = thumbnail720Url
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        loadExtractor(
            "https://www.dailymotion.com/embed/video/$data",
            subtitleCallback,
            callback
        )
        return true
    }
}