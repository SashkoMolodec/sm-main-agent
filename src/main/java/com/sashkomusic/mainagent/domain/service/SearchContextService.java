package com.sashkomusic.mainagent.domain.service;

import com.sashkomusic.mainagent.domain.model.MusicSearchMetadata;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SearchContextService {
    private final Map<String, MusicSearchMetadata> foundReleases = new ConcurrentHashMap<>();

    private final Map<Long, List<MusicSearchMetadata>> userFoundReleases = new ConcurrentHashMap<>();

    public MusicSearchMetadata getReleaseMetadata(String releaseId) {
        return foundReleases.get(releaseId);
    }

    public void saveReleaseMetadata(MusicSearchMetadata metadata) {
        foundReleases.put(metadata.id(), metadata);
    }

    public void saveSearchResults(long chatId, List<MusicSearchMetadata> results) {
        results.forEach(r -> foundReleases.put(r.id(), r));
        userFoundReleases.put(chatId, results);
    }

    public List<MusicSearchMetadata> getSearchResults(long chatId) {
        return userFoundReleases.get(chatId);
    }
}
