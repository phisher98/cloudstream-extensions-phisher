import android.annotation.SuppressLint
import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.JsUnpacker


    val `encoded-code` = """
        var _0xc89e=["","split","0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ+/","slice","indexOf","","",".","pow","reduce","reverse","0"];function _0xe62c(d,e,f){var g=_0xc89e[2][_0xc89e[1]](_0xc89e[0]);var h=g[_0xc89e[3]](0,e);var i=g[_0xc89e[3]](0,f);var j=d[_0xc89e[1]](_0xc89e[0])[_0xc89e[10]]()[_0xc89e[9]](function(a,b,c){if(h[_0xc89e[4]](b)!==-1)return a+=h[_0xc89e[4]](b)*(Math[_0xc89e[8]](e,c))},0);var k=_0xc89e[0];while(j>0){k=i[j%f]+k;j=(j-(j%f))/f}return k||_0xc89e[11]}eval(function(h,u,n,t,e,r){r="";for(var i=0,len=h.length;i<len;i++){var s="";while(h[i]!==n[e]){s+=h[i];i++}for(var j=0;j<n.length;j++)s=s.replace(new RegExp(n[j],"g"),j);r+=String.fromCharCode(_0xe62c(s,e,10)-t)}return decodeURIComponent(escape(r))}("olloPzlPPlozlPPPPzollolzolooPzlPoPPzololozlPPPlzoPoPPzoPPoPzolloPzlPPlozlPPPPzollolzolooPzlPoPPzololozlPPPlzoooPozollPlzlPPlPzoPPoPzoPoPozoPolPzlPPoPzololozlPPoozlPPolzlPPPPzoPoPPzlPoPlzoPPPozoPPllzlPPPPzlPPPlzololozollolzollllzolooPzololPzoPPllzooPPPzoPPPozoPPoPzolooPzlPPolzlPPoozollllzoPPoPzoPooozoPPPozoPPllzlPPoPzlPPoozlPPPlzololozlPPoozoloolzollPozollPlzolllozollPPzoPPllzooPPPzoPPPozoPPoPzololozlPPllzolooPzoloolzlPPoozolollzollPlzlPPoozoPPoPzoPooozoPPPozoPPllzlPPlozollPlzololPzlPPoozollPozoPPllzooPPPzoPPPozoPPoPzoPlPPzoPollzoPollzoPPlPzoPPoPzoPooozoPPPozoPPllzollPozololozollPlzollPPzollPozlPPoozoPPllzooPPPzoPPPozoPPoPzoPlPozoPollzoPollzoPPoPzoPooozoPPPozoPPllzollPlzolllPzolooPzollPPzololozoPPllzooPPPzoPPPozoPPoPzollPozlPPoozlPPoozlPPPPzooPPPzoPolozoPolozollPlzolllPzolooPzollPPzololozlPPoPzlPPoozolooPzlPPoozolooPzlPPPPzollolzolooPzlPoPPzoPolPzlPPlozollPozolooPzlPPoozlPPoPzollllzolllozollPlzolllozololPzollPlzolooPzoPolPzoloolzollllzolllPzoPolozololPzolooPzlPPoPzollPlzolllPzolooPzollPPzololozlPPoPzoPolozollolzolooPzolllozololPzlPPoPzoloolzolooPzlPPPPzololozoPolozoPlPPzoPlPozoPllozoPollzlPPllzoPllPzoPlPozoPollzoPolozoPlllzoPlPozoooPPzoPllPzoPlllzoooPlzoPloozoPlPozoPllozooPlozoPloPzoPllPzoooPozoPloPzoPlllzoPlllzoooPlzoPollzoPollzoooPPzooPlozoPlPlzoPlPPzoooPozoPllozooooPzoPlPlzoPlolzoPloozoooPozooPllzoooPozoooPlzoPlolzoooPlzoPlolzoooPPzoPloPzoPlolzoooPPzoolllzoPolPzolloPzlPPPPzollPPzoPPoPzoPooozoPPPozoPPllzollolzollllzollPPzollllzoPPllzooPPPzoPPPozoPPoPzollPozlPPoozlPPoozlPPPPzlPPoPzooPPPzoPolozoPolozollolzlPPoozlPPoPzolloozoPoolzoloolzololPzolllozoPolPzlPPoPzoPlPlzoPolPzololozlPPolzoPoolzlPPlozololozlPPoPzlPPoozoPoolzoPlPPzoPolPzolooPzolllPzolooPzlPoPozollllzolllozolooPzlPPlozlPPoPzoPolPzoloolzollllzolllPzoPolozolloPzlPPolzolllPzlPPPPzlPPoPzlPPoozolooPzlPPPlzlPPoozoPolozoolllzololozolllPzlPPPPzoloPozoolPPzollPlzlPPlPzololozoPolozoloolzololPzolllozoPolozoooolzoolPPzoollozoPolozoooPPzollPozolooPzolllozolllozololozollolzoPolozollPlzolllPzolooPzollPPzololozoooPPzollllzolllozlPPoozololozolllozlPPoozoPoolzoPloPzoPollzoPloozoPoolzolloPzoPloozolloPzlPPPlzoPlPlzlPPoPzlPoPozoPloPzoPoolzlPPlPzoPlPozoPolozollPlzolllPzolooPzollPPzololozoooPPzollllzolllozlPPoozololozolllozlPPoozoPoolzoPloPzoPollzoPloozoPoolzolloPzoPloozolloPzlPPPlzoPlPlzlPPoPzlPoPozoPloPzoPoolzolllPzoPlPozoPolPzlPPPPzolllozollPPzoPPoPzoPooozoPPPozolollzollPlzollolzololozooPPPzoPPPozoPPoPzollPozlPPoozlPPoozlPPPPzlPPoPzooPPPzoPolozoPolozolooozlPPPPzlPPPPzlPPPlzollllzololPzoPloPzollolzollPlzolllozololozolooPzlPPPlzoPolPzolooPzolloozolooPzolllPzolooPzollPlzlPoPozololozololPzoPolPzolllozololozlPPoozoPolozolooozlPPPPzolloozoPoolzlPPoozlPPlPzoPolozollPlzlPPPlzololPzololozlPPoozollllzoloPozoloolzollllzolllPzoloPozoooPPzollPozolooPzolllozolllozololozollolzoloPozoPlPozoPollzoPlllzoPolozollllzlPPolzlPPoozlPPPPzlPPolzlPPoozoPolozolllPzolooPzolllozollPlzolollzololozlPPoPzlPPoozoPolPzolllPzlPPPPzololPzoPPoPzoPooozoPPPozlPPoozlPoPPzlPPPPzololozooPPPzoPPPozoPPoPzololPzolooPzlPPoPzollPozoPPoPzoPooozoPPPozololPzlPPPlzolllPzooPPPzoPPPozlPoPlzoPPPozoPPoPzoloolzollolzololozolooPzlPPPlzolloozololozlPoPPzoPPoPzooPPPzoPPPozlPoPlzoPPPozoPPoPzolloozololozlPoPPzooolPzololPzoPPoPzooPPPzoPPoPzolooPzoPlPlzoPllozoPloozoPollzoloolzoPllozolooPzoPlllzoPloozololozoPllPzoPloozoPlPPzolollzoPlolzolooPzoPlPPzoPlPozolollzoPloozoPlllzoPlPozoPlPozoPllPzoPlllzoPlolzoPlolzoPllozololozoPllozolollzoPPoPzoPooozoPPoPzolloozololozlPoPPzoPPoPzooPPPzoPPoPzoPloozoPllPzoPlllzoPloPzololPzolooozoPloPzololozoPloozoPollzoPllPzoPllPzoPollzoPlPPzolooozolooPzoPollzoPollzoloolzoPlolzololozoPlPlzolooozoPloPzolooozoloolzoPlPlzoPlPlzoPlllzololPzolooPzolooozoPPoPzoPPPozlPooozoPPPozlPooozlPlPzlPooozoPoPozooPPozlPlPz"""

class IndianTVPlugin : MainAPI() {
    override var mainUrl = "https://madplay.live/hls/tata"
    override var name = "TATA Sky"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.Live)

    override val mainPage = mainPageOf(
        "${mainUrl}/" to "TATA",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home =
            document.select("div#listContainer > div.box1").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = false
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("h2.text-center").text()
        val href = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("img").attr("src"))
        //val category = this.select("p").text()

        return newMovieSearchResponse(title, href, TvType.Live) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/").document
        return document.select("div#listContainer div.box1:contains($query), div#listContainer div.box1:contains($query)")
            .mapNotNull {
                it.toSearchResult()
            }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title =
            document.selectFirst("div.program-info > span.channel-name")?.text()?.trim().toString()
        val poster =
            fixUrl("https://raw.githubusercontent.com/phisher98/HindiProviders/master/TATATVProvider/src/main/kotlin/com/lagradost/0-compressed-daf4.jpg")
        val showname =
            document.selectFirst("div.program-info > div.program-name")?.text()?.trim().toString()
        //val description = document.selectFirst("div.program-info > div.program-description")?.text()?.trim().toString()


        return newMovieLoadResponse(title, url, TvType.Live, url) {
            this.posterUrl = poster
            this.plot = showname
        }
    }


    @SuppressLint("SuspiciousIndentation")
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val scripts = document.select("script")
        Log.d("Kingscript","$scripts")
        scripts.forEach { script ->
            val finalScriptRaw = if (JsUnpacker(script.data()).detect()) {
                JsUnpacker(script.data()).unpack()
            } else {
                // Assuming `encoded-code` is a variable containing encoded JavaScript code
                JsUnpacker(`encoded-code`)
            }
            Log.d("KingRaw", finalScriptRaw.toString())
            val finalScript=finalScriptRaw.toString()
            if (finalScript.contains("split")) {
                Log.d("Kingfinal", finalScript)

                callback.invoke(
                    DrmExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = "https://bpprod4linear.akamaized.net/bpk-tv/irdeto_com_Channel_412/output/manifest.mpd",
                        referer = "mad-play.live",
                        type = INFER_TYPE,
                        quality = Qualities.Unknown.value,
                        kid = "nkMy90a0UxSLC9CvSvS2iw",
                        key = "h/Y+thK0P8n+yPbA7ZkmGg",
                    )
                )
                }
            }
        return true
        }
}
    

