// use an integer for version numbers
version = 11


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Anplay Hindi Animes"
    language    = "hi"
    authors = listOf("HindiProviders")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // will be 3 if unspecified

    // List of video source types. Users are able to filter for extensions in a given category.
    // You can find a list of avaliable types here:
    // https://recloudstream.github.io/cloudstream/html/app/com.lagradost.cloudstream3/-tv-type/index.html
    tvTypes = listOf("Movie,Anime,Cartoon")
    iconUrl="https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQFFWqooQh6c2ojQl0JG9DOzExN3rZMwncShzuvzAUA9A&s"
}
