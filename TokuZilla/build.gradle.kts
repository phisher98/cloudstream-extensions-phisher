// use an integer for version numbers
version = 1

cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them

    description = "Stream tokusatsu content including Power Ranger, Kamen Rider, Super Sentai, Metal Heroes, and other Japanese special effect series with English subs"
    authors = listOf("KaifTaufiq,Phisher98")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "TvSeries",
        "Movie",
        "Anime"
    )

    iconUrl = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/master/TokuZilla/icon.png"
    isCrossPlatform = false
}