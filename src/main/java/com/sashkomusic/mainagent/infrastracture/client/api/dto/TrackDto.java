package com.sashkomusic.mainagent.infrastracture.client.api.dto;

public record TrackDto(Long id, String path, String title, String artistName, String rating) {
    public static TrackDto empty() {
        return new TrackDto(null, null, null, null, null);
    }
}
