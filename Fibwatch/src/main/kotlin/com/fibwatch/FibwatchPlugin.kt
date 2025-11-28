package com.Fibwatch

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin


@CloudstreamPlugin
class FibwatchPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Fibwatch())
        registerMainAPI(Fibwatchdrama())
        registerMainAPI(Fibtoon())
    }
}
