package com.Animecloud

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AnimecloudProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Animecloud())
        registerExtractorAPI(AnimeCloudProxy())
        registerExtractorAPI(LuluStream())
    }
}