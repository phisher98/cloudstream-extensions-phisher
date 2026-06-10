package com.animecloud

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AnimecloudProvider: BasePlugin() {
    override fun load() {
        pingAnalytics("AnimeCloud")
        registerMainAPI(Animecloud())
        registerExtractorAPI(AnimeCloudProxy())
        registerExtractorAPI(LuluStream())
    }
}