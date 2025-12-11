package com.sashkomusic.mainagent.messaging.producer;

import com.sashkomusic.mainagent.messaging.producer.dto.ReprocessReleaseTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReprocessReleaseTaskProducer {
    public static final String TASKS_TOPIC = "reprocess-release-tasks";

    private final KafkaTemplate<String, ReprocessReleaseTaskDto> kafkaTemplate;

    public void send(ReprocessReleaseTaskDto dto) {
        log.info("Sending reprocess task: directory={}, source={}",
                dto.directoryPath(), dto.metadata().source());
        kafkaTemplate.send(TASKS_TOPIC, dto);
    }
}
