// use an integer for version numbers
version = 5

cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Telugu Movies and TV Series"
    language = "te"
    authors = listOf("Phisher98")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // will be 3 if unspecified

    // List of video source types. Users are able to filter for extensions in a given category.
    // You can find a list of available types here:
    // https://recloudstream.github.io/cloudstream/html/app/com.lagradost.cloudstream3/-tv-type/index.html
    tvTypes = listOf(
        "Movie",
        "TvSeries",
    )
    iconUrl = "https://telugumv.fun/wp-content/uploads/2024/05/20240509_195734-e1715265186634.png"

    isCrossPlatform = true
}
