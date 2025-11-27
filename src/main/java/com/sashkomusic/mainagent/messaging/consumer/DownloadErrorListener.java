package com.sashkomusic.mainagent.messaging.consumer;

import com.sashkomusic.mainagent.api.telegram.TelegramChatBot;
import com.sashkomusic.mainagent.messaging.consumer.dto.DownloadErrorDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class DownloadErrorListener {

    private final TelegramChatBot chatBot;

    @KafkaListener(topics = "download-errors", groupId = "main-agent-group")
    public void handleDownloadError(DownloadErrorDto error) {
        log.error("Received download error for chatId={}: {}", error.chatId(), error.errorMessage());

        String message = "🤡 **не получилосі скачати:**\n" + error.errorMessage();
        chatBot.sendMessage(error.chatId(), message);
    }
}