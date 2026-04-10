package com.Aniworld

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse

class Serienstream : Aniworld() {
    override var mainUrl = "https://serienstream.to"
    override var name = "Serienstream"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override val mainPage = mainPageOf(
        "genre/action" to "Action",
        "genre/comedy" to "Comedy",
        "genre/drama" to "Drama",
        "genre/k-drama" to "Drama",
        "genre/thriller" to "Thriller",
        "genre/krimi" to "Krimi",
        "genre/mystery" to "Mystery",
        "genre/abenteuer" to "Abenteuer",
        "genre/fantasy" to "Fantasy",
        "genre/science-fiction" to "Sci-Fi",
        "genre/horror" to "Horror",
        "genre/western" to "Western",
        "genre/romantik" to "Romantik",
        "genre/dramedy" to "Dramedy",
        "genre/familie" to "Familie",
        "genre/telenovela" to "Telenovela",
        "genre/Sitcom" to "Sitcom",
        "genre/reality-tv" to "Reality TV",
        "genre/dokusoap" to "Doku-Soap",
        "genre/dokumentation" to "Dokumentation",
        "genre/anime" to "Anime",
        "genre/animation" to "Animation",
        "genre/Zeichentrick" to "Zeichentrick",
        "genre/kinderserie" to "Kinderserie",
        "genre/history" to "Historie"
    )


    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}?page=$page").document

        val items = arrayListOf<HomePageList>()

        val home = document.select("div.col-6").mapNotNull {
            it.toSearchResult()
        }

        if (home.isNotEmpty()) {
            items.add(
                HomePageList(
                    name = request.name,
                    list = home
                )
            )
        }

        return newHomePageResponse(items)
    }

    override suspend fun load(url: String): LoadResponse? {
        return super.load(url).apply { this?.type = TvType.TvSeries }
    }
}