package com.sashkomusic.mainagent.domain.model;

import java.util.List;
import java.util.Map;

public record DownloadOption(
        String id,
        String sourceName,
        String distributorName,
        int totalSize,
        List<FileItem> files,
        Map<String, String> technicalMetadata
) {
    public record FileItem(
            String filename,
            int size,
            Integer bitRate,
            Integer bitDepth,
            Integer sampleRate,
            int lengthSeconds
    ) {
    }
}