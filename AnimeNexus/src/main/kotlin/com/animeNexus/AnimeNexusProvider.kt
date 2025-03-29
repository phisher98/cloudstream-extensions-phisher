package com.animeNexus

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class AnimeNexusProvider : BasePlugin() {
    override fun load() {
        registerMainAPI(AnimeNexus())
    }
}