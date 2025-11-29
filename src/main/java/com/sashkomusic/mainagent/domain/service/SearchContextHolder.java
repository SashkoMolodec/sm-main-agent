package com.sashkomusic.mainagent.domain.service;

import com.sashkomusic.mainagent.domain.exception.SearchSessionExpiredException;
import com.sashkomusic.mainagent.domain.model.MetadataSearchRequest;
import com.sashkomusic.mainagent.domain.model.SearchEngine;
import com.sashkomusic.mainagent.domain.model.ReleaseMetadata;
import com.sashkomusic.mainagent.domain.model.SearchContext;
import com.sashkomusic.mainagent.domain.service.download.DownloadOptionsAnalyzer;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SearchContextHolder {
    private final Map<String, ReleaseMetadata> releaseMetadata = new ConcurrentHashMap<>();
    private final Map<Long, SearchContext> userSearches = new ConcurrentHashMap<>();
    @Getter
    @Setter
    private List<DownloadOptionsAnalyzer.OptionReport> downloadOptionReports = new ArrayList<>();
    @Getter
    @Setter
    private String chosenReleaseForDownload;

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
}