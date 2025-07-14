// use an integer for version numbers
version = 6


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

    iconUrl = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/Icons/IPTV.png"

    isCrossPlatform = true
}
