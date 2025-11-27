package com.sashkomusic.mainagent.messaging.consumer;

import com.sashkomusic.mainagent.api.telegram.TelegramChatBot;
import com.sashkomusic.mainagent.messaging.consumer.dto.DownloadBatchCompleteDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class DownloadBatchCompleteListener {

    private final TelegramChatBot chatBot;

    @KafkaListener(topics = "download-batch-complete", groupId = "main-agent-group")
    public void handleBatchComplete(DownloadBatchCompleteDto batchComplete) {
        log.info("Received download batch complete for chatId={}, releaseId={}, files={}",
                batchComplete.chatId(), batchComplete.releaseId(), batchComplete.totalFiles());

        String message = buildCompletionMessage(batchComplete);
        chatBot.sendMessage(batchComplete.chatId(), message);
    }

    private String buildCompletionMessage(DownloadBatchCompleteDto batch) {
        String folderName = extractFolderName(batch.directoryPath());
        return "🎉 **весь реліз скачавсі!** 📂 `%s` (%d файлів)".formatted(folderName, batch.totalFiles());
    }

    private String extractFolderName(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        int lastSlash = Math.max(path.lastIndexOf('\\'), path.lastIndexOf('/'));
        if (lastSlash >= 0) {
            return path.substring(lastSlash + 1);
        }
        return path;
    }
}