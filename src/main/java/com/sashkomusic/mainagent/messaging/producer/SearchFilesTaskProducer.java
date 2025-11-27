package com.sashkomusic.mainagent.messaging.producer;

import com.sashkomusic.mainagent.messaging.producer.dto.SearchFilesTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SearchFilesTaskProducer {
    public static final String TASKS_TOPIC = "files-search-tasks";

    private final KafkaTemplate<String, SearchFilesTaskDto> kafkaTemplate;

    public void send(SearchFilesTaskDto task) {
        log.info("Sending task to search release files: {}", task);
        kafkaTemplate.send(TASKS_TOPIC, task);
    }
}
