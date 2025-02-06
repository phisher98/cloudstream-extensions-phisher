package com.YTS

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class YTSProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(YTS())
        registerMainAPI(YTSMX())

    }
}
