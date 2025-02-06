package com.KimCartoon

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class KimCartoonPlugin: BasePlugin() {
    override fun load() {
        // All providers should be added in this manner
        registerMainAPI(KimCartoon())
    }
}