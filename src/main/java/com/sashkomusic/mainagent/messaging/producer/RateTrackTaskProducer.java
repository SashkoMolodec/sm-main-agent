package com.sashkomusic.mainagent.messaging.producer;

import com.sashkomusic.mainagent.messaging.producer.dto.RateTrackTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class RateTrackTaskProducer {

    public static final String TOPIC = "rate-track-tasks";

    private final KafkaTemplate<String, RateTrackTaskDto> kafkaTemplate;

    public void send(RateTrackTaskDto task) {
        log.info("Sending rate track task: trackId={}, rating={}, chatId={}",
                task.trackId(), task.rating(), task.chatId());
        kafkaTemplate.send(TOPIC, task);
    }
}
