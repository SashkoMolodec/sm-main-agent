package com.sashkomusic.mainagent.messaging.consumer.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("download_complete")
public record DownloadCompleteDto(
        long chatId,
        String filename,
        long sizeMB
) {
}