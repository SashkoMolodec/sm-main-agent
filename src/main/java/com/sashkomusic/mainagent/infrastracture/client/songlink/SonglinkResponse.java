package com.sashkomusic.mainagent.infrastracture.client.songlink;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SonglinkResponse(
        Map<String, PlatformLink> linksByPlatform
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlatformLink(
            String url
    ) {
    }
}
