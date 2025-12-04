package com.sashkomusic.mainagent.domain.service.download;

import com.sashkomusic.mainagent.ai.service.AiService;
import com.sashkomusic.mainagent.api.telegram.dto.BotResponse;
import com.sashkomusic.mainagent.domain.model.ReleaseMetadata;
import com.sashkomusic.mainagent.domain.model.SearchEngine;
import com.sashkomusic.mainagent.domain.service.search.ReleaseSearchFlowService;
import com.sashkomusic.mainagent.domain.service.search.SearchContextService;
import com.sashkomusic.mainagent.domain.util.ReleaseCardFormatter;
import com.sashkomusic.mainagent.messaging.consumer.dto.SearchFilesResultDto;
import com.sashkomusic.mainagent.messaging.producer.dto.DownloadFilesTaskDto;
import com.sashkomusic.mainagent.messaging.producer.dto.SearchFilesTaskDto;
import com.sashkomusic.mainagent.messaging.producer.DownloadTaskProducer;
import com.sashkomusic.mainagent.messaging.producer.SearchFilesTaskProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MusicDownloadFlowService {

    private final AiService aiService;
    private final SearchFilesTaskProducer searchFilesProducer;
    private final DownloadTaskProducer downloadTaskProducer;
    private final SearchContextService contextService;
    private final DownloadContextHolder downloadContextHolder;
    private final DownloadOptionsAnalyzer analyzer;
    private final DownloadOptionsFormatter formatter;
    private final ReleaseSearchFlowService releaseSearchFlowService;

    public BotResponse handleCallback(long chatId, String data) {
        if (data.startsWith("DL:")) {
            String releaseId = data.substring(3);
            log.info("User selected release ID: {}", releaseId);

            ReleaseMetadata metadata = contextService.getReleaseMetadata(releaseId);
            if (metadata == null) {
                return BotResponse.text("❌ шось ся не получило...найди реліз ше раз");
            }

            return startFileSearch(chatId, metadata);
        }

        return BotResponse.text("тєжко.");
    }

    private BotResponse startFileSearch(long chatId, ReleaseMetadata metadata) {
        searchFilesProducer.send(SearchFilesTaskDto.of(chatId, metadata.id(), metadata.artist(), metadata.title()));

        return BotResponse.text(
                "🔎 шукаю: _%s - %s_".formatted(
                        metadata.artist(),
                        metadata.title()).toLowerCase()
        );
    }

    public String handleSearchResults(SearchFilesResultDto dto) {
        log.info("Processing search results for chatId={}, releaseId={}", dto.chatId(), dto.releaseId());

        var analysisResult = analyzer.analyzeAll(dto.results(), dto.releaseId(), dto.chatId());
        var reports = analysisResult.reports();
        downloadContextHolder.saveDownloadOptions(dto.chatId(), dto.releaseId(), reports);

        reports.forEach(r -> log.info("{}", r));
        return formatter.format(reports, analysisResult.aiSummary());
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

        log.info("User chose option #{}: {} from {}", optionNumber, option.id(), option.distributorName());

        downloadTaskProducer.send(DownloadFilesTaskDto.of(chatId, releaseId, option));

        String message = "✅ **ок, качаю:**\n%s - %s\n📦 %d файлів, %d MB"
                .formatted(
                        option.distributorName(),
                        option.sourceName(),
                        option.files().size(),
                        option.totalSize()
                );

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

        return List.of(
                buildReleaseFoundCard(selectedRelease, searchResult.engine()),
                startFileSearch(chatId, selectedRelease)
        );
    }

    private BotResponse buildReleaseFoundCard(ReleaseMetadata release, SearchEngine engine) {
        String cardText = ReleaseCardFormatter.formatCardText(release);

        var buttons = new java.util.LinkedHashMap<String, String>();
        String releaseUrl = buildReleaseUrl(release, engine);
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

    private String buildReleaseUrl(ReleaseMetadata release, SearchEngine engine) {
        if (engine == null) return null;

        return switch (engine) {
            case MUSICBRAINZ -> {
                // Use release group ID (masterId) if available, otherwise release ID
                String id = release.masterId() != null ? release.masterId() : release.id();
                String type = release.masterId() != null ? "release-group" : "release";
                yield "https://musicbrainz.org/" + type + "/" + id;
            }
            case DISCOGS -> {
                // Parse "discogs:master:123" or "discogs:release:456"
                if (release.id().startsWith("discogs:master:")) {
                    String masterId = release.id().substring("discogs:master:".length());
                    yield "https://www.discogs.com/master/" + masterId;
                } else if (release.id().startsWith("discogs:release:")) {
                    String releaseId = release.id().substring("discogs:release:".length());
                    yield "https://www.discogs.com/release/" + releaseId;
                }
                yield null;
            }
            case BANDCAMP -> {
                // Bandcamp URL is stored in masterId field
                yield release.masterId();
            }
        };
    }
}
