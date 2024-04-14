version = 8

cloudstream {
    authors     = listOf("HindiProvider")
    language    = "en"
    description = "Spankbang"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("NSFW")
    iconUrl = "https://logos-world.net/wp-content/uploads/2023/01/SpankBang-Logo.png"
}
