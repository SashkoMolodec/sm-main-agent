package com.sashkomusic.mainagent.domain.model;

import java.util.List;

public record SearchContext(
        SearchRequest request,
        List<String> releaseIds
) {
}