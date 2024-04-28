package com.anon

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AnimeDekhoPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AnimeDekhoProvider())
        registerMainAPI(AnimeDekho2Provider())
        registerExtractorAPI(Streamruby())
    }
}