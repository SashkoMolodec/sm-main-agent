package com.sashkomusic.mainagent.messaging.consumer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public record TagChangesNotificationDto(
        @JsonProperty("tracks")
        List<TrackChanges> tracks,

        @JsonProperty("total_changes")
        int totalChanges,

        @JsonProperty("timestamp")
        LocalDateTime timestamp
) {
    public record TrackChanges(
            @JsonProperty("track_id")
            Long trackId,

            @JsonProperty("track_title")
            String trackTitle,

            @JsonProperty("artist_name")
            String artistName,

            @JsonProperty("changes")
            List<TagChangeInfo> changes
    ) {}

    public record TagChangeInfo(
            @JsonProperty("tag_name")
            String tagName,

            @JsonProperty("old_value")
            String oldValue,

            @JsonProperty("new_value")
            String newValue,

            @JsonProperty("is_new")
            boolean isNew
    ) {}
}
