version = 5

cloudstream {
    language = "hi"

    description = "Contains Bharatiya Movies & Webseries from Indian Film Industry and Dubbed Hollywood content, Max Resolution is 720p"

    authors = listOf("AryanInvader,Phisher98")

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
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=prmovies.pet&sz=%size%"

    isCrossPlatform = true
}
