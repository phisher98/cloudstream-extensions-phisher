package com.anineko

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AninekoPlugin : BasePlugin() {
    override fun load() {
        pingAnalytics("Anineko")
        registerMainAPI(Anineko())
        registerExtractorAPI(StreamwishHG())
        registerExtractorAPI(Playmogo())
        registerExtractorAPI(VibePlayer())
        registerExtractorAPI(Earnvids())
        registerExtractorAPI(Bibiemb())
    }
}
