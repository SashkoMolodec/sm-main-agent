package com.sashkomusic.mainagent.messaging.producer;

import com.sashkomusic.mainagent.messaging.producer.dto.DownloadFilesTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class DownloadTaskProducer {

    public static final String TASKS_TOPIC = "files-download-tasks";

    private final KafkaTemplate<String, DownloadFilesTaskDto> kafkaTemplate;

    public void send(DownloadFilesTaskDto task) {
        log.info("Sending task to download release files: {}", task);
        kafkaTemplate.send(TASKS_TOPIC, task);
    }
}
