package com.phisher98

import com.lagradost.cloudstream3.extractors.Filesim

class Pixdrive : Filesim() {
    override var mainUrl = "https://pixdrive.cfd"
}

class Ghbrisk : Filesim() {
    override val name = "Streamwish"
    override val mainUrl = "https://ghbrisk.com"
    override val requiresReferer = true
}