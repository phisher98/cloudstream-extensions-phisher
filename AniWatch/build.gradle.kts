version = 2


cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them
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
        "Anime",
        "OVA",
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=aniwatchtv.to&sz=%size%"
    requiresResources = true
    isCrossPlatform = false
}
