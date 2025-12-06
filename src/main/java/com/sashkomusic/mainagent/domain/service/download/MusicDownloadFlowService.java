package com.sashkomusic.mainagent.domain.service.download;

import com.sashkomusic.mainagent.ai.service.AiService;
import com.sashkomusic.mainagent.api.telegram.dto.BotResponse;
import com.sashkomusic.mainagent.domain.model.DownloadEngine;
import com.sashkomusic.mainagent.domain.model.ReleaseMetadata;
import com.sashkomusic.mainagent.domain.model.SearchEngine;
import com.sashkomusic.mainagent.domain.service.search.ReleaseSearchFlowService;
import com.sashkomusic.mainagent.domain.service.search.SearchContextService;
import com.sashkomusic.mainagent.domain.service.search.SearchEngineService;
import com.sashkomusic.mainagent.domain.util.ReleaseCardFormatter;
import com.sashkomusic.mainagent.messaging.consumer.dto.SearchFilesResultDto;
import com.sashkomusic.mainagent.messaging.producer.dto.DownloadFilesTaskDto;
import com.sashkomusic.mainagent.messaging.producer.dto.SearchFilesTaskDto;
import com.sashkomusic.mainagent.messaging.producer.DownloadTaskProducer;
import com.sashkomusic.mainagent.messaging.producer.SearchFilesTaskProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class MusicDownloadFlowService {

    private final AiService aiService;
    private final SearchFilesTaskProducer searchFilesProducer;
    private final DownloadTaskProducer downloadTaskProducer;
    private final SearchContextService contextService;
    private final DownloadContextHolder downloadContextHolder;
    private final DownloadOptionsFormatter formatter;
    private final ReleaseSearchFlowService releaseSearchFlowService;
    private final Map<SearchEngine, SearchEngineService> searchEngines;
    private final Map<DownloadEngine, DownloadSourceService> downloadSources;

    public List<BotResponse> handleCallback(long chatId, String data) {
        if (data.startsWith("DL:")) {
            String releaseId = data.substring(3);
            log.info("User selected release ID: {}", releaseId);

            ReleaseMetadata metadata = contextService.getReleaseMetadata(releaseId);
            if (metadata == null) {
                return List.of(BotResponse.text("❌ шось ся не получило...найди реліз ше раз"));
            }
            return initiateDownloadSearch(chatId, metadata);
        }

        if (data.startsWith("SEARCH_ALT:")) {
            int lastColonIndex = data.lastIndexOf(':');
            if (lastColonIndex == -1 || lastColonIndex <= "SEARCH_ALT:".length()) {
                return List.of(BotResponse.text("❌ шось не то з командою"));
            }

            String releaseId = data.substring("SEARCH_ALT:".length(), lastColonIndex);
            String sourceName = data.substring(lastColonIndex + 1);

            log.info("Alternative search requested: releaseId={}, source={}", releaseId, sourceName);

            ReleaseMetadata metadata = contextService.getReleaseMetadata(releaseId);
            if (metadata == null) {
                return List.of(BotResponse.text("❌ шось ся не получило...найди реліз ше раз"));
            }

            var source = DownloadEngine.valueOf(sourceName);
            return initiateDownloadSearch(chatId, metadata, source);
        }

        return List.of(BotResponse.text("тєжко."));
    }

    private List<BotResponse> initiateDownloadSearch(long chatId, ReleaseMetadata metadata) {
        return initiateDownloadSearch(chatId, metadata, DownloadEngine.QOBUZ);
    }

    private List<BotResponse> initiateDownloadSearch(long chatId, ReleaseMetadata metadata, DownloadEngine source) {
        log.info("Initiating download search for: {} - {}", metadata.artist(), metadata.title());

        searchFilesProducer.send(SearchFilesTaskDto.of(chatId, metadata.id(), metadata.artist(), metadata.title(), source));

        return List.of(BotResponse.text(
                "🔎 шукаю опції завантаження (%s): _%s - %s_".formatted(
                        source.name().toLowerCase(),
                        metadata.artist(),
                        metadata.title()).toLowerCase()
        ));
    }

    public BotResponse handleSearchResults(SearchFilesResultDto dto) {
        log.info("Processing search results for chatId={}, releaseId={}", dto.chatId(), dto.releaseId());

        if (dto.results().isEmpty()) {
            return BotResponse.text(formatter.format(List.of(), ""));
        }

        var currentSource = dto.results().getFirst().source();
        var sourceService = downloadSources.get(currentSource);

        var analysisResult = sourceService.analyzeAll(dto.results(), dto.releaseId(), dto.chatId());
        var reports = analysisResult.reports();
        downloadContextHolder.saveDownloadOptions(dto.chatId(), dto.releaseId(), reports);

        reports.forEach(r -> log.info("{}", r));

        if (sourceService.shouldAutoDownload(reports)) {
            var chosenReport = reports.getFirst();
            var option = chosenReport.option();

            log.info("Auto-downloading from {}: {}", currentSource, option.displayName());

            downloadTaskProducer.send(DownloadFilesTaskDto.of(dto.chatId(), dto.releaseId(), option));

            String message = "✅ **знайшов то шо треба, для душі, качаю:**\n`%s`".formatted(option.displayName());
            return BotResponse.text(message);
        }

        String text = formatter.format(reports, analysisResult.aiSummary());
        return sourceService.buildSearchResultsResponse(text, dto.releaseId(), currentSource);
    }

    public List<BotResponse> handleDownloadOption(long chatId, String rawInput) {
        var reports = downloadContextHolder.getDownloadOptions(chatId);
        if (reports.isEmpty()) {
            return List.of(BotResponse.text("😔 **варіанти пропали, нич нема, давай ше раз.**"));
        }

        Integer optionNumber = aiService.parseOptionNumber(rawInput);
        if (optionNumber == null || optionNumber < 1 || optionNumber > reports.size()) {
            return List.of(BotResponse.text("🤔 **незрозумілий зроз.**"));
        }

        var chosenReport = reports.get(optionNumber - 1);
        var option = chosenReport.option();
        String releaseId = downloadContextHolder.getChosenRelease(chatId);

        log.info("User chose option #{}: {} from {}", optionNumber, option.id(), option.displayName());

        downloadTaskProducer.send(DownloadFilesTaskDto.of(chatId, releaseId, option));

        var sourceService = downloadSources.get(option.source());
        String message = sourceService.formatDownloadConfirmation(option);
        return List.of(BotResponse.text(message));
    }

    public List<BotResponse> getDownloadOptions(long chatId, String query) {
        log.info("Direct download request for chatId={}, query: {}", chatId, query);

        var searchResult = releaseSearchFlowService.searchWithFallback(query, SearchEngine.MUSICBRAINZ, SearchEngine.DISCOGS);

        if (searchResult.releases().isEmpty()) {
            return List.of(BotResponse.text("😔 **нич взагалі не знайшов у світі авдіо, спробуй по-іншому.**"));
        }

        ReleaseMetadata selectedRelease = searchResult.releases().getFirst();
        log.info("Auto-selected release: {} - {} from {}",
                selectedRelease.artist(), selectedRelease.title(), searchResult.engine());

        contextService.saveSearchContext(chatId, searchResult.engine(), query,
                searchResult.searchRequest(), searchResult.releases());
        contextService.saveReleaseMetadata(selectedRelease);

        List<BotResponse> responses = new ArrayList<>();
        responses.add(buildReleaseFoundCard(selectedRelease, searchResult.engine()));
        responses.addAll(initiateDownloadSearch(chatId, selectedRelease));

        return responses;
    }

    private BotResponse buildReleaseFoundCard(ReleaseMetadata release, SearchEngine engine) {
        String cardText = ReleaseCardFormatter.formatCardText(release);

        var buttons = new java.util.LinkedHashMap<String, String>();
        String releaseUrl = searchEngines.get(engine).buildReleaseUrl(release);
        if (releaseUrl != null) {
            String buttonLabel = switch (engine) {
                case MUSICBRAINZ -> "🎵 musicbrainz";
                case DISCOGS -> "💿 discogs";
                case BANDCAMP -> "📼 bandcamp";
                default -> "🔗 link";
            };
            buttons.put(buttonLabel, "URL:" + releaseUrl);
        }

        return BotResponse.card(cardText, release.getCoverArtUrl(), buttons.isEmpty() ? null : buttons);
    }
}
