package com.DoraBash

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.FileMoon
import java.io.File

@CloudstreamPlugin
class DoraBashProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DoraBash())
        registerExtractorAPI(Vtbe())
        registerExtractorAPI(waaw())
        registerExtractorAPI(wishfast())
        registerExtractorAPI(FileMoonSx())
        registerExtractorAPI(FileMoon())
    }
}