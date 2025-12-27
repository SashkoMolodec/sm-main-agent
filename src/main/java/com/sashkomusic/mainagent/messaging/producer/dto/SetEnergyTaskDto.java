package com.sashkomusic.mainagent.messaging.producer.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("set_energy_task")
public record SetEnergyTaskDto(
        Long trackId,
        String energy,  // E1-E5
        long chatId
) {
}
