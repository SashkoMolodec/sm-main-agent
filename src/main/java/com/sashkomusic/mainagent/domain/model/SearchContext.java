package com.sashkomusic.mainagent.domain.model;

import java.util.List;

public record SearchContext(
        SearchEngine source,
        MetadataSearchRequest request,
        String rawInput,
        List<String> releaseIds
) {
}