package com.animeworld

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AnimeWorldPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(AnimeWorld())
        registerExtractorAPI(Techinmind())
        registerExtractorAPI(Pixdrive())
        registerExtractorAPI(Ghbrisk())
        registerExtractorAPI(AWSStream())
        registerExtractorAPI(MyAnimeworld())
        registerExtractorAPI(ascdn21())
    }
}
