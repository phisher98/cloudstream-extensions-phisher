package com.idlix

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class IdlixProviderPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(IdlixProvider())
        registerExtractorAPI(Jeniusplay())
    }
}