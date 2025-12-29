package com.sashkomusic.mainagent.infrastracture.client.songlink;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class SonglinkClient {

    private final RestClient client;

    public SonglinkClient(RestClient.Builder builder) {
        this.client = builder
                .baseUrl("https://api.song.link/v1-alpha.1")
                .defaultHeader("User-Agent", "SashkoMusicBot/1.0")
                .build();
    }

    @CircuitBreaker(name = "songlinkClient", fallbackMethod = "getLinksFallback")
    @Retry(name = "songlinkClient")
    public SonglinkResponse getLinks(String youtubeUrl) {
        log.info("Fetching streaming links from Songlink for YouTube URL: {}", youtubeUrl);

        try {
            var response = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/links")
                            .queryParam("url", youtubeUrl)
                            .build())
                    .retrieve()
                    .body(SonglinkResponse.class);

            if (response == null || response.linksByPlatform() == null) {
                log.warn("Empty response from Songlink API");
                return null;
            }

            log.info("Successfully fetched {} platform links", response.linksByPlatform().size());
            return response;

        } catch (Exception ex) {
            log.error("Error fetching from Songlink API: {}", ex.getMessage());
            throw ex;
        }
    }

    public SonglinkResponse getLinksFallback(String youtubeUrl, Exception e) {
        log.warn("Songlink getLinks fallback triggered for URL '{}': {}",
            youtubeUrl, e.getMessage());
        return null;
    }
}
