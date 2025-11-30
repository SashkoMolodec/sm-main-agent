package com.sashkomusic.mainagent.domain.service;

import com.sashkomusic.mainagent.ai.service.AiService;
import com.sashkomusic.mainagent.api.telegram.dto.BotResponse;
import com.sashkomusic.mainagent.domain.model.UserIntent;
import com.sashkomusic.mainagent.domain.service.download.MusicDownloadFlowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.sashkomusic.mainagent.domain.model.SearchEngine.BANDCAMP;
import static com.sashkomusic.mainagent.domain.model.SearchEngine.DISCOGS;
import static com.sashkomusic.mainagent.domain.model.SearchEngine.MUSICBRAINZ;

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
            case SEARCH_FOR_RELEASE_DEFAULT -> releaseSearchFlowService.search(chatId, rawInput, MUSICBRAINZ);
            case SEARCH_FOR_RELEASE_DISCOGS -> releaseSearchFlowService.search(chatId, rawInput, DISCOGS);
            case SEARCH_FOR_RELEASE_BANDCAMP -> releaseSearchFlowService.search(chatId, rawInput, BANDCAMP);
            case DIG_DEEPER -> releaseSearchFlowService.switchStrategyAndSearch(chatId);
            case CHOOSE_DOWNLOAD_OPTION -> musicDownloadFlowService.handleDownload(chatId, rawInput);
            case GENERAL_CHAT, UNKNOWN -> List.of(BotResponse.text("шем не видів такого, сорі 😔"));
        };
    }

    public List<BotResponse> handleCallback(long chatId, String data) {
        if (data.startsWith("PAGE:")) {
            return releaseSearchFlowService.buildPageResponse(chatId, getPage(data));
        }
        if (data.startsWith("DL:")) {
            return List.of(musicDownloadFlowService.handleCallback(chatId, data));
        }
        if (data.equals("DIG_DEEPER")) {
            return releaseSearchFlowService.switchStrategyAndSearch(chatId);
        }
        return List.of(BotResponse.text("хз, пупупу"));
    }

    private static int getPage(String data) {
        return Integer.parseInt(data.substring(5));
    }
}