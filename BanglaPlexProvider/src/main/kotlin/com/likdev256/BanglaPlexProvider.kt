package com.likdev256

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import com.lagradost.nicehttp.NiceResponse
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import okhttp3.FormBody
import java.net.URI

class BanglaPlexProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://banglaplex.fun"
    override var name = "BanglaPlex"
    override val hasMainPage = true
    override var lang = "bn"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    private suspend fun queryApi(page: Int, query: String): String {
        val body = FormBody.Builder()
            .addEncoded("action", "fetch_data")
            .addEncoded("minimum_rating", "0")
            .addEncoded("maximum_rating", "10")
            .addEncoded("sort", query)
            .addEncoded("page", "$page")
            .build()

        return app.post(
            "$mainUrl/filter_movies/$page",
            requestBody = body,
            referer = "$mainUrl/movies.html"
        ).parsed<Home>().movieList.replace("\\r", "").replace("\\n", "").replace("\\\"", "")
    }

    private suspend fun querysearchApi(query: String): NiceResponse {
        return app.get(
            "$mainUrl/home/autocompleteajax?term=$query",
            referer = "$mainUrl/movies.html"
        )
    }

    private val trendingMovies = "total_view"
    private val ratingMovies   = "rating"
    private val recentMovies   = "release"
    private val randomMovies   = "rand"
    private val movies         = "az"

    override val mainPage = mainPageOf(
        trendingMovies to "Trending Movies",
        ratingMovies to "Best Rated Movies",
        recentMovies to "Recent Movies",
        randomMovies to "Random",
        movies to "Movies"
    )


    data class Home (
        @JsonProperty("movie_list"      ) var movieList      : String,
        @JsonProperty("pagination_link" ) var paginationLink : String?
    )

    data class SearchResult (
        @JsonProperty("title" ) var title : String,
        @JsonProperty("type"  ) var type  : String,
        @JsonProperty("image" ) var image : String,
        @JsonProperty("url"   ) var url   : String
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val query = request.data.format(page)
        Log.d("Mytest", queryApi(
            page,
            query
        ))
        val homeList = Jsoup.parse(
            queryApi(
                page,
                query
            )
        )
        val home = homeList.select("div.col-sm-4").map {
            it.toSearchResult()
        }
        return HomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("div.movie-img > div.movie-title > h3 > a").text().trim()
        //Log.d("title", title)
        val href = fixUrl(this.select("div.movie-img > a.ico-play").attr("href").toString())
        //Log.d("href", href)
        val posterRegex = Regex("url\\('(.*)\\);")
        val posterUrl = fixUrlNull(posterRegex.find(this.select("div.latest-movie-img-container").attr("style"))?.groups?.get(1)?.value.toString())
        //Log.d("posterUrl", posterUrl.toString())
        val quality = getQualityFromString(this.select("div.video_quality_movie > span.label").text())
        //Log.d("Quality", quality.toString())

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = quality
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchList = querysearchApi(
            query
        ).parsed<ArrayList<SearchResult>>()
        //Log.d("document", document.toString())

        return searchList.map {
            val title = it.title
            //Log.d("title", title)
            val href = fixUrl(it.url)
            //Log.d("href", href)
            val posterUrl = fixUrlNull(it.image)
            //Log.d("posterUrl", posterUrl.toString())

            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.select("div.col-md-9 > div.row > div.col-md-12 > h1").text()
        //Log.d("title", title)
        val poster = fixUrlNull(doc.selectFirst("img.img-responsive")?.attr("src"))
        //Log.d("poster", poster.toString())
        val tags = doc.select("div.col-md-6 > p > a").map { it.text() }
        //Log.d("TTAAGG", tags.toString())
        //val year = doc.selectFirst("h1.px-5 span.text-gray-400")?.text().toString().removePrefix("(").removeSuffix(")").toInt()
        //Log.d("year", year.toString())
        val description = doc.selectFirst(".col-md-9 > div.row > div.col-md-12 > p:nth-child(5)")?.text()?.trim()
        //val trailer = fixUrlNull(document.select("iframe#iframe-trailer").attr("src"))
        //val rating = doc.select("span.text-xl").text().toRatingInt()
        val recommendations = doc.select("div.col-md-2").mapNotNull {
            it.toSearchResult()
        }
        val data = url+","+doc.select("iframe.responsive-embed-item").attr("src")

        return newMovieLoadResponse(title, url, TvType.Movie, data) {
            this.posterUrl = poster
            //this.year = year
            this.plot = description
            this.tags = tags
            //this.rating = rating
            this.recommendations = recommendations
            //addTrailer(trailer)
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
        val url = data.substringAfter(",")
        val referer = data.substringBefore(",")
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
