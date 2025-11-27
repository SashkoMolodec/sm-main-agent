package com.sashkomusic.mainagent.messaging.consumer;

import com.sashkomusic.mainagent.api.telegram.TelegramChatBot;
import com.sashkomusic.mainagent.domain.service.download.MusicDownloadFlowService;
import com.sashkomusic.mainagent.messaging.consumer.dto.SearchFilesResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SearchFilesResultListener {

    private final MusicDownloadFlowService musicDownloadFlowService;
    private final TelegramChatBot telegramBot;

    @KafkaListener(topics = "file-search-results", groupId = "main-agent-group")
    public void handleSearchResults(SearchFilesResultDto dto) {
        String message = musicDownloadFlowService.handleSearchResults(dto);
        telegramBot.sendMessage(dto.chatId(), message);
    }
}