package com.MovieBlast

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class MovieBlastProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(MovieBlast())
    }
}