package com.phisher98.cloudplay

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class CloudPlayPlugin: BasePlugin() {
    override fun load() {
        // All providers should be added in this manner.
        registerMainAPI(CloudPlay())
    }
}
