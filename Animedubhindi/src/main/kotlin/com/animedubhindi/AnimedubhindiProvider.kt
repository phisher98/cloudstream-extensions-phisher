package com.animedubhindi

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.FileMoon

@CloudstreamPlugin
class AnimedubhindiProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Animedubhindi())
        registerExtractorAPI(GDFlix())
        registerExtractorAPI(HubCloud())
        registerExtractorAPI(FileMoon())
    }
}