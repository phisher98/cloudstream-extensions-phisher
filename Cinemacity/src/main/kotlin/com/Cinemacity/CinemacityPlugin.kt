package com.Cinemacity

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin


@CloudstreamPlugin
class CinemacityPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Cinemacity())
    }
}