package com.sashkomusic.mainagent.domain.service;

import com.sashkomusic.mainagent.ai.service.AiService;
import com.sashkomusic.mainagent.api.telegram.dto.BotResponse;
import com.sashkomusic.mainagent.domain.model.UserIntent;
import com.sashkomusic.mainagent.domain.service.download.MusicDownloadFlowService;
import com.sashkomusic.mainagent.domain.service.process.ProcessFolderFlowService;
import com.sashkomusic.mainagent.domain.service.search.ReleaseSearchFlowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
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
    private final ProcessFolderFlowService processFolderFlowService;

    public List<BotResponse> handleUserRequest(long chatId, String rawInput) {
        var res = processUserCommands(chatId, rawInput);
        if (!res.isEmpty()) return res;

        UserIntent intent = analyzer.classifyIntent(rawInput);
        log.info("Identified intent: {}", intent);

        return switch (intent) {
            case SEARCH_FOR_RELEASE_DEFAULT -> releaseSearchFlowService.search(chatId, rawInput, MUSICBRAINZ);
            case SEARCH_FOR_RELEASE_DISCOGS -> releaseSearchFlowService.search(chatId, rawInput, DISCOGS);
            case SEARCH_FOR_RELEASE_BANDCAMP -> releaseSearchFlowService.search(chatId, rawInput, BANDCAMP);
            case DIG_DEEPER -> releaseSearchFlowService.switchStrategyAndSearch(chatId);
            case CHOOSE_DOWNLOAD_OPTION -> musicDownloadFlowService.handleDownloadOption(chatId, rawInput);
            case DIRECT_DOWNLOAD_REQUEST -> musicDownloadFlowService.getDownloadOptions(chatId, rawInput);
            case GENERAL_CHAT, UNKNOWN -> List.of(BotResponse.text("шем не видів такого, сорі 😔"));
        };
    }

    public List<BotResponse> handleCallback(long chatId, String data) {
        if (data.startsWith("PAGE:")) {
            return releaseSearchFlowService.buildPageResponse(chatId, getPage(data));
        }
        if (data.startsWith("DL:") || data.startsWith("SEARCH_ALT:")) {
            return musicDownloadFlowService.handleCallback(chatId, data);
        }
        if (data.equals("DIG_DEEPER")) {
            return releaseSearchFlowService.switchStrategyAndSearch(chatId);
        }
        return List.of(BotResponse.text("хз, пупупу"));
    }

    private List<BotResponse> processUserCommands(long chatId, String rawInput) {
        if (rawInput.startsWith("/process")) {
            return handleProcessCommand(chatId, rawInput);
        }

        if (processFolderFlowService.hasActiveContext(chatId)) {
            return processFolderFlowService.handleMetadataSelection(chatId, rawInput);
        }
        return Collections.emptyList();
    }

    private List<BotResponse> handleProcessCommand(long chatId, String rawInput) {
        String folderName = rawInput.substring(9).trim();
        return processFolderFlowService.process(chatId, folderName);
    }

    private static int getPage(String data) {
        return Integer.parseInt(data.substring(5));
    }
}