package com.sashkomusic.mainagent.domain.service.search;

import com.sashkomusic.mainagent.domain.model.MetadataSearchRequest;
import com.sashkomusic.mainagent.domain.model.ReleaseMetadata;
import com.sashkomusic.mainagent.domain.model.ReleaseMetadataFile;
import com.sashkomusic.mainagent.domain.model.SearchEngine;
import com.sashkomusic.mainagent.domain.model.TrackMetadata;

import java.util.List;

public interface SearchEngineService {

    List<ReleaseMetadata> searchReleases(MetadataSearchRequest request);

    List<TrackMetadata> getTracks(String releaseId);

    String getName();

    SearchEngine getSource();

    String buildReleaseUrl(ReleaseMetadata release);

    ReleaseMetadata getReleaseMetadata(ReleaseMetadataFile metadataFile);
}
