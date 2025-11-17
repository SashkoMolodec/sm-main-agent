package com.sashkomusic.mainagent.messaging.dto;

public record SearchFilesTaskDto(
        long chatId,
        String artist,
        String title) {

    public static SearchFilesTaskDto of(long chatId, String artist, String title) {
        return new SearchFilesTaskDto(chatId, artist, title);
    }
}
