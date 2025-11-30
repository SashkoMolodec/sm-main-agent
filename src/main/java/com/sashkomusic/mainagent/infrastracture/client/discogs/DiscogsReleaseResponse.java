package com.sashkomusic.mainagent.infrastracture.client.discogs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DiscogsReleaseResponse(
        Long id,
        String title,
        List<Track> tracklist,
        @JsonProperty("main_release") Long mainRelease
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Track(
            String position,
            String title,
            String duration
    ) {
    }
}
