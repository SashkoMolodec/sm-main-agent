package com.sashkomusic.mainagent.domain.service;

import com.sashkomusic.mainagent.ai.service.AiService;
import com.sashkomusic.mainagent.api.telegram.dto.BotResponse;
import com.sashkomusic.mainagent.domain.model.UserIntent;
import com.sashkomusic.mainagent.domain.service.download.MusicDownloadFlowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserInteractionOrchestrator {

    private final AiService analyzer;
    private final MusicDownloadFlowService musicDownloadFlowService;
    private final ReleaseSearchFlowService releaseSearchFlowService;

    public List<BotResponse> handleUserRequest(long chatId, String rawInput) {
        UserIntent intent = analyzer.classifyIntent(rawInput);
        log.info("Identified intent: {}", intent);

        return switch (intent) {
            case SEARCH_FOR_RELEASE -> releaseSearchFlowService.search(chatId, rawInput);
            case GENERAL_CHAT, UNKNOWN -> List.of(BotResponse.text("Ше не вмію такого, сорі 😔"));
        };
    }

    public List<BotResponse> handleCallback(long chatId, String data) {
        if (data.startsWith("PAGE:")) {
            int page = Integer.parseInt(data.substring(5));
            return releaseSearchFlowService.handlePagination(chatId, page);
        }
        if (data.startsWith("DL:")) {
            return List.of(musicDownloadFlowService.handleCallback(chatId, data));
        }
        return List.of(BotResponse.text("хз, пупупу"));
    }
}