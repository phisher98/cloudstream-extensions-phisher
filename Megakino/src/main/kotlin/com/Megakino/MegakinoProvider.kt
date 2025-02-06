package com.Megakino

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.Voe

@CloudstreamPlugin
class MegakinoProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Megakino())
        registerExtractorAPI(Voe())
        registerExtractorAPI(Gxplayer())
    }
}