package com.sashkomusic.mainagent.messaging.consumer;

import com.sashkomusic.mainagent.api.telegram.TelegramChatBot;
import com.sashkomusic.mainagent.domain.service.download.DownloadOptionsAnalyzer;
import com.sashkomusic.mainagent.domain.service.download.DownloadOptionsFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SearchFilesResultListener {

    private final DownloadOptionsAnalyzer analyzer;
    private final DownloadOptionsFormatter formatter;
    private final TelegramChatBot telegramBot;

    @KafkaListener(topics = "file-search-results", groupId = "main-agent-group")
    public void handleSearchResults(SearchFilesResultDto dto) {
        var analysisResult = analyzer.analyzeAll(dto.results(), dto.releaseId());

        String message = formatter.format(analysisResult.reports(), analysisResult.aiSummary());
        telegramBot.sendMessage(dto.chatId(), message);
    }
}