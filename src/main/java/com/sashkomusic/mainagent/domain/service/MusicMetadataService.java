package com.sashkomusic.mainagent.domain.service;

import com.sashkomusic.mainagent.domain.model.ReleaseMetadata;

import java.util.List;

public interface MusicMetadataService {

    List<ReleaseMetadata> searchReleases(String query);

    List<String> getTracks(String releaseId);
}
