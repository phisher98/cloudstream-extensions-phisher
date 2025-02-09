package com.lindilink4u

import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class Hindilink4uPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(Hindilink4u())
        registerExtractorAPI(StreamT())
        registerExtractorAPI(Mxdrop())
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(MixDrop())
    }
}
