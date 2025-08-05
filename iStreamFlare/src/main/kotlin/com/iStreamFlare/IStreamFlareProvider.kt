package com.IStreamFlare

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class IStreamFlareProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(IStreamFlare())
    }
}