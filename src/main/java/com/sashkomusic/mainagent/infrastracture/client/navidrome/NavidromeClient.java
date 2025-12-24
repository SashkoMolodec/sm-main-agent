package com.sashkomusic.mainagent.infrastracture.client.navidrome;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sashkomusic.mainagent.config.NavidromeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Component
public class NavidromeClient {

    private static final Logger log = LoggerFactory.getLogger(NavidromeClient.class);

    private final RestClient restClient;
    private final NavidromeConfig config;

    public NavidromeClient(RestClient.Builder restClientBuilder, NavidromeConfig config) {
        this.restClient = restClientBuilder.build();
        this.config = config;
    }

    public void triggerScan(String folderPath) {
        try {
            URI uri = buildScanUri(folderPath);

            log.info("Triggering Navidrome scan for folder: {}", folderPath);
            log.debug("Scan request URL: {}", uri);

            restClient.get()
                    .uri(uri)
                    .retrieve()
                    .toBodilessEntity();

            log.info("✓ Successfully triggered Navidrome scan for: {}", folderPath);

        } catch (Exception e) {
            log.warn("⚠️  Failed to trigger Navidrome scan for {}: {}. Navidrome will scan automatically on schedule.",
                    folderPath, e.getMessage());
            log.debug("Navidrome scan error details", e);
        }
    }

    public void triggerFullScan() {
        triggerScan(null);
    }

    public String getCurrentlyPlayingTrackPath() {
        try {
            URI uri = buildCurrentlyPlayingUri();

            log.info("Fetching currently playing track from Navidrome");

            NavidromeNowPlayingResponse response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(NavidromeNowPlayingResponse.class);

            if (response != null &&
                    response.subsonicResponse() != null &&
                    response.subsonicResponse().nowPlaying() != null &&
                    response.subsonicResponse().nowPlaying().entry() != null &&
                    !response.subsonicResponse().nowPlaying().entry().isEmpty()) {

                String path = response.subsonicResponse().nowPlaying().entry().getFirst().path();
                log.info("✓ Successfully fetched currently playing track: {}", path);
                return path;
            }

            log.warn("No tracks currently playing in Navidrome");
            return null;

        } catch (Exception e) {
            log.error("Failed to fetch currently playing track: {}", e.getMessage(), e);
            return null;
        }
    }

    private URI buildScanUri(String folderPath) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(config.getBaseUrl())
                .path("/rest/startScan")
                .queryParam("u", config.getUsername())
                .queryParam("p", config.getPassword())
                .queryParam("v", config.getApiVersion())
                .queryParam("c", config.getClientName())
                .queryParam("f", "json");

        if (folderPath != null && !folderPath.isEmpty()) {
            builder.queryParam("target", folderPath);
        }

        return builder.build().toUri();
    }

    private URI buildCurrentlyPlayingUri() {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(config.getBaseUrl())
                .path("/rest/getNowPlaying")
                .queryParam("u", config.getUsername())
                .queryParam("p", config.getPassword())
                .queryParam("v", config.getApiVersion())
                .queryParam("c", config.getClientName())
                .queryParam("f", "json");

        return builder.build().toUri();
    }

    public record NavidromeNowPlayingResponse(
            @JsonProperty("subsonic-response") SubsonicResponse subsonicResponse
    ) {
        public record SubsonicResponse(NowPlaying nowPlaying) {}
        public record NowPlaying(List<Entry> entry) {}
        public record Entry(String path) {}
    }
}