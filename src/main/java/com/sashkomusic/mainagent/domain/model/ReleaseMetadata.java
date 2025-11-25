package com.sashkomusic.mainagent.domain.model;

import com.sashkomusic.mainagent.domain.util.SearchUrlUtils;

import java.util.List;

public record ReleaseMetadata(
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

    public String getYoutubeUrl() {
        Language language = SearchUrlUtils.detectLanguage(artist, title);
        String albumWord = SearchUrlUtils.buildYoutubeAlbumWord(language);
        String query = artist + " " + title + " " + albumWord;
        return "https://www.youtube.com/results?search_query=" + SearchUrlUtils.encode(query);
    }

    public String getDiscogsUrl() {
        String query = artist + " " + title;
        return "https://www.discogs.com/search/?q=" + SearchUrlUtils.encode(query);
    }

    public String getBandcampUrl() {
        String query = artist + " " + title;
        return "https://bandcamp.com/search?q=" + SearchUrlUtils.encode(query);
    }

    public ReleaseMetadata withTracks(List<String> tracks) {
        return new ReleaseMetadata(
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