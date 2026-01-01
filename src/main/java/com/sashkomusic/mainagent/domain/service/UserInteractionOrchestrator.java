package com.sashkomusic.mainagent.domain.service;

import com.sashkomusic.mainagent.ai.service.AiService;
import com.sashkomusic.mainagent.api.telegram.dto.BotResponse;
import com.sashkomusic.mainagent.domain.model.UserIntent;
import com.sashkomusic.mainagent.domain.service.djtag.DjTagFlowService;
import com.sashkomusic.mainagent.domain.service.download.MusicDownloadFlowService;
import com.sashkomusic.mainagent.domain.service.process.ProcessFolderFlowService;
import com.sashkomusic.mainagent.domain.service.process.ReprocessReleasesFlowService;
import com.sashkomusic.mainagent.domain.service.search.ReleaseSearchFlowService;
import com.sashkomusic.mainagent.domain.service.streaming.StreamingFlowService;
import com.sashkomusic.mainagent.infrastracture.client.navidrome.NavidromeClient;
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
    private final StreamingFlowService streamingFlowService;
    private final NowPlayingFlowService nowPlayingFlowService;
    private final ReprocessReleasesFlowService reprocessReleasesFlowService;
    private final DjTagFlowService djTagFlowService;
    private final UtilFlowService utilFlowService;

    public List<BotResponse> handleUserRequest(long chatId, String rawInput) {
        var res = processOngoingFlow(chatId, rawInput);
        if (!res.isEmpty()) return res;

        res = processUserCommands(chatId, rawInput);
        if (!res.isEmpty()) return res;

        UserIntent intent = analyzer.classifyIntent(rawInput);
        log.info("Identified intent: {}", intent);

        return switch (intent) {
            case SEARCH_FOR_RELEASE_DEFAULT -> releaseSearchFlowService.searchDefault(chatId, rawInput);
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
            return releaseSearchFlowService.handlePageCallback(chatId, data);
        }
        if (data.startsWith("DL:") || data.startsWith("SEARCH_ALT:")) {
            return musicDownloadFlowService.handleCallback(chatId, data);
        }
        if (data.equals("DIG_DEEPER")) {
            return releaseSearchFlowService.switchStrategyAndSearch(chatId);
        }
        if (data.startsWith("STREAM:")) {
            return streamingFlowService.handleStreamingPlatforms(chatId, data);
        }
        if (data.startsWith("RATE:")) {
            return nowPlayingFlowService.handleRate(chatId, data);
        }
        if (data.startsWith("EXPAND_DJ_RATE:")) {
            return djTagFlowService.expandDjRatePanel(chatId, data);
        }
        if (data.startsWith("ENERGY_RATE:")) {
            return djTagFlowService.handleEnergyRate(chatId, data);
        }
        if (data.startsWith("FUNCTION_RATE:")) {
            return djTagFlowService.handleFunctionRate(chatId, data);
        }
        if (data.startsWith("ADD_COMMENT:")) {
            return djTagFlowService.handleCommentAdd(chatId, data);
        }
        return List.of(BotResponse.text("хз, пупупу"));
    }

    private List<BotResponse> processOngoingFlow(long chatId, String rawInput) {
        if (djTagFlowService.isWaitingForComment(chatId)) {
            return djTagFlowService.handleCommentInput(chatId, rawInput);
        } else return Collections.emptyList();
    }

    private List<BotResponse> processUserCommands(long chatId, String rawInput) {
        if (rawInput.equalsIgnoreCase("стоп")) {
            return utilFlowService.clearAllCaches();
        }

        if (rawInput.startsWith("/np")) {
            return nowPlayingFlowService.nowPlaying(chatId);
        }

        if (rawInput.startsWith("/process")) {
            return processFolderFlowService.handleProcessCommand(chatId, rawInput);
        }

        if (rawInput.startsWith("/reprocess")) {
            ReprocessReleasesFlowService.ReprocessResult result = reprocessReleasesFlowService.handle(chatId, rawInput);
            return List.of(BotResponse.text(result.message()));
        }

        if (processFolderFlowService.hasActiveContext(chatId)) {
            return processFolderFlowService.handleMetadataSelection(chatId, rawInput);
        }
        return Collections.emptyList();
    }
}