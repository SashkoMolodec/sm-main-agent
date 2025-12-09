package com.sashkomusic.mainagent.domain.model;

import lombok.Getter;

@Getter
public enum SearchEngine {
    MUSICBRAINZ("musicbrainz"),
    DISCOGS("discogs"),
    BANDCAMP("bandcamp");

    public final String name;

    SearchEngine(String name) {
        this.name = name;
    }
}
