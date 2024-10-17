// use an integer for version numbers
version = 4


cloudstream {
    // All of these properties are optional, you can safely remove them
    language = "hi"
    description = "IPTV Player"
    authors = listOf("Phisher98,Adippe")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "Live",
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=github.com&sz=%size%"
}
