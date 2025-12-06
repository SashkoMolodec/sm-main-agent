package com.sashkomusic.mainagent.domain.service.search;

import com.sashkomusic.mainagent.domain.model.MetadataSearchRequest;
import com.sashkomusic.mainagent.domain.model.ReleaseMetadata;

import java.util.List;

public interface SearchEngineService {

    List<ReleaseMetadata> searchReleases(MetadataSearchRequest request);

    List<String> getTracks(String releaseId);

    String getName();

    /**
     * Build a URL to view the release on this search engine's website
     * @param release the release metadata
     * @return URL string, or null if not applicable
     */
    String buildReleaseUrl(ReleaseMetadata release);
}
