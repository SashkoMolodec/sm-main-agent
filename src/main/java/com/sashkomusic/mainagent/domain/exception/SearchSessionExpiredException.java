package com.sashkomusic.mainagent.domain.exception;

public class SearchSessionExpiredException extends RuntimeException {
    public SearchSessionExpiredException(String message) {
        super(message);
    }
}
