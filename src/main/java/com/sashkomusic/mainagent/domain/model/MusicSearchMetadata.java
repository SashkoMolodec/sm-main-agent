package com.sashkomusic.mainagent.domain.model;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public record MusicSearchMetadata(
        String id,
        String releaseGroupId,
        String artist,
        String title,
        int score,
        List<String> years,
        List<String> types,
        int minTracks,
        int maxTracks,
        int totalReleasesFound,
        List<String> trackTitles
) {

    public String getCoverArtUrl() {
        if (releaseGroupId != null && !releaseGroupId.isBlank()) {
            return "https://coverartarchive.org/release-group/" + releaseGroupId + "/front-500";
        }
        return null;
    }

    public String getTrackCountDisplay() {
        if (minTracks == 0 && maxTracks == 0) {
            return "?";
        }
        if (minTracks == maxTracks) {
            return String.valueOf(minTracks);
        }
        return minTracks + "-" + maxTracks;
    }

    public String getYearsDisplay() {
        if (years == null || years.isEmpty()) return "N/A";
        if (years.size() == 1) {
            return years.getFirst();
        }
        return String.join(", ", years);
    }

    public String getTypesDisplay() {
        if (types == null || types.isEmpty()) return "N/A";
        if (types.size() == 1) {
            return types.getFirst();
        }
        return String.join(", ", types);
    }

    public MusicSearchMetadata withTracks(List<String> tracks) {
        return new MusicSearchMetadata(
                this.id,
                this.releaseGroupId,
                this.artist,
                this.title,
                this.score,
                this.years,
                this.types,
                this.minTracks,
                this.maxTracks,
                this.totalReleasesFound,
                tracks
        );
    }
}