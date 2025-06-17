package com.Desicinemas

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class DesicinemasPlugin: BasePlugin() {
    override fun load() {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        val provider = DesicinemasProvider()
        registerMainAPI(provider)
        registerMainAPI(BollyzoneProvider())
        registerExtractorAPI(Tvlogyflow((provider.name)))
        registerExtractorAPI(Tellygossips((provider.name)))
        registerExtractorAPI(Tvlogyflow((provider.name)))
    }
}


