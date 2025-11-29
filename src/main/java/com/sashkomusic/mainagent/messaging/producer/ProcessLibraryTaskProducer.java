package com.sashkomusic.mainagent.messaging.producer;

import com.sashkomusic.mainagent.messaging.producer.dto.ProcessLibraryTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ProcessLibraryTaskProducer {
    public static final String TASKS_TOPIC = "process-library-tasks";

    private final KafkaTemplate<String, ProcessLibraryTaskDto> kafkaTemplate;

    public void send(ProcessLibraryTaskDto dto) {
        log.info("Sending library processing task: masterId={}, files={}", dto.masterId(), dto.downloadedFiles().size());
        kafkaTemplate.send(TASKS_TOPIC, dto);
    }
}