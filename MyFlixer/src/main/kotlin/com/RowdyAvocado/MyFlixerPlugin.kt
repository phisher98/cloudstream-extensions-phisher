package com.phisher98

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class MyFlixerPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(MyFlixer())
        registerExtractorAPI(Videostr())
    }
}
