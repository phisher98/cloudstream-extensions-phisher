package com.Phisher98

import com.lagradost.cloudstream3.extractors.GDMirrorbot
import com.lagradost.cloudstream3.extractors.Vidmoly
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AnimeWorldProvider : BasePlugin() {
    override fun load() {
        // All providers should be added in this manner. Please don't edit the providers list
        // directly.
        registerMainAPI(AnimeWorld())
        registerExtractorAPI(Pixdrive())
        registerExtractorAPI(Ghbrisk())
        registerExtractorAPI(AWSStream())
        registerExtractorAPI(Cybervynx())
        registerExtractorAPI(GDMirrorbot())
        registerExtractorAPI(Vidmoly())
        registerExtractorAPI(Streamruby())
        registerExtractorAPI(Vidcloud())
        registerExtractorAPI(Animedekho())
    }
}
