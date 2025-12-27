package com.sashkomusic.mainagent.messaging.producer;

import com.sashkomusic.mainagent.messaging.producer.dto.AddCommentTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class AddCommentTaskProducer {

    public static final String TOPIC = "add-comment-tasks";

    private final KafkaTemplate<String, AddCommentTaskDto> kafkaTemplate;

    public void send(AddCommentTaskDto task) {
        log.info("Sending add comment task: trackId={}, comment={}, chatId={}",
                task.trackId(), task.comment(), task.chatId());
        kafkaTemplate.send(TOPIC, task);
    }
}
