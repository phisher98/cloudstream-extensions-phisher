version = 1


cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them

    description = "Live TV and Live Events"
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
        "Live"
    )

    iconUrl = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/Icons/TVVV.png"

    isCrossPlatform = true
}
