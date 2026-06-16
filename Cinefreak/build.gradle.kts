// use an integer for version numbers
version = 8


cloudstream {
    authors = listOf("Phisher98")
    description ="Bangla/Hindi Movies/Series"
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
    language = "bn"
    iconUrl = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/Icons/cinefreak.png"

    isCrossPlatform = true
}
