package com.sashkomusic.mainagent.domain.service.search;

import com.sashkomusic.mainagent.domain.model.MetadataSearchRequest;
import com.sashkomusic.mainagent.domain.model.ReleaseMetadata;
import com.sashkomusic.mainagent.domain.model.TrackMetadata;

import java.util.List;

public interface SearchEngineService {

    List<ReleaseMetadata> searchReleases(MetadataSearchRequest request);

    List<TrackMetadata> getTracks(String releaseId);

    String getName();

    String buildReleaseUrl(ReleaseMetadata release);
}
