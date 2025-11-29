package com.sashkomusic.mainagent.domain.model;

import java.util.List;

public record SearchContext(
        SearchEngine searchEngine,
        MetadataSearchRequest request,
        String rawInput,
        List<String> releaseIds
) {
}