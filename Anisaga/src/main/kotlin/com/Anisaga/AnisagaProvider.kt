package com.Anisaga

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AnisagaProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Anisaga())
        registerExtractorAPI(AnisagaStream())
    }
}