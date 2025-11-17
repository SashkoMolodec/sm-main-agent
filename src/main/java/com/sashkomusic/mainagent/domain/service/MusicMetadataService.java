package com.sashkomusic.mainagent.domain.service;

import com.sashkomusic.mainagent.domain.model.MusicSearchMetadata;

public interface MusicMetadataService {

    MusicSearchMetadata search(String artist, String title);
}
