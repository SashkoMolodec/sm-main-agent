package com.sashkomusic.mainagent.domain.service;

import com.sashkomusic.mainagent.ai.service.AiService;
import com.sashkomusic.mainagent.api.telegram.dto.BotResponse;
import com.sashkomusic.mainagent.domain.model.Language;
import com.sashkomusic.mainagent.domain.model.MetadataSearchRequest;
import com.sashkomusic.mainagent.domain.model.SearchEngine;
import com.sashkomusic.mainagent.domain.model.ReleaseMetadata;
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
    private final SearchContextHolder contextService;

    public List<BotResponse> search(long chatId, String rawInput, SearchEngine searchEngine) {
        if (continueDigging(rawInput)) {
            rawInput = contextService.getRawInput(chatId);
        }

        log.info("Searching with engine: {}", searchEngine);
        var searchRequest = analyzer.buildSearchRequest(rawInput);

        var engine = searchEngines.get(searchEngine);
        var releases = engine.searchReleases(searchRequest);

        contextService.saveSearchContext(chatId, searchEngine, rawInput, searchRequest, releases);

        if (releases.isEmpty()) {
            var buttons = buildSearchButtons(searchRequest);
            buttons.put("⛏️", "DIG_DEEPER");
            return List.of(BotResponse.withButtons("😔 нич не знайшов в тому %s.".formatted(engine.getName()), buttons));
        }
        return buildPageResponse(chatId, 0);
    }

    public List<BotResponse> switchStrategyAndSearch(long chatId) {
        SearchEngine currentEngine = contextService.getSearchEngine(chatId);
        String rawInput = contextService.getRawInput(chatId);

        if (currentEngine == SearchEngine.MUSICBRAINZ) {
            return search(chatId, rawInput, SearchEngine.DISCOGS);
        } else if (currentEngine == SearchEngine.DISCOGS) {
            return search(chatId, rawInput, SearchEngine.BANDCAMP);
        } else {
            return List.of(BotResponse.text("😔 глибше нікуди, вшьо."));
        }
    }

    public List<BotResponse> buildPageResponse(long chatId, int page) {
        var releases = contextService.getSearchResults(chatId);
        var searchRequest = contextService.getSearchRequest(chatId);
        var searchEngine = contextService.getSearchEngine(chatId);
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

    private static BotResponse buildReleaseCard(ReleaseMetadata release, MetadataSearchRequest searchRequest) {
        String metadataLine = "%s • %s".formatted(release.getYearsDisplay(), release.getTypesDisplay());
        metadataLine = addTrackCount(metadataLine, release.getTrackCountDisplay());

        String cardText = """
                💿 %s
                👤 %s
                %s
                """.formatted(
                release.title(),
                release.artist(),
                metadataLine
        ).toLowerCase();

        Map<String, String> buttons = new LinkedHashMap<>();
        buttons.put("▶️", buildYoutubeUrl(release.artist(), release.title()));
        buttons.put("💿", buildDiscogsUrl(release.artist(), release.title()));
        buttons.put("📼", buildBandcampUrl(release.artist(), release.title()));
        buttons.put("⬇️", "DL:" + release.id());

        return BotResponse.card(
                cardText,
                release.getCoverArtUrl(),
                buttons);
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
                ? " (%s)".formatted(searchEngine.name().toLowerCase())
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

    private static Map<String, String> buildSearchButtons(MetadataSearchRequest searchRequest) {
        Map<String, String> buttons = new LinkedHashMap<>();
        String artist = searchRequest.artist();
        String title = searchRequest.getTitle();
        buttons.put("▶️", buildYoutubeUrl(artist, title));
        buttons.put("💿", buildDiscogsUrl(artist, title));
        buttons.put("📼", buildBandcampUrl(artist, title));
        return buttons;
    }

    private static String buildYoutubeUrl(String artist, String title) {
        Language language = SearchUrlUtils.detectLanguage(artist, title);
        String albumWord = SearchUrlUtils.buildYoutubeAlbumWord(language);
        String query = artist + " " + title + " " + albumWord;
        String url = "https://www.youtube.com/results?search_query=" + SearchUrlUtils.encode(query);
        return "URL:" + url;
    }

    private static String buildDiscogsUrl(String artist, String title) {
        String query = artist + " " + title;
        String url = "https://www.discogs.com/search/?q=" + SearchUrlUtils.encode(query);
        return "URL:" + url;
    }

    private static String buildBandcampUrl(String artist, String title) {
        String query = artist + " " + title;
        String url = "https://bandcamp.com/search?q=" + SearchUrlUtils.encode(query);
        return "URL:" + url;
    }

    private static String addTrackCount(String metadataLine, String trackCount) {
        if (trackCount == null || trackCount.isEmpty()) {
            return metadataLine;
        }
        return metadataLine + " • " + trackCount + " тр.";
    }

    private boolean continueDigging(String rawInput) {
        String lower = rawInput.toLowerCase().trim();
        return lower.contains("копай")
                || lower.contains("ше")
                || lower.contains("блять");
    }
}
