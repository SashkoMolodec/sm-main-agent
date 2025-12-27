package com.sashkomusic.mainagent.messaging.producer.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("set_function_task")
public record SetFunctionTaskDto(
        Long trackId,
        String function,  // intro|tool|banger|closer
        long chatId
) {
}
