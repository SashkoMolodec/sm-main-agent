package com.sashkomusic.mainagent.domain.model;

import com.sashkomusic.mainagent.domain.util.SearchUrlUtils;

import java.util.UUID;

public record MetadataSearchRequest(
        String id,
        String artist,
        String release,
        String recording,
        DateRange dateRange,
        String format,
        String type,
        String country,
        String status,
        String style,
        String label,
        String catno
) {

    public static MetadataSearchRequest create(
            String artist,
            String release,
            String recording,
            DateRange dateRange,
            String format,
            String type,
            String country,
            String status,
            String style,
            String label,
            String catno,
            Language language
    ) {
        return new MetadataSearchRequest(
                UUID.randomUUID().toString(),
                artist != null ? artist : "",
                release != null ? release : "",
                recording != null ? recording : "",
                dateRange != null ? dateRange : DateRange.empty(),
                format != null ? format : "",
                type != null ? type : "",
                country != null ? country : "",
                status != null ? status : "",
                style != null ? style : "",
                label != null ? label : "",
                catno != null ? catno : ""
        );
    }

    public String getYoutubeUrl() {
        Language language = SearchUrlUtils.detectLanguage(artist, getTitle());
        String albumWord = SearchUrlUtils.buildYoutubeAlbumWord(language);
        String query = artist + " " + getTitle() + " " + albumWord;
        return "https://www.youtube.com/results?search_query=" + SearchUrlUtils.encode(query);
    }

    public String getDiscogsUrl() {
        String query = artist + " " + getTitle();
        return "https://www.discogs.com/search/?q=" + SearchUrlUtils.encode(query);
    }

    public String getBandcampUrl() {
        String query = artist + " " + getTitle();
        return "https://bandcamp.com/search?q=" + SearchUrlUtils.encode(query);
    }

    public String getTitle() {
        if (!release.isEmpty()) {
            return release;
        }
        if (!recording.isEmpty()) {
            return recording;
        }
        return "";
    }
}
