package com.sashkomusic.mainagent.domain.model;

import java.util.List;

public record ReleaseMetadata(
        String id,
        String masterId,
        Source source,
        String artist,
        String title,
        int score,
        List<String> years,
        List<String> types,
        int minTracks,
        int maxTracks,
        int totalReleasesFound,
        List<String> trackTitles,
        String coverUrl,
        List<String> tags,
        String label
) {

    public String getCoverArtUrl() {
        return coverUrl;
    }

    public String getTrackCountDisplay() {
        if (minTracks == 0 && maxTracks == 0) {
            return "";
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

    public String getTagsDisplay() {
        if (tags == null || tags.isEmpty()) return "";
        return tags.stream()
                .limit(7)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    public String getLabelDisplay() {
        if (label == null || label.isBlank()) return "";
        return label;
    }

    public ReleaseMetadata withTracks(List<String> tracks) {
        return new ReleaseMetadata(
                this.id,
                this.masterId,
                this.source,
                this.artist,
                this.title,
                this.score,
                this.years,
                this.types,
                this.minTracks,
                this.maxTracks,
                this.totalReleasesFound,
                tracks,
                this.coverUrl,
                this.tags,
                this.label
        );
    }
}