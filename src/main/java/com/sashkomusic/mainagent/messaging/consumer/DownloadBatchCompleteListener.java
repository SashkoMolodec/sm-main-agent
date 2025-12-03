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