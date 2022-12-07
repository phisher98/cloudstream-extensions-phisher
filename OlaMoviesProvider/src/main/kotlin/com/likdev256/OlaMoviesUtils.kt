package com.likdev256

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mapper
import com.lagradost.nicehttp.JsonAsString

suspend fun bypassAd(url: String): List<String> {
    val urls = mutableListOf<String>()
    urls += with(url) {
        when {
            contains("ser2.crazyblog") -> bypassCrazyBlogs(url)
            else -> {
                bypassAdLinks(url)
            }
        }
    }
    println("urls: $urls")
    return urls
}

private suspend fun bypassAdLinks(link: String): String {
    val apiUrl = "https://api.emilyx.in/api/bypass"
    val type = listOf("rocklinks", "dulinks", "ez4short")
    val values = mapOf("type" to type, "url" to link)
    val json = mapper.writeValueAsString(values)
    return app.post(
        url = apiUrl,
        headers = mapOf(
            "Content-Type" to "application/json"
        ),
        json = JsonAsString(json)
    ).parsed<OlaMoviesProvider.Response>().url
}

private suspend fun bypassCrazyBlogs(link: String): String {
    val domain = "https://ser3.crazyblog.in"
    println(link.substringAfterLast("/"))
    val html = app.get("$domain/${link.substringAfterLast("/")}")
    val cookies = html.headers.filter { it.first.contains("set-cookie") }.toString()
    println(cookies)
    val (AppSession, csrfToken, app_visitor) = cookies.split("),").map {
        println("it: $it")
        it.substringAfter("=").substringBefore("; path")
    }
    val document = html.document
    val data = document.select("#go-link input")
        .mapNotNull { it.attr("name").toString() to it.attr("value").toString() }.toMap()

    
    //println(data)
    val response = app.post(
        url = "$domain/links/go",
        headers = mapOf(
            "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
            "X-Requested-With" to "XMLHttpRequest",
            "Accept" to "application/json, text/javascript, */*; q=0.01",
            "Accept-Language" to "en-US,en;q=0.5",
        ),
        cookies = mapOf(
            "app_visitor" to app_visitor,
            "AppSession" to AppSession,
            "csrfToken" to csrfToken,

        ),
        data =  data,
        referer = "$domain/${link.substringAfterLast("/")}"
    )
    println(response)
    return ""
}