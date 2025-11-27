package com.sashkomusic.mainagent.domain.service;

import com.sashkomusic.mainagent.domain.model.ReleaseMetadata;
import com.sashkomusic.mainagent.domain.model.SearchContext;
import com.sashkomusic.mainagent.domain.model.SearchRequest;
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

    public void saveSearchResults(long chatId, SearchRequest request, List<ReleaseMetadata> results) {
        results.forEach(r -> releaseMetadata.put(r.id(), r));

        List<String> releaseIds = results.stream()
                .map(ReleaseMetadata::id)
                .toList();

        userSearches.put(chatId, new SearchContext(request, releaseIds));
    }

    public List<ReleaseMetadata> getSearchResults(long chatId) {
        SearchContext context = userSearches.get(chatId);
        if (context == null) {
            return null;
        }

        return context.releaseIds().stream()
                .map(releaseMetadata::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public SearchRequest getSearchRequest(long chatId) {
        SearchContext context = userSearches.get(chatId);
        return context != null ? context.request() : null;
    }
}