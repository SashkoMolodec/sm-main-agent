package com.sashkomusic.mainagent.messaging.consumer;

import com.sashkomusic.mainagent.domain.model.DownloadOption;

import java.util.List;

public record SearchFilesResultDto(
        long chatId,
        String releaseId,
        List<DownloadOption> results) {
}