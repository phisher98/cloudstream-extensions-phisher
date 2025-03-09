package com.prmovies

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class PRMoviesPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(PRMoviesProvider())
        registerExtractorAPI(Waaw())
        registerExtractorAPI(Minoplres())
        registerExtractorAPI(Embdproxy())
    }
}
