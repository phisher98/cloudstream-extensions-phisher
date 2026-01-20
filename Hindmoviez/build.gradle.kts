// use an integer for version numbers
version = 1


cloudstream {
    description = "Watch Movies & TvSeries (Multi-Lang)"
    authors = listOf("Phisher98")

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
    )
    language = "hi"
    iconUrl = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/Icons/hindmoviez.png"

    isCrossPlatform = false
}
