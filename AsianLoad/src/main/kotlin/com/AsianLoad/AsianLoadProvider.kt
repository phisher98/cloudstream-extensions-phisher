package com.AsianLoad

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.Mp4Upload
import com.lagradost.cloudstream3.extractors.StreamTape

@CloudstreamPlugin
class AsianLoadProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(AsianLoad())
        registerExtractorAPI(MixDropPs())
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(Embasic())
        registerExtractorAPI(asianbxkiun())
        registerExtractorAPI(AsianLoadInfo())
        registerExtractorAPI(bulbasaur())
        registerExtractorAPI(Mp4Upload())
    }
}
