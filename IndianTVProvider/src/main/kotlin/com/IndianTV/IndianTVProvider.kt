package com.IndianTV

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.SearchResponse
import com.fasterxml.jackson.annotation.JsonProperty

class IndianTVProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://livesportsclub.me/hls/tata/" 
    override var name = "IndianTV"
    override val supportedTypes = setOf(TvType.Live)
    override var lang = "hi"

    // enable this when your provider has a main page
    override val hasMainPage = false

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val response = app.get(request.data).document
        val results = response.mapNotNull{
            box_elements = document.find_all('div', class_='box1')
            title = box_elements.find('h2', class_='text-center text-sm font-bold').text.strip()
            link_element = box.find('a', target='_blank')
            link = link_element['href'] if link_element else None
            # Extract poster link
            poster_link = box.find('img')['src']
    }
    return newHomePageResponse(request.name, results)
    }
    // this function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        return listOf<SearchResponse>()
    }
}