// use an integer for version numbers
version = 6


cloudstream {
    //description = "Movie website in Bangladesh"
    authors = listOf("Phisher98,Redowan")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // will be 3 if unspecified

    tvTypes = listOf(
        "Movie",
        "TvSeries",
        "Anime"
    )
    language = "hi"

    iconUrl = "https://hdhub4u.soccer/wp-content/uploads/2021/05/hdhub4ulogo.png"

    isCrossPlatform = true
}
