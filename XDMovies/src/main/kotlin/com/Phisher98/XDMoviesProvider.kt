package com.Phisher98

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class XDMoviesProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(XDMovies())
        registerExtractorAPI(Hubdrive())
        registerExtractorAPI(HubCloud())
        registerExtractorAPI(XdMoviesExtractor())
    }
}