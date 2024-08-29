package com.Desicinemas

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class DesicinemasPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        val provider = DesicinemasProvider()
        registerMainAPI(provider)
        registerMainAPI(BollyzoneProvider())
        registerExtractorAPI(Tvlogy((provider.name)))
        registerExtractorAPI(Tellygossips((provider.name)))
    }
}


