package com.sashkomusic.mainagent.messaging.consumer.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.List;

@JsonTypeName("download_batch_complete")
public record DownloadBatchCompleteDto(
        long chatId,
        String releaseId,
        String directoryPath,
        List<String> allFiles,
        int totalFiles
) {
}