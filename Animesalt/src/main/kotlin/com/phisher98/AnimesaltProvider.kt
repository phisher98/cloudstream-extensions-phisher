package com.phisher98

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AnimesaltProvider : BasePlugin() {
    override fun load() {
        registerMainAPI(Animesalt())
        registerExtractorAPI(Pixdrive())
        registerExtractorAPI(Ghbrisk())
        registerExtractorAPI(AWSStream())
        registerExtractorAPI(Zephyrflick())
        registerExtractorAPI(betaAwstream())
    }
}
