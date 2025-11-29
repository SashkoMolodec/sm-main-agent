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
                .queryParam("per_page", "50");

        if (!request.artist().isEmpty()) {
            builder.queryParam("artist", request.artist());
        }
        if (!request.release().isEmpty()) {
            builder.queryParam("release_title", request.release());
        }
        if (!request.recording().isEmpty()) {
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

        if (!apiToken.isEmpty()) {
            builder.queryParam("token", apiToken);
        }
    }

    private List<ReleaseMetadata> mapToDomain(List<DiscogsSearchResponse.Result> results) {
        List<DiscogsSearchResponse.Result> releases = results.stream()
                .filter(r -> "master".equals(r.type()) || "release".equals(r.type()))
                .toList();

        // Group by master_id when present (not null and not 0)
        // Otherwise, each release becomes its own group
        Map<String, List<DiscogsSearchResponse.Result>> grouped = releases.stream()
                .collect(Collectors.groupingBy(r -> {
                    if (r.masterId() != null && r.masterId() != 0) {
                        return "M" + r.masterId();
                    } else {
                        return "R" + r.id();
                    }
                }));

        return grouped.values().stream()
                .map(this::aggregateGroup)
                .sorted(Comparator.comparing((ReleaseMetadata m) -> m.years().isEmpty() ? "0000" : m.years().getFirst()).reversed()
                        .thenComparingInt(ReleaseMetadata::score).reversed())
                .toList();
    }

    private ReleaseMetadata aggregateGroup(List<DiscogsSearchResponse.Result> groupResults) {
        // Select most relevant release from group:
        // 1. Prefer "master" type entries
        // 2. Otherwise use first result
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
                .filter(f -> !f.isEmpty())
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
                List.of()
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

        // For now, return empty list as Discogs track fetching requires additional API calls
        // This can be implemented later if needed
        return List.of();
    }

    @Override
    public String getName() {
        return "discogs";
    }
}