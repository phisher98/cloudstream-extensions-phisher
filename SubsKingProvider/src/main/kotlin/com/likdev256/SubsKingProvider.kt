package com.likdev256

//import android.util.Log
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.jsoup.nodes.Element
import com.lagradost.nicehttp.NiceResponse
import okhttp3.FormBody
import java.util.*
import javax.crypto.*
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class SubsKingProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://subsking.com"
    override var name = "SubsKing"
    override val hasMainPage = true
    override var lang = "si"
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

    fun String.decodeFromHexString(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }

        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun decryptVideoUrl(cipherText: ByteArray, salt: ByteArray, iv: ByteArray, key: ByteArray): String {
        Log.d("mybadsalt", String(salt))
        Log.d("mybadiv", String(iv))
        Log.d("mybadkey", String(key))
        val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val keySpec = PBEKeySpec(String(key).toCharArray(), salt, 999, 32)
        val secretKey = secretKeyFactory.generateSecret(keySpec)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKeySpec = SecretKeySpec(secretKey.encoded, "AES")
        val ivParameterSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)
        val decodedBytes = cipher.doFinal(cipherText.copyOfRange(16, cipherText.size))
        return String(decodedBytes)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun chillxExctractor(url: String):String {
        val cipherData = AppUtils.parseJson<CipherData>(
            String(
                base64DecodeArray(
                    Regex("MasterJS\\s*=\\s*'([^']+)").find(app.get(url).text)?.groupValues?.get(1).toString()
                )
            )
        )
        val key = "4VqE3%23N7zt%26HEP%5Ea".toByteArray()
        val salt = cipherData.salt.toByteArray()
        val iv = cipherData.iv.toByteArray()
        val cipherText = cipherData.ciphertext.toByteArray()

        return decryptVideoUrl(cipherText, salt, iv, key)
    }

    private suspend fun getEmbed(postid: String?, nume: String, referUrl: String?): NiceResponse {
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

    data class CipherData (
        @JsonProperty("ciphertext" ) var ciphertext : String,
        @JsonProperty("iv"         ) var iv         : String,
        @JsonProperty("salt"       ) var salt       : String,
        @JsonProperty("iterations" ) var iterations : Int
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
                    doc.select("#report-video-button-field > input[name~=postid]").attr("value").toString(),
                    "trailer",
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

        /*val episodes = ArrayList<Episode>()
        doc.select("section.container > div.border-b").forEach { me ->
            val seasonNum = me.select("button > span").text()
            me.select("div.season-list > a").forEach {
                episodes.add(
                    Episode(
                        data = mainUrl + it.attr("href").toString(),
                        name = it.ownText().toString().removePrefix("Episode ").substring(2),//.replaceFirst(epName.first().toString(), ""),
                        season = titRegex.find(seasonNum)?.value?.toInt(),
                        episode = titRegex.find(it.select("span.flex").text().toString())?.value?.toInt()
                    )
                )
            }
        }*/

        //return if (type == TvType.Movie) {
        return newMovieLoadResponse(title, url, TvType.Movie, url+","+doc.select("#report-video-button-field > input[name~=postid]").attr("value").toString()) {
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
        /*} else {
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
        }*/
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val url = data.substringBefore(",")
        Log.d("mybadembedlink", url)
        //val postid = data.substringAfter(",")
        //Log.d("embedlink", postid)
        /*val Embedlink = getEmbed(
            postid,
            "1",
            link
        ).parsed<embedUrl>().embedUrl*/
        val doc = app.get(url).document
        doc.select("#playeroptionsul > li").map {
            Log.d("mybadembedlink", "got")
            val link = getEmbed(
                it.attr("data-post"),
                it.attr("data-nume"),
                ""
            ).parsed<EmbedUrl>().embedUrl

            if (link != null) {
                if (link.contains("beastx", true) || link.contains("chillx", true)) {
                    Log.d("mybadembedlink", chillxExctractor(link))
                }
            }
        }
        return true
    }
}
