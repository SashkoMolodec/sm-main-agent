package com.sashkomusic.mainagent.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record ReleaseMetadataFile(
        @JsonProperty("metadata_version") int metadataVersion,
        @JsonProperty("source_id") String sourceId,
        @JsonProperty("master_id") String masterId,
        @JsonProperty("source") SearchEngine source,
        @JsonProperty("artist") String artist,
        @JsonProperty("title") String title,
        @JsonProperty("year") Integer year,
        @JsonProperty("processed_at") LocalDateTime processedAt,
        @JsonProperty("track_count") int trackCount,
        @JsonProperty("label") String label,
        @JsonProperty("tags") java.util.List<String> tags,
        @JsonProperty("types") java.util.List<String> types
) {
}
