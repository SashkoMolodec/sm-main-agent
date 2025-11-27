package com.sashkomusic.mainagent.messaging.consumer.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("download_error")
public record DownloadErrorDto(
        long chatId,
        String errorMessage
) {
}