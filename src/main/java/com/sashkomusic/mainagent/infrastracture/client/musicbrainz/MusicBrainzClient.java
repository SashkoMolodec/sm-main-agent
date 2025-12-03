package com.sashkomusic.mainagent.infrastracture.client.musicbrainz;

import com.sashkomusic.mainagent.domain.model.MetadataSearchRequest;
import com.sashkomusic.mainagent.domain.model.ReleaseMetadata;
import com.sashkomusic.mainagent.domain.model.Source;
import com.sashkomusic.mainagent.domain.service.search.SearchEngineService;
import com.sashkomusic.mainagent.infrastracture.client.musicbrainz.exception.SearchNotCompleteException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class MusicBrainzClient implements SearchEngineService {

    private final RestClient client;

    public MusicBrainzClient(RestClient.Builder builder) {
        this.client = builder
                .baseUrl("https://musicbrainz.org/ws/2")
                .defaultHeader("User-Agent", "SashkoMusicBot/1.0 ( contact@example.com )")
                .build();
    }

    @Override
    @Retryable(retryFor = SearchNotCompleteException.class, maxAttempts = 3, backoff = @Backoff(delay = 5000))
    public List<ReleaseMetadata> searchReleases(MetadataSearchRequest request) {
        boolean hasRecording = !request.recording().isEmpty();
        boolean hasRelease = !request.release().isEmpty();

        if (hasRecording && !hasRelease) {
            log.info("Explicit track search detected, using recording endpoint");
            return searchByRecording(request);
        }

        var results = searchByRelease(request);
        if (results.isEmpty() && hasRecording) {
            log.info("No results from release search, trying recording endpoint as fallback");
            return searchByRecording(request);
        }

        return results;
    }

    private List<ReleaseMetadata> searchByRelease(MetadataSearchRequest request) {
        String luceneQuery = toLuceneQuery(request);
        log.info("Searching MusicBrainz release endpoint with query: {}", luceneQuery);

        try {
            var response = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/release")
                            .queryParam("query", luceneQuery)
                            .queryParam("fmt", "json")
                            .queryParam("limit", 150)
                            .queryParam("inc", "tags")
                            .build())
                    .retrieve()
                    .body(MusicBrainzSearchResponse.class);

            if (response == null || response.releases() == null || response.releases().isEmpty()) {
                return List.of();
            }

            return mapToGroupedDomain(response.releases());

        } catch (Exception ex) {
            log.error("Error searching MusicBrainz release endpoint: {}", ex.getMessage());
            throw new SearchNotCompleteException("Search failed due to API error.");
        }
    }

    private List<ReleaseMetadata> searchByRecording(MetadataSearchRequest request) {
        String luceneQuery = toLuceneQuery(request);
        log.info("Searching MusicBrainz recording endpoint with query: {}", luceneQuery);

        try {
            var response = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/recording")
                            .queryParam("query", luceneQuery)
                            .queryParam("fmt", "json")
                            .queryParam("limit", 100)
                            .build())
                    .retrieve()
                    .body(RecordingSearchResponse.class);

            if (response == null || response.recordings() == null || response.recordings().isEmpty()) {
                return List.of();
            }

            var allReleases = response.recordings().stream()
                    .flatMap(recording -> {
                        if (recording.releases() == null) return Stream.empty();
                        return recording.releases().stream()
                                .map(release -> convertToReleases(release, recording.artistCredit()));
                    })
                    .toList();

            if (allReleases.isEmpty()) {
                return List.of();
            }

            return mapToGroupedDomain(allReleases);

        } catch (Exception ex) {
            log.error("Error searching MusicBrainz recording endpoint: {}", ex.getMessage());
            throw new SearchNotCompleteException("Search failed due to API error.");
        }
    }

    private MusicBrainzSearchResponse.Release convertToReleases(
            RecordingSearchResponse.Release recordingRelease,
            List<RecordingSearchResponse.ArtistCredit> recordingArtistCredit) {

        // Use release's artist-credit if present, otherwise fallback to recording's artist-credit
        List<RecordingSearchResponse.ArtistCredit> sourceArtistCredit =
                recordingRelease.artistCredit() != null ? recordingRelease.artistCredit() : recordingArtistCredit;

        // Convert artist-credit from recording response to release response format
        List<MusicBrainzSearchResponse.ArtistCredit> artistCredit = null;
        if (sourceArtistCredit != null) {
            artistCredit = sourceArtistCredit.stream()
                    .map(ac -> new MusicBrainzSearchResponse.ArtistCredit(
                            ac.name(),
                            new MusicBrainzSearchResponse.Artist(
                                    ac.artist().id(),
                                    ac.artist().name(),
                                    ac.artist().sortName(),
                                    null // disambiguation not present in recording response
                            )
                    ))
                    .toList();
        }

        return new MusicBrainzSearchResponse.Release(
                recordingRelease.id(),
                100, // Default score for releases from recording search
                recordingRelease.statusId(),
                null, // packagingId
                null, // artistCreditId
                0, // count
                recordingRelease.title(),
                recordingRelease.status(),
                null, // disambiguation
                null, // packaging
                null, // textRepresentation
                artistCredit,
                new MusicBrainzSearchResponse.ReleaseGroup(
                        recordingRelease.releaseGroup().id(),
                        null, // typeId
                        null, // primaryTypeId
                        recordingRelease.releaseGroup().title(),
                        recordingRelease.releaseGroup().primaryType(),
                        recordingRelease.releaseGroup().secondaryTypes() != null ? recordingRelease.releaseGroup().secondaryTypes() : List.of(),
                        List.of() // secondaryTypeIds
                ),
                recordingRelease.date(),
                recordingRelease.country(),
                null, // releaseEvents
                null, // barcode
                null, // asin
                null, // labelInfo
                recordingRelease.trackCount() != null ? recordingRelease.trackCount() : 0,
                null, // media
                null // tags
        );
    }

    private String toLuceneQuery(MetadataSearchRequest request) {
        List<String> conditions = new ArrayList<>();

        if (!request.artist().isEmpty()) {
            conditions.add("artist:\"" + escapeLucene(request.artist()) + "\"");
        }

        // Handle release and recording with OR logic when both are present
        boolean hasRelease = !request.release().isEmpty();
        boolean hasRecording = !request.recording().isEmpty();

        if (hasRelease && hasRecording) {
            String orCondition = String.format(
                "(release:\"%s\" OR recording:\"%s\")",
                escapeLucene(request.release()),
                escapeLucene(request.recording())
            );
            conditions.add(orCondition);
        } else if (hasRelease) {
            conditions.add("release:\"" + escapeLucene(request.release()) + "\"");
        } else if (hasRecording) {
            conditions.add("recording:\"" + escapeLucene(request.recording()) + "\"");
        }

        if (request.dateRange() != null && !request.dateRange().isEmpty()) {
            conditions.add(request.dateRange().toMusicBrainzQuery());
        }

        if (!request.format().isEmpty()) {
            conditions.add("format:\"" + escapeLucene(request.format()) + "\"");
        }

        if (!request.country().isEmpty()) {
            conditions.add("country:" + request.country());
        }

        if (!request.status().isEmpty()) {
            conditions.add("status:" + request.status());
        }

        if (!request.style().isEmpty()) {
            conditions.add("tag:\"" + escapeLucene(request.style()) + "\"");
        }

        if (!request.label().isEmpty()) {
            conditions.add("label:\"" + escapeLucene(request.label()) + "\"");
        }

        if (!request.catno().isEmpty()) {
            conditions.add("catno:\"" + escapeLucene(request.catno()) + "\"");
        }

        return conditions.isEmpty() ? "*" : String.join(" AND ", conditions);
    }

    private String escapeLucene(String value) {
        return value.replace("\"", "\\\"");
    }

    private List<ReleaseMetadata> mapToGroupedDomain(List<MusicBrainzSearchResponse.Release> releases) {
        Map<String, List<MusicBrainzSearchResponse.Release>> grouped = releases.stream()
                .filter(r -> r.releaseGroup() != null)
                .collect(Collectors.groupingBy(r -> r.releaseGroup().id()));

        return grouped.values().stream()
                .map(this::aggregateGroup)
                .sorted(
                        Comparator.comparing((ReleaseMetadata m) -> m.years().isEmpty() ? "0000" : m.years().getFirst()).reversed()
                                .thenComparing(Comparator.comparingInt(ReleaseMetadata::score).reversed())
                                .thenComparingInt(r -> r.title().length())
                )
                .toList();
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

        List<String> tags = groupReleases.stream()
                .flatMap(r -> r.tags() != null ? r.tags().stream() : Stream.empty())
                .sorted(Comparator.comparingInt(MusicBrainzSearchResponse.Tag::count).reversed())
                .map(t -> t.name().toLowerCase())
                .distinct()
                .toList(); // Keep all tags, display will be limited

        var representative = groupReleases.stream().min(Comparator.comparingInt(MusicBrainzSearchResponse.Release::score).reversed()
                        .thenComparing(r -> "Official".equals(r.status()) ? 0 : 1)
                        .thenComparing(r -> r.date() != null ? r.date() : "9999")
                        .thenComparingInt(r -> r.title().length()))
                .orElse(groupReleases.getFirst());

        String coverUrl = getCoverUrl(representative);

        return new ReleaseMetadata(
                representative.id(),
                representative.releaseGroup().id(),
                Source.MUSICBRAINZ,
                getArtistName(representative),
                representative.title(),
                representative.score(),
                years,
                types,
                minTracks,
                maxTracks,
                groupReleases.size(),
                List.of(),
                coverUrl,
                tags
        );
    }

    @Nullable
    private static String getCoverUrl(MusicBrainzSearchResponse.Release representative) {
        return representative.releaseGroup() != null && representative.releaseGroup().id() != null
                ? "https://coverartarchive.org/release-group/" + representative.releaseGroup().id() + "/front-500"
                : null;
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

    @Override
    public String getName() {
        return "musicbrainz";
    }
}