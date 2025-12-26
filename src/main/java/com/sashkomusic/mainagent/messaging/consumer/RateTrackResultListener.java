package com.sashkomusic.mainagent.messaging.consumer;

import com.sashkomusic.mainagent.api.telegram.TelegramChatBot;
import com.sashkomusic.mainagent.messaging.consumer.dto.RateTrackResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class RateTrackResultListener {

    private final TelegramChatBot chatBot;

    @KafkaListener(topics = "rate-track-results", groupId = "main-agent-group")
    public void handleRateTrackResult(RateTrackResultDto result) {
        log.info("Received rate track result: trackId={}, rating={}, success={}",
                result.trackId(), result.rating(), result.success());

        String message = result.success()
                ? buildSuccessMessage(result.rating())
                : buildErrorMessage(result.message());

        chatBot.sendMessage(result.chatId(), message);
    }

    private String buildSuccessMessage(int rating) {
        String stars = "★".repeat(rating) + "☆".repeat(5 - rating);
        return "✅ оцінено: " + stars;
    }

    private String buildErrorMessage(String errorMessage) {
        return "❌ помилка: " + errorMessage;
    }
}
