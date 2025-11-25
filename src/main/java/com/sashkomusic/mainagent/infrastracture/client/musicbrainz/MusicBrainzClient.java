package com.sashkomusic.mainagent.infrastracture.client.musicbrainz;

import com.sashkomusic.mainagent.domain.model.ReleaseMetadata;
import com.sashkomusic.mainagent.domain.service.MusicMetadataService;
import com.sashkomusic.mainagent.infrastracture.client.musicbrainz.exception.SearchNotCompleteException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class MusicBrainzClient implements MusicMetadataService {

    private final RestClient client;

    public MusicBrainzClient(RestClient.Builder builder) {
        this.client = builder
                .baseUrl("https://musicbrainz.org/ws/2")
                .defaultHeader("User-Agent", "SashkoMusicBot/1.0 ( contact@example.com )")
                .build();
    }

    @Override
    @Retryable(retryFor = SearchNotCompleteException.class, maxAttempts = 3, backoff = @Backoff(delay = 5000))
    public List<ReleaseMetadata> searchReleases(String query) {
        log.info("Searching with query: {}", query);

        try {
            var response = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/release")
                            .queryParam("query", query)
                            .queryParam("fmt", "json")
                            .queryParam("limit", defineResponseLimit(query))
                            .build())
                    .retrieve()
                    .body(MusicBrainzSearchResponse.class);

            if (response == null || response.releases() == null || response.releases().isEmpty()) {
                return List.of();
            }

            return mapToGroupedDomain(response.releases());

        } catch (Exception ex) {
            log.error("Error searching MusicBrainz candidates: {}", ex.getMessage());
            throw new SearchNotCompleteException("Search failed due to API error.");
        }
    }

    private List<ReleaseMetadata> mapToGroupedDomain(List<MusicBrainzSearchResponse.Release> releases) {
        Map<String, List<MusicBrainzSearchResponse.Release>> grouped = releases.stream()
                .filter(r -> r.releaseGroup() != null)
                .collect(Collectors.groupingBy(r -> r.releaseGroup().id()));

        return grouped.values().stream()
                .map(this::aggregateGroup)
                .sorted(
                        // 1. Рік: від новіших до старіших (DESC)
                        Comparator.comparing((ReleaseMetadata m) -> m.years().isEmpty() ? "0000" : m.years().getFirst()).reversed()

                                // 2. Score: від більшого до меншого (DESC)
                                // Важливо: ми передаємо компаратор всередину thenComparing!
                                .thenComparing(Comparator.comparingInt(ReleaseMetadata::score).reversed())

                                // 3. Назва: від коротшої до довшої (ASC)
                                .thenComparingInt(r -> r.title().length())
                )
                .toList();
    }

    private static int defineResponseLimit(String query) {
        if (!query.contains("artist") || !query.contains("release")) {
            return 100;
        } else return 1;
    }

    private ReleaseMetadata aggregateGroup(List<MusicBrainzSearchResponse.Release> groupReleases) {
        IntSummaryStatistics trackStats = groupReleases.stream()
                .mapToInt(this::getTrackCount)
                .filter(c -> c > 0)
                .summaryStatistics();

        int minTracks = trackStats.getCount() > 0 ? trackStats.getMin() : 0;
        int maxTracks = trackStats.getCount() > 0 ? trackStats.getMax() : 0;

        List<String> years = groupReleases.stream()
                .map(r -> extractYear(r.date()))
                .filter(y -> !y.equals("N/A"))
                .distinct()
                .sorted()
                .toList();

        List<String> types = groupReleases.stream()
                .map(r -> r.releaseGroup().primaryType())
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        var representative = groupReleases.stream().min(Comparator.comparingInt(MusicBrainzSearchResponse.Release::score).reversed()
                        .thenComparing(r -> "Official".equals(r.status()) ? 0 : 1)
                        .thenComparing(r -> r.date() != null ? r.date() : "9999")
                        .thenComparingInt(r -> r.title().length()))
                .orElse(groupReleases.getFirst());

        return new ReleaseMetadata(
                representative.id(),
                representative.releaseGroup().id(),
                getArtistName(representative),
                representative.title(),
                representative.score(),
                years,
                types,
                minTracks,
                maxTracks,
                groupReleases.size(),
                List.of()
        );
    }

    private int getTrackCount(MusicBrainzSearchResponse.Release r) {
        if (r.trackCount() != null && r.trackCount() > 0) return r.trackCount();
        if (r.media() != null) {
            return r.media().stream()
                    .mapToInt(m -> m.trackCount() != null ? m.trackCount() : 0)
                    .sum();
        }
        return 0;
    }

    private String getArtistName(MusicBrainzSearchResponse.Release release) {
        if (release.artistCredit() != null && !release.artistCredit().isEmpty()) {
            return release.artistCredit().getFirst().name();
        }
        return "Unknown Artist";
    }

    private String extractYear(String date) {
        if (date != null && date.length() >= 4) {
            return date.substring(0, 4);
        }
        return "N/A";
    }

    @Override
    public List<String> getTracks(String releaseId) {
        log.info("Fetching tracklist for release ID: {}", releaseId);

        try {
            var release = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/release/{id}")
                            .queryParam("inc", "recordings")
                            .queryParam("fmt", "json")
                            .build(releaseId))
                    .retrieve()
                    .body(MusicBrainzSearchResponse.Release.class);

            if (release == null || release.media() == null) {
                log.warn("Release or media not found: {}", releaseId);
                return List.of();
            }

            return release.media().stream()
                    .flatMap(media -> media.tracks() != null ? media.tracks().stream() : java.util.stream.Stream.empty())
                    .map(track -> track.recording() != null ? track.recording().title() : track.title())
                    .toList();

        } catch (Exception ex) {
            log.error("Error fetching tracklist: {}", ex.getMessage());
            return List.of();
        }
    }
}