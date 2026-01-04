package com.sashkomusic.mainagent.messaging.producer;

import com.sashkomusic.mainagent.messaging.producer.dto.DownloadCancelTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class DownloadCancelTaskProducer {

    public static final String TOPIC = "download-cancel-tasks";

    private final KafkaTemplate<String, DownloadCancelTaskDto> kafkaTemplate;

    public void send(DownloadCancelTaskDto task) {
        log.info("Sending cancel download task: chatId={}, releaseId={}",
                task.chatId(), task.releaseId());
        kafkaTemplate.send(TOPIC, task);
    }
}
