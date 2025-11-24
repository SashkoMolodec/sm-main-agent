package com.sashkomusic.mainagent.infrastracture.client.musicbrainz.exception;

public class SearchNotCompleteException extends RuntimeException {
    public SearchNotCompleteException(String message) {
        super(message);
    }
}
