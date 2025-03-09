package com.phisher98

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class QuickIPTVPlugin: BasePlugin() {
    override fun load() {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(SportsIPTV())
        registerMainAPI(PirateIPTV())
        registerMainAPI(SonyIPTV())
        registerMainAPI(JapanIPTV())
    }
}