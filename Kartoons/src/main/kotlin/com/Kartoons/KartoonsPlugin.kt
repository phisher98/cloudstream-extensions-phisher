package com.Kartoons

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class KartoonsPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Kartoons())
    }
}