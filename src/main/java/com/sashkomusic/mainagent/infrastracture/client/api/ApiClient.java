package com.sashkomusic.mainagent.infrastracture.client.api;

import com.sashkomusic.mainagent.infrastracture.client.api.dto.TrackDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Slf4j
@Component
public class ApiClient {

    private final RestClient restClient;

    public ApiClient(RestClient.Builder restClientBuilder, @Value("${sm-api.base-url}") String apiBaseUrl) {
        this.restClient = restClientBuilder
                .baseUrl(apiBaseUrl).build();
    }

    @CircuitBreaker(name = "apiClient", fallbackMethod = "findTrackByArtistAndTitleFallback")
    @Retry(name = "apiClient")
    public Optional<TrackDto> findTrackByArtistAndTitle(String artist, String title) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/tracks/search")
                            .queryParam("artist", artist)
                            .queryParam("title", title)
                            .build())
                    .retrieve()
                    .body(TrackDto.class));
        } catch (Exception e) {
            log.error("Failed to find track by artist '{}' and title '{}': {}", artist, title, e.getMessage());
            throw e;
        }
    }

    public Optional<TrackDto> findTrackByArtistAndTitleFallback(String artist, String title, Exception e) {
        log.warn("ApiClient findTrackByArtistAndTitle fallback triggered for '{}' - '{}': {}",
            artist, title, e.getMessage());
        return Optional.empty();
    }
}

