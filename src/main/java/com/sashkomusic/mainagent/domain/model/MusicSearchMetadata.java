package com.sashkomusic.mainagent.domain.model;

public record MusicSearchMetadata(
        String title,
        String artist,
        int releasesFound,
        int minTrackCount
) {

}
