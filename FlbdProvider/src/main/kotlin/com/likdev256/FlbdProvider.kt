package com.likdev256

//import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.jsoup.nodes.Element
import com.lagradost.nicehttp.NiceResponse
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import okhttp3.FormBody
import java.net.URI

class FlbdProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://subsking.com"
    override var name = "FLBD"
    override val hasMainPage = true
    override var lang = "bn"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override val mainPage = mainPageOf(
        "$mainUrl/movies/" to "Movies",
        "$mainUrl/tvshows/" to "TV Shows",
        "$mainUrl/genre/action-adventure/" to "Action & Adeventure",
        "$mainUrl/genre/comedy/" to "Comedy",
        "$mainUrl/genre/crime/" to "Crime",
        "$mainUrl/genre/drama/" to "Drama",
        "$mainUrl/genre/mystery/" to "Mystery",
        "$mainUrl/genre/sci-fi-fantasy/" to "Sci-Fi & Fantasy"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = if (page == 1) {
            app.get(request.data).document
        } else {
            app.get(request.data + "/page/$page/").document
        }

        //Log.d("Document", request.data)
        val home = if (request.data.contains("genre")) {
            document.select("div.items > article").mapNotNull {
                it.toSearchResult()
            }
        } else {
            document.select("#archive-content > article").mapNotNull {
                it.toSearchResult()
            }
        }

        return HomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        //Log.d("Got","got here")
        val title = this.selectFirst("div.data > h3 > a")?.text()?.toString()?.trim() ?: return null
        //Log.d("title", title)
        val href = fixUrl(this.selectFirst("div.data > h3 > a")?.attr("href").toString())
        //Log.d("href", href)
        val posterUrl = fixUrlNull(this.selectFirst("div.poster > img")?.attr("src"))
        //Log.d("posterUrl", posterUrl.toString())
        //Log.d("QualityN", qualityN)
        val quality = getQualityFromString(this.select("div.poster > div.mepo > span").text().toString())
        //Log.d("Quality", quality.toString())
        return if (href.contains("Movie")) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        //Log.d("document", document.toString())

        return document.select("div.result-item").mapNotNull {
            val title = it.selectFirst("article > div.details > div.title > a")?.text().toString().trim()
            //Log.d("title", titleS)
            val href = fixUrl(it.selectFirst("article > div.details > div.title > a")?.attr("href").toString())
            //Log.d("href", href)
            val posterUrl = fixUrlNull(it.selectFirst("article > div.image > div.thumbnail > a > img")?.attr("src"))
            //Log.d("posterUrl", posterUrl.toString())
            //Log.d("QualityN", qualityN)
            val quality = getQualityFromString(it.select("div.poster > div.mepo > span").text().toString())
            //Log.d("Quality", quality.toString())
            val type = it.select("article > div.image > div.thumbnail > a > span").text().toString()
            if (type.contains("Movie")) {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = posterUrl
                    this.quality = quality
                }
            } else {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = posterUrl
                    this.quality = quality
                }
            }
        }
    }

    private suspend fun getEmbed(postid: String?, nume: String, type:String, referUrl: String?): NiceResponse {
        val body = FormBody.Builder()
            .addEncoded("action", "doo_player_ajax")
            .addEncoded("post", postid.toString())
            .addEncoded("nume", nume)
            .addEncoded("type", "movie")
            .build()

        return app.post(
            "$mainUrl/wp-admin/admin-ajax.php",
            requestBody = body,
            referer = referUrl
        )
    }

    data class EmbedUrl (
        @JsonProperty("embed_url") var embedUrl : String?,
        @JsonProperty("type") var type : String?
    )

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        //Log.d("Doc", doc.toString())
        val title = doc.selectFirst("div.sheader > div.data > h1")?.text()?.toString()?.trim()
            ?: return null
        //Log.d("titleL", titleL)
        //Log.d("title", title)
        val poster = fixUrlNull(doc.selectFirst("div.poster > img")?.attr("src"))
        val bgposter = fixUrlNull(
            doc.selectFirst("div.g-item:nth-child(1) > a:nth-child(1) > img:nth-child(1)")
                ?.attr("data-src").toString()
        )
        //Log.d("bgposter", bgposter.toString())
        //Log.d("poster", poster.toString())
        val tags = doc.select("div.sgeneros > a").map { it.text() }
        val year =
            doc.selectFirst("span.date")?.text()?.toString()?.substringAfter(",")?.trim()?.toInt()
        //Log.d("year", year.toString())
        val description = doc.selectFirst("div.wp-content > p")?.text()?.trim()
        val type = if (url.contains("movies")) TvType.Movie else TvType.TvSeries
        //Log.d("desc", description.toString())
        val trailer = if (type == TvType.Movie)
            fixUrlNull(
                getEmbed(
                    doc.select("#player-option-trailer").attr("data-post").toString(),
                    "trailer",
                    "movie",
                    url
                ).parsed<EmbedUrl>().embedUrl
            )
        else fixUrlNull(doc.select("iframe.rptss").attr("src").toString())
        //Log.d("trailer", trailer.toString())
        val rating = doc.select("span.dt_rating_vgs").text().toRatingInt()
        //Log.d("rating", rating.toString())
        val duration =
            doc.selectFirst("span.runtime")?.text()?.toString()?.removeSuffix(" Min.")?.trim()
                ?.toInt()
        //Log.d("dur", duration.toString())
        val actors =
            doc.select("div.person").map {
                ActorData(
                    Actor(
                        it.select("div.data > div.name > a").text().toString(),
                        it.select("div.img > a > img").attr("src").toString()
                    ),
                    roleString = it.select("div.data > div.caracter").text().toString(),
                )
            }
        val recommendations = doc.select("#dtw_content_related-2 article").mapNotNull {
            it.toSearchResult()
        }

        val episodes = ArrayList<Episode>()
        doc.select("#seasons ul.episodios").mapIndexed { seasonNum, me ->
            me.select("li").mapIndexed { epNum, it ->
                episodes.add(
                    Episode(
                        data = it.select("div.episodiotitle > a").attr("href"),
                        name = it.select("div.episodiotitle > a").text(),
                        season = seasonNum+1,
                        episode = epNum+1,
                        posterUrl = it.select("div.imagen > img").attr("src")
                    )
                )
            }
        }

        return if (type == TvType.Movie) {
            newMovieLoadResponse(title, url, TvType.Movie, url+","+doc.select("#player-option-1").attr("data-post").toString()) {
                this.posterUrl = poster?.trim()
                this.backgroundPosterUrl = bgposter?.trim()
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.duration = duration
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster?.trim()
                this.backgroundPosterUrl = bgposter?.trim()
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.duration = duration
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer?.toString())
            }
        }
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var url = ""
        var referer = ""
        if(data.contains(",")) {
            referer = data.substringBefore(",")
            url = fixUrlNull(
                    getEmbed(
                        data.substringAfter(","),
                        "1",
                        "movie",
                        referer
                    ).parsed<EmbedUrl>().embedUrl
                ).toString()
        } else {
            referer = data
            url = fixUrlNull(
                    getEmbed(
                        app.get(data).document.select("#player-option-1").attr("data-post").toString(),
                        "1",
                        "tv",
                        referer
                    ).parsed<EmbedUrl>().embedUrl
                ).toString()
        }
        val main = getBaseUrl(url)
        
        //Log.d("embedlink", url)
        val KEY = "4VqE3#N7zt&HEP^a"

        val master = Regex("MasterJS\\s*=\\s*'([^']+)").find(
            app.get(
                url,
                referer = referer
            ).text
        )?.groupValues?.get(1)
        val encData = AppUtils.tryParseJson<AESData>(base64Decode(master ?: return true))
        val decrypt = cryptoAESHandler(encData ?: return true, KEY, false)
        //Log.d("decrypt", decrypt)

        val source = Regex("""sources:\s*\[\{"file":"([^"]+)""").find(decrypt)?.groupValues?.get(1)
        val tracks = Regex("""tracks:\s*\[(.+)]""").find(decrypt)?.groupValues?.get(1)

        // required
        val headers = mapOf(
            "Accept" to "*/*",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Origin" to main,
        )

        callback.invoke(
            ExtractorLink(
                name,
                name,
                source ?: return true,
                "$main/",
                Qualities.Unknown.value,
                headers = headers,
                isM3u8 = true
            )
        )

        AppUtils.tryParseJson<List<Tracks>>("[$tracks]")
            ?.filter { it.kind == "captions" }?.map { track ->
                subtitleCallback.invoke(
                    SubtitleFile(
                        track.label ?: "",
                        track.file ?: return@map null
                    )
                )
            }
        return true
    }


    private fun cryptoAESHandler(
        data: AESData,
        pass: String,
        encrypt: Boolean = true
    ): String {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val spec = PBEKeySpec(
            pass.toCharArray(),
            data.salt?.hexToByteArray(),
            data.iterations?.toIntOrNull() ?: 1,
            256
        )
        val key = factory.generateSecret(spec)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        return if (!encrypt) {
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key.encoded, "AES"),
                IvParameterSpec(data.iv?.hexToByteArray())
            )
            String(cipher.doFinal(base64DecodeArray(data.ciphertext.toString())))
        } else {
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(key.encoded, "AES"),
                IvParameterSpec(data.iv?.hexToByteArray())
            )
            base64Encode(cipher.doFinal(data.ciphertext?.toByteArray()))
        }
    }

    private fun String.hexToByteArray(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }

            .toByteArray()
    }

    data class AESData(
        @JsonProperty("ciphertext") val ciphertext: String? = null,
        @JsonProperty("iv") val iv: String? = null,
        @JsonProperty("salt") val salt: String? = null,
        @JsonProperty("iterations") val iterations: String? = null,
    )

    data class Tracks(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("kind") val kind: String? = null,
    )
}
