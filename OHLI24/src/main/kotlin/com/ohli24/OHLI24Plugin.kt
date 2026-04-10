package com.ohli24

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class OHLI24Plugin: BasePlugin() {
    override fun load() {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(OHLI24())
        registerExtractorAPI(Cdndania())
        registerExtractorAPI(MichealCDN())
    }
}