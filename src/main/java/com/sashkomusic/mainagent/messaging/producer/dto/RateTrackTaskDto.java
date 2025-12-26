package com.sashkomusic.mainagent.messaging.producer.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("rate_track_task")
public record RateTrackTaskDto(
        Long trackId,
        int rating,
        long chatId
) {
}
