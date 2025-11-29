package com.sashkomusic.mainagent.messaging.producer.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.List;

@JsonTypeName("process_library")
public record ProcessLibraryTaskDto(
        long chatId,
        String masterId,
        String directoryPath,
        List<String> downloadedFiles
) {
    public static ProcessLibraryTaskDto of(long chatId, String masterId, String directoryPath, List<String> files) {
        return new ProcessLibraryTaskDto(chatId, masterId, directoryPath, files);
    }
}