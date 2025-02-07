package com.DoraBash

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.FileMoon
import java.io.File

@CloudstreamPlugin
class DoraBashProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(DoraBash())
        registerExtractorAPI(Vtbe())
        registerExtractorAPI(waaw())
        registerExtractorAPI(wishfast())
        registerExtractorAPI(FileMoonSx())
        registerExtractorAPI(FileMoon())
    }
}