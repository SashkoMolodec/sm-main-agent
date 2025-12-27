package com.sashkomusic.mainagent.messaging.producer;

import com.sashkomusic.mainagent.messaging.producer.dto.SetEnergyTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SetEnergyTaskProducer {

    public static final String TOPIC = "set-energy-tasks";

    private final KafkaTemplate<String, SetEnergyTaskDto> kafkaTemplate;

    public void send(SetEnergyTaskDto task) {
        log.info("Sending set energy task: trackId={}, energy={}, chatId={}",
                task.trackId(), task.energy(), task.chatId());
        kafkaTemplate.send(TOPIC, task);
    }
}
