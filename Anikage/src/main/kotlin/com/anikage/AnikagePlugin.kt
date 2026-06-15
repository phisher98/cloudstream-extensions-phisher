package com.anikage

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class AnikagePlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(AnikageProvider())
    }
}
