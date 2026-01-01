package com.sashkomusic.mainagent.domain.service.search;

import com.sashkomusic.mainagent.ai.service.AiService;
import com.sashkomusic.mainagent.api.telegram.dto.BotResponse;
import com.sashkomusic.mainagent.domain.model.MetadataSearchRequest;
import com.sashkomusic.mainagent.domain.model.SearchEngine;
import com.sashkomusic.mainagent.domain.model.ReleaseMetadata;
import com.sashkomusic.mainagent.domain.util.ReleaseCardFormatter;
import com.sashkomusic.mainagent.domain.util.SearchUrlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReleaseSearchFlowService {
    private static final int PAGE_SIZE = 3;

    private final AiService analyzer;
    private final Map<SearchEngine, SearchEngineService> searchEngines;
    private final SearchContextService contextService;

    public List<BotResponse> searchDefault(long chatId, String rawInput) {
        var searchRequest = analyzer.buildSearchRequest(rawInput);
        for (SearchEngine engine : SearchEngine.values()) {
            log.info("Trying to search in {}", engine);

            var releases = searchEngines.get(engine).searchReleases(searchRequest);
            if (!releases.isEmpty()) {
                contextService.saveSearchContext(chatId, engine, rawInput, searchRequest, releases);
                return buildPageResponse(chatId, 0);
            }
        }
        var buttons = buildEmptyResultsButtons(searchRequest);
        return List.of(BotResponse.withButtons("😔 нич не знайшов.", buttons));
    }

    public List<BotResponse> search(long chatId, String rawInput, SearchEngine searchEngine) {
        log.info("Searching with engine: {}", searchEngine);
        var searchRequest = analyzer.buildSearchRequest(rawInput);

        var engine = searchEngines.get(searchEngine);
        var releases = engine.searchReleases(searchRequest);

        contextService.saveSearchContext(chatId, searchEngine, rawInput, searchRequest, releases);

        if (releases.isEmpty()) {
            var buttons = buildEmptyResultsButtons(searchRequest);
            return List.of(BotResponse.withButtons("😔 нич не знайшов в тому %s.".formatted(engine.getName()), buttons));
        }
        return buildPageResponse(chatId, 0);
    }

    public List<BotResponse> switchStrategyAndSearch(long chatId) {
        SearchEngine currentEngine = contextService.getSource(chatId);
        String rawInput = contextService.getRawInput(chatId);

        if (currentEngine == SearchEngine.MUSICBRAINZ) {
            return search(chatId, rawInput, SearchEngine.DISCOGS);
        } else if (currentEngine == SearchEngine.DISCOGS) {
            return search(chatId, rawInput, SearchEngine.BANDCAMP);
        } else {
            return List.of(BotResponse.text("😔 глибше нікуди, вшьо."));
        }
    }

    public SearchResult searchWithFallback(String query, SearchEngine... engines) {
        var searchRequest = analyzer.buildSearchRequest(query);

        for (SearchEngine engine : engines) {
            log.info("Trying to search in {}", engine);
            var searchEngineService = searchEngines.get(engine);
            var releases = searchEngineService.searchReleases(searchRequest);

            if (!releases.isEmpty()) {
                log.info("Found {} releases in {}", releases.size(), engine);
                return new SearchResult(releases, engine, searchRequest);
            }
        }

        log.warn("No releases found in any engine for query: {}", query);
        return new SearchResult(List.of(), null, searchRequest);
    }

    public record SearchResult(List<ReleaseMetadata> releases, SearchEngine engine,
                               MetadataSearchRequest searchRequest) {
    }

    public List<BotResponse> handlePageCallback(long chatId, String callbackData) {
        int page = Integer.parseInt(callbackData.substring("PAGE:".length()));
        return buildPageResponse(chatId, page);
    }

    public List<BotResponse> buildPageResponse(long chatId, int page) {
        var releases = contextService.getSearchResults(chatId);
        var searchRequest = contextService.getSearchRequest(chatId);
        var searchEngine = contextService.getSource(chatId);
        var responses = new ArrayList<BotResponse>();

        int start = page * PAGE_SIZE;
        if (start >= releases.size()) {
            return List.of(BotResponse.text("більше результатів немає."));
        }

        responses.add(BotResponse.text(resolveFoundReleasesMessage(releases, page, searchEngine)));

        int end = Math.min(start + PAGE_SIZE, releases.size());
        for (int i = start; i < end; i++) {
            var release = releases.get(i);
            responses.add(buildReleaseCard(release, searchRequest));
        }

        if (end < releases.size()) {
            responses.add(buildPageNavigation(releases, page, end));
        }
        return responses;
    }

    private BotResponse buildReleaseCard(ReleaseMetadata release, MetadataSearchRequest searchRequest) {
        String cardText = ReleaseCardFormatter.formatCardText(release);

        Map<String, String> buttons = new LinkedHashMap<>();
        buttons.put("🎧", "STREAM:" + release.id());

        String releaseUrl = buildReleaseUrlForSource(release);
        if (releaseUrl != null) {
            buttons.put("🔗", releaseUrl);
        }

        buttons.put("⬇️", "DL:" + release.id());

        return BotResponse.card(
                cardText,
                release.getCoverArtUrl(),
                buttons);
    }

    private String buildReleaseUrlForSource(ReleaseMetadata release) {
        try {
            SearchEngine engine = SearchEngine.valueOf(release.source().name());
            SearchEngineService service = searchEngines.get(engine);
            if (service != null) {
                String url = service.buildReleaseUrl(release);
                if (url != null && !url.isEmpty()) {
                    return "URL:" + url;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to build release URL for source {}: {}", release.source(), e.getMessage());
        }
        return null;
    }

    private static BotResponse buildPageNavigation(List<ReleaseMetadata> releases, int page, int end) {
        int nextPage = page + 1;
        int remaining = releases.size() - end;

        Map<String, String> navButtons = new LinkedHashMap<>();
        navButtons.put("➡️ показати ще %d".formatted(Math.min(remaining, PAGE_SIZE)), "PAGE:" + nextPage);

        String navText = "залишилось ще %d релізів".formatted(remaining);
        return BotResponse.withButtons(navText, navButtons);
    }

    private static String resolveFoundReleasesMessage(List<ReleaseMetadata> releases, int page, SearchEngine searchEngine) {
        String engineSuffix = searchEngine != SearchEngine.MUSICBRAINZ
                ? " (%s)".formatted(searchEngine.getName())
                : "";

        if (page == 0) {
            if (releases.size() == 1) {
                return "🔎 знайдено реліз%s".formatted(engineSuffix);
            }
            return "🔎 знайдено релізів: %d%s".formatted(releases.size(), engineSuffix);
        } else {
            return "📄 сторінка %d".formatted(page + 1);
        }
    }

    private static LinkedHashMap<String, String> buildEmptyResultsButtons(MetadataSearchRequest searchRequest) {
        var buttons = new LinkedHashMap<String, String>();
        buttons.put("🎧", "STREAM:");
        buttons.put("💿", SearchUrlUtils.buildDiscogsSearchUrl(searchRequest.artist(), searchRequest.getTitle()));
        buttons.put("⛏️", "DIG_DEEPER");
        return buttons;
    }

    public BotResponse buildReleaseDownloadCard(ReleaseMetadata release, SearchEngine engine) {
        String cardText = ReleaseCardFormatter.formatCardText(release);

        var buttons = new LinkedHashMap<String, String>();
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
