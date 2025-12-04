package com.sashkomusic.mainagent.messaging.consumer;

import com.sashkomusic.mainagent.api.telegram.TelegramChatBot;
import com.sashkomusic.mainagent.domain.model.ReleaseMetadata;
import com.sashkomusic.mainagent.domain.service.search.SearchContextService;
import com.sashkomusic.mainagent.messaging.consumer.dto.DownloadBatchCompleteDto;
import com.sashkomusic.mainagent.messaging.producer.dto.ProcessLibraryTaskDto;
import com.sashkomusic.mainagent.messaging.producer.ProcessLibraryTaskProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class DownloadBatchCompleteListener {

    private final TelegramChatBot chatBot;
    private final ProcessLibraryTaskProducer libraryTaskProducer;
    private final SearchContextService contextHolder;

    @KafkaListener(topics = "download-batch-complete", groupId = "main-agent-group")
    public void handleBatchComplete(DownloadBatchCompleteDto batchComplete) {
        log.info("Received download batch complete for chatId={}, releaseId={}, files={}",
                batchComplete.chatId(), batchComplete.releaseId(), batchComplete.totalFiles());

        String message = buildCompletionMessage(batchComplete);
        chatBot.sendMessage(batchComplete.chatId(), message);

        sendToLibraryProcess(batchComplete);
    }

    private void sendToLibraryProcess(DownloadBatchCompleteDto batchComplete) {
        ReleaseMetadata metadata = contextHolder.getReleaseMetadata(batchComplete.releaseId());
        if (metadata != null) {
            String masterId = metadata.masterId();

            ProcessLibraryTaskDto libraryTask = ProcessLibraryTaskDto.of(
                    batchComplete.chatId(),
                    batchComplete.directoryPath(),
                    batchComplete.allFiles(),
                    metadata
            );

            libraryTaskProducer.send(libraryTask);
            log.info("Sent library processing task for releaseId={}, masterId={}",
                    batchComplete.releaseId(), masterId);
        } else {
            log.warn("No metadata found for releaseId={}, skipping library processing", batchComplete.releaseId());
        }
    }

    private String buildCompletionMessage(DownloadBatchCompleteDto batch) {
        String[] pathParts = extractPathParts(batch.directoryPath());
        String artist = pathParts[0];
        String release = pathParts[1];

        StringBuilder message = new StringBuilder();
        message.append("✅ **додано в лібку!**\n");
        message.append("`%s` → `%s`\n\n".formatted(artist, release));

        for (String track : batch.allFiles()) {
            message.append("`%s`\n".formatted(track));
        }
        return message.toString();
    }

    private String[] extractPathParts(String path) {
        if (path == null || path.isEmpty()) {
            return new String[]{"Unknown", "Unknown"};
        }

        String[] parts = path.split("[/\\\\]");

        if (parts.length >= 2) {
            String release = parts[parts.length - 1];
            String artist = parts[parts.length - 2];
            return new String[]{artist, release};
        } else if (parts.length == 1) {
            return new String[]{"Unknown", parts[0]};
        }

        return new String[]{"Unknown", "Unknown"};
    }
}