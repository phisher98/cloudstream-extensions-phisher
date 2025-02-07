package com.PublicSportsIPTV

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class PublicSportsIPTVProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(PublicSportsIPTV())
    }
}
