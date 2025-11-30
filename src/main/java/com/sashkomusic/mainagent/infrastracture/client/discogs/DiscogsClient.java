package com.sashkomusic.mainagent.infrastracture.client.discogs;

import com.sashkomusic.mainagent.domain.model.MetadataSearchRequest;
import com.sashkomusic.mainagent.domain.model.ReleaseMetadata;
import com.sashkomusic.mainagent.domain.service.SearchEngineService;
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

        int filledFieldCount = 0;
        String singleValue = null;

        if (!request.artist().isEmpty()) {
            filledFieldCount++;
            singleValue = request.artist();
        }

        boolean hasRelease = !request.release().isEmpty();
        boolean hasRecording = !request.recording().isEmpty();
        if (hasRelease || hasRecording) {
            filledFieldCount++;
            singleValue = hasRelease ? request.release() : request.recording();
        }

        if (request.dateRange() != null && !request.dateRange().isEmpty()) {
            filledFieldCount++;
            singleValue = request.dateRange().toDiscogsParam();
        }
        if (!request.format().isEmpty()) {
            filledFieldCount++;
            singleValue = request.format();
        }
        if (!request.catno().isEmpty()) {
            filledFieldCount++;
            singleValue = request.catno();
        }
        if (!request.label().isEmpty()) {
            filledFieldCount++;
            singleValue = request.label();
        }
        if (!request.style().isEmpty()) {
            filledFieldCount++;
            singleValue = request.style();
        }
        if (!request.country().isEmpty()) {
            filledFieldCount++;
            singleValue = request.country();
        }

        if (filledFieldCount == 1) {
            log.info("Using general 'q' search with value: {}", singleValue);
            builder.queryParam("q", singleValue);
        } else {
            log.info("Using specific field search with {} parameters", filledFieldCount);
            if (!request.artist().isEmpty()) {
                builder.queryParam("artist", request.artist());
            }

            if (hasRelease) {
                builder.queryParam("release_title", request.release());
            } else if (hasRecording) {
                builder.queryParam("track", request.recording());
            }

            if (request.dateRange() != null && !request.dateRange().isEmpty()) {
                builder.queryParam("year", request.dateRange().toDiscogsParam());
            }
            if (!request.format().isEmpty()) {
                builder.queryParam("format", request.format());
            }
            if (!request.catno().isEmpty()) {
                builder.queryParam("catno", request.catno());
            }
            if (!request.label().isEmpty()) {
                builder.queryParam("label", request.label());
            }
            if (!request.style().isEmpty()) {
                builder.queryParam("style", request.style());
            }
            if (!request.country().isEmpty()) {
                builder.queryParam("country", request.country());
            }
        }

        if (!apiToken.isEmpty()) {
            builder.queryParam("token", apiToken);
        }
    }

    private List<ReleaseMetadata> mapToDomain(List<DiscogsSearchResponse.Result> results) {
        List<DiscogsSearchResponse.Result> releases = results.stream()
                .filter(r -> "master".equals(r.type()) || "release".equals(r.type()))
                .toList();

        Map<String, List<DiscogsSearchResponse.Result>> grouped = releases.stream()
                .collect(Collectors.groupingBy(r -> extractTitle(r.title()).toLowerCase().trim()));

        return grouped.values().stream()
                .map(this::aggregateGroup)
                .sorted(Comparator.comparing((ReleaseMetadata m) -> m.years().isEmpty() ? "0000" : m.years().getLast()).reversed()
                        .thenComparingInt(ReleaseMetadata::score).reversed())
                .toList();
    }

    private ReleaseMetadata aggregateGroup(List<DiscogsSearchResponse.Result> groupResults) {
        var representative = groupResults.stream()
                .filter(r -> "master".equals(r.type()))
                .findFirst()
                .orElse(groupResults.getFirst());

        String artist = extractArtist(representative.title());
        String title = extractTitle(representative.title());

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

        // Construct releaseId for unique identification
        String releaseId = "discogs:" + (representative.masterId() != null && representative.masterId() != 0 ?
                "master:" + representative.masterId() :
                "release:" + representative.id());

        // masterId for grouping - only set if release has a master
        String masterId = (representative.masterId() != null && representative.masterId() != 0) ?
                String.valueOf(representative.masterId()) :
                null;

        return new ReleaseMetadata(
                releaseId,
                masterId,
                artist,
                title,
                100, // Default score for Discogs results
                years,
                types,
                0, // Track count not available in search response
                0,
                groupResults.size(),
                List.of(),
                representative.coverImage() // Use Discogs cover image URL
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

    @Override
    public List<String> getTracks(String releaseId) {
        log.info("Fetching tracklist from Discogs for release ID: {}", releaseId);
        return List.of();
    }

    @Override
    public String getName() {
        return "discogs";
    }
}