package com.sashkomusic.mainagent.domain.service;

import com.sashkomusic.mainagent.domain.model.MusicSearchMetadata;

import java.util.List;

public interface MusicMetadataService {

    List<MusicSearchMetadata> searchReleases(String query);

    List<String> getTracks(String releaseId);
}
