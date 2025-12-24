package com.sashkomusic.mainagent.messaging.consumer;

import com.sashkomusic.mainagent.api.telegram.TelegramChatBot;
import com.sashkomusic.mainagent.domain.service.process.ProcessFolderFlowService;
import com.sashkomusic.mainagent.messaging.consumer.dto.DownloadBatchCompleteDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class DownloadBatchCompleteListener {

    private final ProcessFolderFlowService processFolderFlowService;
    private final TelegramChatBot telegramBot;

    @KafkaListener(topics = "download-batch-complete", groupId = "main-agent-group")
    public void handleBatchComplete(DownloadBatchCompleteDto batchComplete) {
        log.info("Received download batch complete for chatId={}, releaseId={}, files={}",
                batchComplete.chatId(), batchComplete.releaseId(), batchComplete.totalFiles());

        processFolderFlowService.process(batchComplete.chatId(), batchComplete.directoryPath())
                .forEach(msg -> telegramBot.sendResponse(batchComplete.chatId(), msg));
    }
}