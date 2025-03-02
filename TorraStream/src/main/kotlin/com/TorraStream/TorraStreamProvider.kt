package com.TorraStream

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class TorraStreamProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(TorraStream())
        registerMainAPI(TorraStreamAnime())
    }
}
