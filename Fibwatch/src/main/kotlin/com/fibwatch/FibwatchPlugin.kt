package com.Fibwatch

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin


@CloudstreamPlugin
class FibwatchPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Fibwatch())
        registerMainAPI(Fibwatchdrama())
        registerMainAPI(Fibtoon())
    }
}
