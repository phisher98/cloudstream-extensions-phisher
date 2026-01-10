// use an integer for version numbers
version = 2


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
    iconUrl = "https://dudefilms.media/wp-content/uploads/2020/06/cropped-cropped-DudeFilms-LOGO-v02-1.png"

    isCrossPlatform = false
}
