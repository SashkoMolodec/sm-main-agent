package com.sashkomusic.mainagent.messaging.consumer.dto;

import com.sashkomusic.mainagent.domain.model.DownloadEngine;
import com.sashkomusic.mainagent.domain.model.DownloadOption;

import java.util.List;

public record SearchFilesResultDto(
        long chatId,
        String releaseId,
        DownloadEngine source,
        List<DownloadOption> results,
        boolean autoDownload) {
}