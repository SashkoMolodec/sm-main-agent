package com.sashkomusic.mainagent.messaging.producer;

import com.sashkomusic.mainagent.messaging.producer.dto.SetFunctionTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SetFunctionTaskProducer {

    public static final String TOPIC = "set-function-tasks";

    private final KafkaTemplate<String, SetFunctionTaskDto> kafkaTemplate;

    public void send(SetFunctionTaskDto task) {
        log.info("Sending set function task: trackId={}, function={}, chatId={}",
                task.trackId(), task.function(), task.chatId());
        kafkaTemplate.send(TOPIC, task);
    }
}
