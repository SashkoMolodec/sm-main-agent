package com.sashkomusic.mainagent.infrastracture.client.musicbrainz;

import com.sashkomusic.mainagent.domain.model.MusicSearchMetadata;
import com.sashkomusic.mainagent.domain.service.MusicMetadataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import com.sashkomusic.mainagent.infrastracture.client.musicbrainz.exception.exception.SearchNotCompleteException;
import com.sashkomusic.mainagent.infrastracture.client.musicbrainz.exception.exception.SearchNotFoundException;

@Slf4j
@Component
public class MusicBrainzClient implements MusicMetadataService {

    private final RestClient client;

    public MusicBrainzClient(RestClient.Builder builder) {
        this.client = builder
                .baseUrl("https://musicbrainz.org/ws/2")
                .build();
    }

    @Retryable(
            retryFor = SearchNotCompleteException.class,
            maxAttempts = 5,
            backoff = @Backoff(delay = 500)
    )
    @Override
    public MusicSearchMetadata search(String artist, String title) {
        log.info("Searching for release using MusicBrainz: artist={}, title={}", artist, title);

        String query = String.format("artist:%s AND release:%s", artist, title);

        String uri = UriComponentsBuilder.fromPath("/release/")
                .queryParam("query", query)
                .queryParam("fmt", "json")
                .build()
                .toUriString();

        try {
            var response = client.get()
                    .uri(uri)
                    .retrieve()
                    .body(MusicBrainzSearchResponse.class);

            if (response == null || response.count() == 0) {
                throw new SearchNotFoundException("Search did not return any results.");
            }

            return mapToDomain(response, artist, title);
        } catch (Exception ex) {
            throw new SearchNotCompleteException("Search not complete.");
        }
    }

    private MusicSearchMetadata mapToDomain(MusicBrainzSearchResponse response, String artist, String title) {
        return new MusicSearchMetadata(title, artist, response.count(), response.getMinTrackCount());
    }
}
