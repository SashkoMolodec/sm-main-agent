package com.sashkomusic.mainagent.messaging.producer.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.sashkomusic.mainagent.domain.model.ReleaseMetadata;
import com.sashkomusic.mainagent.domain.service.process.ReprocessReleasesFlowService.ReprocessOptions;

@JsonTypeName("reprocess_release")
public record ReprocessReleaseTaskDto(
        long chatId,
        String directoryPath,
        ReleaseMetadata metadata,
        int newMetadataVersion,
        ReprocessOptions options
) {
}
