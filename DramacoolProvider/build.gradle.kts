// use an integer for version numbers
version = 2


cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them

//    description = "Kdrama"
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
        "AsianDrama",
    )

    iconUrl = "https://mir-s3-cdn-cf.behance.net/projects/404/5488d0187090001.Y3JvcCw2MDAwLDQ2OTMsMCw2NTM.jpg"
}
