package com.sashkomusic.mainagent.messaging.consumer;

import com.sashkomusic.mainagent.api.telegram.TelegramChatBot;
import com.sashkomusic.mainagent.messaging.consumer.dto.DownloadCompleteDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class DownloadCompleteListener {

    private final TelegramChatBot chatBot;

    @KafkaListener(topics = "download-complete", groupId = "main-agent-group")
    public void handleDownloadComplete(DownloadCompleteDto complete) {
        log.info("Received download complete for chatId={}: {} ({} MB)",
                complete.chatId(), complete.filename(), complete.sizeMB());

        String displayName = extractDisplayName(complete.filename());
        String message = "✅ `%s` (%d MB)".formatted(displayName, complete.sizeMB());

        chatBot.sendMessage(complete.chatId(), message);
    }

    private String extractDisplayName(String filename) {
        if (filename == null) {
            return "";
        }

        // Handle both Windows and Unix paths
        int lastSlash = Math.max(filename.lastIndexOf('\\'), filename.lastIndexOf('/'));
        if (lastSlash >= 0 && lastSlash < filename.length() - 1) {
            return filename.substring(lastSlash + 1);
        }

        return filename;
    }
}