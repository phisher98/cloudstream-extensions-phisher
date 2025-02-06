package com.Animeowl

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AnimecloudProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Animeowl())
        registerExtractorAPI(OwlExtractor())
    }
}