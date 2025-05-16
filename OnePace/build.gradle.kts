version = 8


cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them

    description = "One Pace"
    authors = listOf("KillerDogeEmpire,Phisher98")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "Anime"
    )
    iconUrl = "https://raw.githubusercontent.com/Rowdy-Avocado/Rowdycado-Extensions/master/logos/onepace.png"

    isCrossPlatform = true
}
