package com.PublicSportsIPTV

import com.fasterxml.jackson.annotation.JsonProperty

data class Root(
    val type: String,
    @JsonProperty("generated_by")
    val generatedBy: String,
    @JsonProperty("total_mathes")
    val totalMathes: Long,
    @JsonProperty("last_upaded")
    val lastUpaded: String,
    val matches: List<Match>,
)

data class Match(
    @JsonProperty("event_catagory")
    val eventCatagory: String,
    @JsonProperty("event_name")
    val eventName: String,
    @JsonProperty("match_id")
    val matchId: Long,
    @JsonProperty("match_name")
    val matchName: String,
    @JsonProperty("team_1")
    val team1: String,
    @JsonProperty("team_1_flag")
    val team1Flag: String,
    @JsonProperty("team_2")
    val team2: String,
    @JsonProperty("team_2_flag")
    val team2Flag: String,
    val banner: String,
    @JsonProperty("stream_link")
    val streamLink: String,
)
