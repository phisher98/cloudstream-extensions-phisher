package com.Phisher98

//import android.util.Log
import android.annotation.SuppressLint
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import java.time.Year

class Hdmovie6 : Hdmovie2() {

    override var mainUrl = "https://hdmovie6.com"
    override var name = "Hdmovie6"

}
