package com.phisher98

data class MyFlixerParser(
    val sources: List<Source>,
    val tracks: List<Track>
) {
    data class Source(
        val `file`: String,
        val type: String
    )

    data class Track(
        val `file`: String,
        val label: String
    )
}