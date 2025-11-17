package com.sashkomusic.mainagent.infrastracture.client.musicbrainz.exception.exception;

public class SearchNotFoundException extends RuntimeException {
    public SearchNotFoundException(String message) {
        super(message);
    }
}
