// use an integer for version numbers
version = 24


cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them

    description = "Sport Live Stream"
    authors = listOf("Hexated")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 0 // will be 3 if unspecified
    tvTypes = listOf(
        "Live",
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=dlhd.so&sz=%size%"
}
