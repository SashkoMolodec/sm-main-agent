package com.sashkomusic.mainagent.messaging.consumer;

import com.sashkomusic.mainagent.api.telegram.TelegramChatBot;
import com.sashkomusic.mainagent.messaging.consumer.dto.TrackUpdateResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class TrackUpdateResultListener {

    private final TelegramChatBot chatBot;

    @KafkaListener(topics = "track-update-results", groupId = "main-agent-group")
    public void handleTrackUpdateResult(TrackUpdateResultDto result) {
        log.info("Received track update result: trackId={}, field={}, value={}, success={}",
                result.trackId(), result.fieldUpdated(), result.value(), result.success());

        String message = result.success()
                ? "✅ оновлено"
                : "❌ помилка: " + result.message();

        chatBot.sendMessage(result.chatId(), message);
    }
}
