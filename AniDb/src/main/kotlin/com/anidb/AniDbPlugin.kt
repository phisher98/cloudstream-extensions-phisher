package com.anidb

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AniDbPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(AniDb())
    }
}
