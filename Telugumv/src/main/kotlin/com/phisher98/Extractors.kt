package com.phisher98

import com.lagradost.cloudstream3.extractors.VidhideExtractor


class smoothpre : VidhideExtractor() {
    override var mainUrl = "https://smoothpre.com"
    override var requiresReferer = true
}

class movearnpre : VidhideExtractor() {
    override var mainUrl = "https://movearnpre.com"
    override var requiresReferer = true
}
