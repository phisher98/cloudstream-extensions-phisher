package com.piratexplay

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class PiratexplayProvider : BasePlugin() {
    override fun load() {
        registerMainAPI(Piratexplay())
        registerExtractorAPI(Techinmind())
        registerExtractorAPI(Pixdrive())
        registerExtractorAPI(Ghbrisk())
        registerExtractorAPI(AWSStream())
        registerExtractorAPI(MyAnimeworld())
        registerExtractorAPI(ascdn21())
        registerExtractorAPI(PiratexplayExtractor())
        registerExtractorAPI(Iqsmartgamesstreams())
        registerExtractorAPI(Iqsmartgamespro())
        registerExtractorAPI(Cloudy())
    }
}
