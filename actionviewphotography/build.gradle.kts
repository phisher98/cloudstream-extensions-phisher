version = 5

cloudstream {
    authors     = listOf("Hindi Provider")
    language    = "en"
    description = "NSFW Search Videos"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("NSFW")
    iconUrl = "https://actionviewphotography.com/static/extend/light/favicon.ico\n"
}
