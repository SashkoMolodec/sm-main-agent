package com.sashkomusic.mainagent.domain.model;

import com.sashkomusic.mainagent.domain.util.SearchUrlUtils;

import java.util.UUID;

public record SearchRequest(
        String id,
        String artist,
        String album,
        String recording,
        Language language,
        String luceneQuery
) {

    public String getTitle() {
        return !album.isEmpty() ? album : recording;
    }

    public String getYoutubeUrl() {
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

    public static SearchRequest create(String artist, String album, String recording, Language language, String luceneQuery) {
        return new SearchRequest(
                UUID.randomUUID().toString(),
                artist,
                album,
                recording,
                language,
                luceneQuery
        );
    }
}
