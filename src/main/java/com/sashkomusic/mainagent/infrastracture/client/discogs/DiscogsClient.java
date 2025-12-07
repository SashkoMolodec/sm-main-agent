package com.sashkomusic.mainagent.infrastracture.client.discogs;

import com.sashkomusic.mainagent.domain.model.MetadataSearchRequest;
import com.sashkomusic.mainagent.domain.model.ReleaseMetadata;
import com.sashkomusic.mainagent.domain.model.Source;
import com.sashkomusic.mainagent.domain.model.TrackMetadata;
import com.sashkomusic.mainagent.domain.service.search.SearchEngineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DiscogsClient implements SearchEngineService {

    private final RestClient client;
    private final String apiToken;

    public DiscogsClient(RestClient.Builder builder, @Value("${discogs.api.token:}") String apiToken) {
        this.apiToken = apiToken;
        this.client = builder
                .baseUrl("https://api.discogs.com")
                .defaultHeader("User-Agent", "SashkoMusicBot/1.0")
                .build();
    }

    @Override
    public List<ReleaseMetadata> searchReleases(MetadataSearchRequest request) {
        log.info("Searching Discogs with parameters: artist={}, release={}, year={}, format={}",
                request.artist(), request.release(),
                request.dateRange() != null ? request.dateRange().toDiscogsParam() : "",
                request.format());

        try {
            var response = client.get()
                    .uri(uriBuilder -> {
                        addDiscogsParameters(uriBuilder, request);
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(DiscogsSearchResponse.class);

            if (response == null || response.results() == null || response.results().isEmpty()) {
                return List.of();
            }

            return mapToDomain(response.results());

        } catch (Exception ex) {
            log.error("Error searching Discogs: {}", ex.getMessage());
            return List.of();
        }
    }

    private void addDiscogsParameters(UriBuilder builder, MetadataSearchRequest request) {
        builder.path("/database/search")
                .queryParam("type", "master,release")
                .queryParam("per_page", "200");

        // Build general query from all available fields
        List<String> queryParts = new ArrayList<>();

        if (!request.artist().isEmpty()) {
            queryParts.add(request.artist());
        }

        boolean hasRelease = !request.release().isEmpty();
        boolean hasRecording = !request.recording().isEmpty();
        if (hasRelease) {
            queryParts.add(request.release());
        } else if (hasRecording) {
            queryParts.add(request.recording());
        }

        if (request.dateRange() != null && !request.dateRange().isEmpty()) {
            queryParts.add(request.dateRange().toDiscogsParam());
        }
        if (!request.format().isEmpty()) {
            queryParts.add(request.format());
        }
        if (!request.catno().isEmpty()) {
            queryParts.add(request.catno());
        }
        if (!request.label().isEmpty()) {
            queryParts.add(request.label());
        }
        if (!request.style().isEmpty()) {
            queryParts.add(request.style());
        }
        if (!request.country().isEmpty()) {
            queryParts.add(request.country());
        }

        // Combine all parts into single query
        if (!queryParts.isEmpty()) {
            String generalQuery = String.join(" ", queryParts);
            log.info("Using general 'q' search with value: {}", generalQuery);
            builder.queryParam("q", generalQuery);
        }

        if (!apiToken.isEmpty()) {
            builder.queryParam("token", apiToken);
        }
    }

    private List<ReleaseMetadata> mapToDomain(List<DiscogsSearchResponse.Result> results) {
        log.debug("Mapping {} Discogs results to domain", results.size());

        List<DiscogsSearchResponse.Result> releases = results.stream()
                .filter(r -> "master".equals(r.type()) || "release".equals(r.type()))
                .toList();

        log.debug("After filtering by type, {} releases remain", releases.size());

        Map<String, List<DiscogsSearchResponse.Result>> grouped = releases.stream()
                .collect(Collectors.groupingBy(r -> {
                    String title = extractTitle(r.title()).toLowerCase().trim();
                    return title.replaceAll("[\\p{C}\\p{Z}&&[^ ]]", "");
                }));

        log.debug("Grouped into {} unique titles", grouped.size());

        return grouped.values().stream()
                .map(this::aggregateGroup)
                .sorted(Comparator.comparing((ReleaseMetadata m) -> m.years().stream()
                        .max(String::compareTo)
                        .orElse("0000")).thenComparingInt(ReleaseMetadata::score).reversed())
                .toList();
    }

    private ReleaseMetadata aggregateGroup(List<DiscogsSearchResponse.Result> groupResults) {
        var representative = groupResults.stream()
                .filter(r -> "master".equals(r.type()))
                .findFirst()
                .orElse(groupResults.getFirst());

        String artist = extractArtist(representative.title());
        String title = extractTitle(representative.title());

        artist = cleanArtistName(clean(artist));
        title = clean(title);

        List<String> years = groupResults.stream()
                .map(DiscogsSearchResponse.Result::year)
                .filter(Objects::nonNull)
                .filter(y -> !y.isEmpty())
                .distinct()
                .sorted()
                .toList();

        List<String> types = groupResults.stream()
                .map(DiscogsSearchResponse.Result::format)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .distinct()
                .toList();

        // Combine genre and style for comprehensive tags
        List<String> tags = groupResults.stream()
                .flatMap(r -> {
                    java.util.stream.Stream<String> genreStream = r.genre() != null ? r.genre().stream() : java.util.stream.Stream.empty();
                    java.util.stream.Stream<String> styleStream = r.style() != null ? r.style().stream() : java.util.stream.Stream.empty();
                    return java.util.stream.Stream.concat(genreStream, styleStream);
                })
                .collect(Collectors.groupingBy(
                        java.util.function.Function.identity(),
                        Collectors.counting()
                ))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(7)
                .map(e -> e.getKey().toLowerCase())
                .toList();

        // Construct releaseId for unique identification
        String releaseId = "discogs:" + (representative.masterId() != null && representative.masterId() != 0 ?
                "master:" + representative.masterId() :
                "release:" + representative.id());

        // masterId for grouping - only set if release has a master
        String masterId = (representative.masterId() != null && representative.masterId() != 0) ?
                String.valueOf(representative.masterId()) :
                null;

        // Extract label from Discogs label list
        String label = groupResults.stream()
                .filter(r -> r.label() != null && !r.label().isEmpty())
                .flatMap(r -> r.label().stream())
                .findFirst()
                .orElse("");

        return new ReleaseMetadata(
                releaseId,
                masterId,
                Source.DISCOGS,
                artist,
                title,
                100, // Default score for Discogs results
                years,
                types,
                0, // Track count not available in search response
                0,
                groupResults.size(),
                List.of(),
                representative.coverImage(), // Use Discogs cover image URL
                tags,
                label
        );
    }

    private String extractArtist(String fullTitle) {
        if (fullTitle == null) return "Unknown Artist";
        int dashIndex = fullTitle.indexOf(" - ");
        if (dashIndex > 0) {
            return fullTitle.substring(0, dashIndex).trim();
        }
        return "Unknown Artist";
    }

    private String extractTitle(String fullTitle) {
        if (fullTitle == null) return "Unknown";
        int dashIndex = fullTitle.indexOf(" - ");
        if (dashIndex > 0 && dashIndex + 3 < fullTitle.length()) {
            return fullTitle.substring(dashIndex + 3).trim();
        }
        return fullTitle;
    }

    private String clean(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return text.replaceAll("[*?\\[\\]{}|<>\"'`]", "").trim();
    }

    @Override
    public List<TrackMetadata> getTracks(String releaseId) {
        log.info("Fetching tracklist from Discogs for release ID: {}", releaseId);

        // Parse releaseId format: "discogs:master:123" or "discogs:release:456"
        if (!releaseId.startsWith("discogs:")) {
            log.warn("Invalid Discogs release ID format: {}", releaseId);
            return List.of();
        }

        String[] parts = releaseId.split(":");
        if (parts.length != 3) {
            log.warn("Invalid Discogs release ID format: {}", releaseId);
            return List.of();
        }

        String type = parts[1]; // "master" or "release"
        String id = parts[2];   // actual ID

        try {
            // If it's a master, we need to fetch the main release first
            if ("master".equals(type)) {
                return getTracksFromMaster(id);
            } else {
                return getTracksFromRelease(id);
            }
        } catch (Exception ex) {
            log.error("Error fetching tracklist from Discogs: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<TrackMetadata> getTracksFromMaster(String masterId) {
        log.info("Fetching master {} to get main release", masterId);

        var masterResponse = client.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/masters/" + masterId);
                    if (!apiToken.isEmpty()) {
                        uriBuilder.queryParam("token", apiToken);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .body(DiscogsReleaseResponse.class);

        if (masterResponse == null || masterResponse.mainRelease() == null) {
            log.warn("No main release found for master {}", masterId);
            return List.of();
        }
        return getTracksFromRelease(String.valueOf(masterResponse.mainRelease()));
    }

    private List<TrackMetadata> getTracksFromRelease(String releaseId) {
        log.info("Fetching tracklist for release {}", releaseId);

        var response = client.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/releases/" + releaseId);
                    if (!apiToken.isEmpty()) {
                        uriBuilder.queryParam("token", apiToken);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .body(DiscogsReleaseResponse.class);

        if (response == null || response.tracklist() == null) {
            log.warn("No tracklist found for release {}", releaseId);
            return List.of();
        }

        // Get album artist from response
        String albumArtist = extractArtistName(response);

        // Build TrackMetadata with position numbers
        return response.tracklist().stream()
                .map(track -> {
                    int trackNumber = parseTrackPosition(track.position());
                    String trackTitle = track.title();
                    String trackArtist = albumArtist; // Default to album artist

                    // Priority 1: Use track's artists field from Discogs API (most accurate)
                    if (track.artists() != null && !track.artists().isEmpty()) {
                        // Combine multiple artists using their 'join' field (e.g., "HATELOVE & Wanton")
                        StringBuilder artistBuilder = new StringBuilder();
                        for (int i = 0; i < track.artists().size(); i++) {
                            var artist = track.artists().get(i);
                            artistBuilder.append(cleanArtistName(artist.name()));

                            // Add join separator if not the last artist and join is provided
                            if (i < track.artists().size() - 1 && artist.join() != null && !artist.join().isEmpty()) {
                                artistBuilder.append(" ").append(artist.join()).append(" ");
                            }
                        }
                        trackArtist = artistBuilder.toString().trim();
                        log.debug("Using per-track artist from Discogs API: '{}'", trackArtist);
                    }
                    // Priority 2: Try to parse from title if format is "Artist - Title"
                    else if (trackTitle != null && trackTitle.contains(" - ")) {
                        int dashIndex = trackTitle.indexOf(" - ");
                        String possibleArtist = trackTitle.substring(0, dashIndex).trim();
                        String possibleTitle = trackTitle.substring(dashIndex + 3).trim();

                        // Only split if the artist part looks reasonable (not empty, not too long)
                        if (!possibleArtist.isEmpty() && possibleArtist.length() < 100 && !possibleTitle.isEmpty()) {
                            trackArtist = cleanArtistName(possibleArtist);
                            trackTitle = possibleTitle;
                            log.debug("Parsed track artist from title: '{}' - '{}'", trackArtist, trackTitle);
                        }
                    }
                    // Priority 3: Fall back to album artist (already set as default)

                    return new TrackMetadata(trackNumber, trackArtist, trackTitle);
                })
                .toList();
    }

    private String extractArtistName(DiscogsReleaseResponse response) {
        if (response.artists() != null && !response.artists().isEmpty()) {
            return cleanArtistName(response.artists().getFirst().name());
        }
        return "Unknown Artist";
    }

    private int parseTrackPosition(String position) {
        if (position == null || position.isEmpty()) {
            return 0;
        }
        // Handle formats like "1", "A1", "B2", etc.
        try {
            // Try to extract number from position (e.g., "A1" -> 1, "B2" -> 2)
            return Integer.parseInt(position.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String cleanArtistName(String artistName) {
        if (artistName == null || artistName.isEmpty()) {
            return artistName;
        }
        // Remove disambiguation suffix: " (number)" at the end
        return artistName.replaceAll("\\s*\\(\\d+\\)\\s*$", "").trim();
    }

    @Override
    public String getName() {
        return "discogs";
    }

    @Override
    public String buildReleaseUrl(ReleaseMetadata release) {
        // Parse "discogs:master:123" or "discogs:release:456"
        if (release.id().startsWith("discogs:master:")) {
            String masterId = release.id().substring("discogs:master:".length());
            return "https://www.discogs.com/master/" + masterId;
        } else if (release.id().startsWith("discogs:release:")) {
            String releaseId = release.id().substring("discogs:release:".length());
            return "https://www.discogs.com/release/" + releaseId;
        }
        return null;
    }
}