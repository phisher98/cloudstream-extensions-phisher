package com.phisher98

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class OnePacePlugin : BasePlugin() {
    override fun load() {
        //registerMainAPI(OnePace())
        registerMainAPI(OnepaceProvider())
        registerExtractorAPI(Streamruby())
        registerExtractorAPI(VidStream())
        registerExtractorAPI(Vidmolynet())
        registerExtractorAPI(GDMirrorbot())
        registerExtractorAPI(Cdnwish())
        registerExtractorAPI(Dhcplay())
        registerExtractorAPI(raretoonsindia())
        registerExtractorAPI(vidcloudupns())
    }
}
