package com.sashkomusic.mainagent.messaging.consumer.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.List;

@JsonTypeName("library_complete")
public record LibraryProcessingCompleteDto(
        long chatId,
        String masterId,
        String directoryPath,
        List<ProcessedFileDto> processedFiles,
        boolean success,
        String message,
        List<String> errors
) {
    public record ProcessedFileDto(
            String originalPath,
            String newPath,
            String trackTitle,
            int trackNumber
    ) {}
}
