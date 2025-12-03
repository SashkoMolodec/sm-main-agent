package com.sashkomusic.mainagent.domain.service.search;

import com.sashkomusic.mainagent.domain.exception.SearchSessionExpiredException;
import com.sashkomusic.mainagent.domain.model.MetadataSearchRequest;
import com.sashkomusic.mainagent.domain.model.SearchEngine;
import com.sashkomusic.mainagent.domain.model.ReleaseMetadata;
import com.sashkomusic.mainagent.domain.model.SearchContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SearchContextService {
    private final Map<String, ReleaseMetadata> releaseMetadata = new ConcurrentHashMap<>();
    private final Map<Long, SearchContext> userSearches = new ConcurrentHashMap<>();
    private final Map<SearchEngine, SearchEngineService> searchEngines;

    public SearchContextService(Map<SearchEngine, SearchEngineService> searchEngines) {
        this.searchEngines = searchEngines;
    }

    public ReleaseMetadata getReleaseMetadata(String releaseId) {
        return releaseMetadata.get(releaseId);
    }

    public void saveReleaseMetadata(ReleaseMetadata metadata) {
        releaseMetadata.put(metadata.id(), metadata);
    }

    public void saveSearchContext(long chatId, SearchEngine source, String rawInput, MetadataSearchRequest request, List<ReleaseMetadata> results) {
        results.forEach(r -> releaseMetadata.put(r.id(), r));

        List<String> releaseIds = results.stream()
                .map(ReleaseMetadata::id)
                .toList();

        userSearches.put(chatId, new SearchContext(source, request, rawInput, releaseIds));
    }

    public void validateSession(long chatId) {
        SearchContext context = userSearches.get(chatId);
        if (context == null) {
            throw new SearchSessionExpiredException("Search session not found for chatId: " + chatId);
        }
    }

    public List<ReleaseMetadata> getSearchResults(long chatId) {
        validateSession(chatId);
        SearchContext context = userSearches.get(chatId);
        return context.releaseIds().stream()
                .map(releaseMetadata::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public MetadataSearchRequest getSearchRequest(long chatId) {
        validateSession(chatId);
        return userSearches.get(chatId).request();
    }

    public SearchEngine getSearchEngine(long chatId) {
        validateSession(chatId);
        return userSearches.get(chatId).searchEngine();
    }

    public String getRawInput(long chatId) {
        validateSession(chatId);
        return userSearches.get(chatId).rawInput();
    }

    public ReleaseMetadata getMetadataWithTracks(String releaseId, long chatId) {
        ReleaseMetadata metadata = getReleaseMetadata(releaseId);
        if (metadata == null) {
            log.warn("No metadata found for releaseId={}", releaseId);
            return null;
        }

        if (metadata.trackTitles() != null && !metadata.trackTitles().isEmpty()) {
            log.debug("Tracks already loaded for releaseId={}", releaseId);
            return metadata;
        }

        log.info("Fetching tracks for releaseId={}, source={}", releaseId, metadata.source());

        try {
            SearchEngine searchEngine = getSearchEngine(chatId);
            SearchEngineService engine = searchEngines.get(searchEngine);

            List<String> tracks = engine.getTracks(releaseId);

            if (tracks != null && !tracks.isEmpty()) {
                ReleaseMetadata enriched = metadata.withTracks(tracks);
                saveReleaseMetadata(enriched);
                log.info("Successfully fetched {} tracks for releaseId={}", tracks.size(), releaseId);
                return enriched;
            } else {
                log.warn("No tracks returned from {} for releaseId={}", searchEngine, releaseId);
                return metadata;
            }
        } catch (Exception e) {
            log.error("Failed to fetch tracks for releaseId={}: {}", releaseId, e.getMessage(), e);
            return metadata;
        }
    }
}