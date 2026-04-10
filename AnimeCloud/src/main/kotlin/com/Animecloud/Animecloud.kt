package com.Animecloud

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbUrl

class Animecloud : MainAPI() {
    override var mainUrl              = "https://fireani.me"
    override var name                 = "Animecloud"
    override val hasMainPage          = true
    override var lang                 = "de"
    override val hasDownloadSupport   = true
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.Movie,TvType.Anime)

    override val mainPage = mainPageOf(
        "best-last-7d?page=" to "Trending",
        "genre?genere=Action&page=" to "Action",
        "genre?genere=Drama&page=" to "Drama",
        "genre?genere=Komödie&page=" to "Comedy",
        "genre?genere=Mystery&page=" to "Mystery",
        "genre?genere=Romanze&page=" to "Romanze",
        "genre?genere=Abenteuer&page=" to "Abenteuer",
        "genre?genere=EngSub&page=" to "EngSub",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val url = "$mainUrl/api/animes/${request.data}$page"

        val response = app.get(url).parsedSafe<Home>()
            ?: return newHomePageResponse(
                list = HomePageList(
                    name = request.name,
                    list = emptyList(),
                    isHorizontalImages = false
                ),
                hasNext = false
            )

        val home = response.data.map { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = page < response.pages
        )
    }

    private fun HomeDaum.toSearchResult(): SearchResponse {
        val href = "$mainUrl/api/anime?slug=${this.slug}"
        val posterslug= this.poster
        return newMovieSearchResponse(
            name = this.title,
            url = href,
            type = TvType.Movie
        ) {
            posterUrl = fixUrlNull("$mainUrl/img/posters/${posterslug}")
        }
    }


    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun search(query: String): List<SearchResponse>? {
        val searchResponse = app.get("$mainUrl/api/anime/search?q=$query").parsedSafe<SearchParser>()?.data?.map { it.toSearchResponse() }
        return searchResponse
    }

    private fun Daum.toSearchResponse(): SearchResponse {
        val title=this.title
        val poster= fixUrlNull("$mainUrl/img/posters/${this.poster}")
        val href= "$mainUrl/api/anime?slug=${this.slug}"
        return newAnimeSearchResponse(
            title,
            href,
            TvType.TvSeries,
        ) {
            this.posterUrl=poster
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document= app.get(url).parsedSafe<EpisodeParser>()?.data
        val title = document?.title ?: "Unknown"
        val imdburl=document?.imdb
        val poster= fixUrlNull("$mainUrl/img/posters/${document?.poster}")
        val backgroundurl = "$mainUrl/img/posters/bg-${document?.backdrop}.webp"
        val description = document?.desc
        val genres=document?.generes
        val animeSeasons= document?.animeSeasons
        val episodes = mutableListOf<Episode>()
        animeSeasons?.map { info->
             var season:String
             season = info.season
             if (season.contains("Filme")) season="0"
             info.animeEpisodes.map {
                 val episode=it.episode?.toIntOrNull()
                 val epname="Episode $episode "
                 val epposter="${mainUrl}/img/thumbs/${it.image}"
                 val animename=url.substringAfterLast("slug=")
                 val searchSeason = if (season == "0") "Filme" else season
                 val href = "$mainUrl/api/anime/episode?slug=$animename&season=$searchSeason&episode=$episode"
                 episodes+= newEpisode(href)
                     {
                         this.name=epname
                         this.season=season.toIntOrNull()
                         this.episode=episode
                         this.posterUrl=epposter
                     }
             }
        }
        return newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backgroundurl
                this.plot = description
                this.tags = genres
                addImdbUrl(imdburl)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        app.get(data).parsedSafe<AnimecloudEP>()?.data?.anime_episode_links?.map {
            val dubtype=it.lang
            val href=it.link
            loadSourceNameExtractor("$name ${dubtype.uppercase()}",href, "", subtitleCallback, callback, quality = "1080P")
        }
        return true
    }
}
