package com.Topcartoons

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class TopcartoonsProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Topcartoons())
    }
}