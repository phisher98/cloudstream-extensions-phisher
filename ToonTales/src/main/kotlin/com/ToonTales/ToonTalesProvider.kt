package com.ToonTales

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class ToonTalesProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(ToonTales())
    }
}