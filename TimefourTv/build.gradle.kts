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
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "Live",
    )

    iconUrl = "https://cdn.discordapp.com/attachments/1109266606292488297/1193088870212976640/Untitled.jpg?ex=65ec0b19&is=65d99619&hm=0eaf0f1926b6eb787b80c2eb3000ec9d77e2e706ab0601cad053ad8f677b8cc8&"
}
