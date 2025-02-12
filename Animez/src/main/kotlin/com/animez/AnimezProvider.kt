package com.animez

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class AnimezProvider : BasePlugin() {
    override fun load() {
        registerMainAPI(Animez())
    }
}