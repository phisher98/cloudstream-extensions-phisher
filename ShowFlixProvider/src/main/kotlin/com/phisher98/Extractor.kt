package com.phisher98

import com.lagradost.cloudstream3.extractors.StreamWishExtractor

open class StreamRuby : StreamWishExtractor() {
    override val name = "StreamRuby"
    override val mainUrl = "https://streamruby.com"
    override val requiresReferer = false
}