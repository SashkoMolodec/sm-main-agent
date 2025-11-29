package com.sashkomusic.mainagent.domain.service;

import com.sashkomusic.mainagent.ai.service.AiService;
import com.sashkomusic.mainagent.api.telegram.dto.BotResponse;
import com.sashkomusic.mainagent.domain.model.MetadataSearchRequest;
import com.sashkomusic.mainagent.domain.model.SearchEngine;
import com.sashkomusic.mainagent.domain.model.ReleaseMetadata;
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
        log.info("Searching with engine: {}", searchEngine);
        var searchRequest = analyzer.buildSearchRequest(rawInput);

        var engine = searchEngines.get(searchEngine);
        var releases = engine.searchReleases(searchRequest);

        if (releases.isEmpty()) {
            var buttons = buildSearchButtons(searchRequest);
            buttons.put("⛏️", "DIG_DEEPER");
            return List.of(BotResponse.withButtons("😔 нич не знайшов в %s.".formatted(engine.getName()), buttons));
        }

        contextService.saveSearchContext(chatId, searchEngine, rawInput, searchRequest, releases);
        return buildPageResponse(chatId, 0);
    }

    public List<BotResponse> switchStrategyAndSearch(long chatId) {
        SearchEngine currentEngine = contextService.getSearchEngine(chatId);
        if (currentEngine == SearchEngine.MUSICBRAINZ) {
            String rawInput = contextService.getRawInput(chatId);
            return search(chatId, rawInput, SearchEngine.DISCOGS);
        } else {
            return List.of(BotResponse.text("😔 глибше нікуди, вшьо."));
        }
    }

    public List<BotResponse> buildPageResponse(long chatId, int page) {
        var releases = contextService.getSearchResults(chatId);
        var searchRequest = contextService.getSearchRequest(chatId);
        var responses = new ArrayList<BotResponse>();

        int start = page * PAGE_SIZE;
        if (start >= releases.size()) {
            return List.of(BotResponse.text("більше результатів немає."));
        }

        responses.add(BotResponse.text(resolveFoundReleasesMessage(releases, page)));

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
        String cardText = """
                💿 %s
                👤 %s
                %s • %s • %s тр.
                """.formatted(
                release.title(),
                release.artist(),
                release.getYearsDisplay(),
                release.getTypesDisplay(),
                release.getTrackCountDisplay()
        ).toLowerCase();

        Map<String, String> buttons = new LinkedHashMap<>();
        addYoutubeButton(buttons, searchRequest.getYoutubeUrl());
        addDiscogsButton(buttons, searchRequest.getDiscogsUrl());
        addBandcampButton(buttons, searchRequest.getBandcampUrl());
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

    private static String resolveFoundReleasesMessage(List<ReleaseMetadata> releases, int page) {
        if (page == 0) {
            if (releases.size() == 1) {
                return "🔎 знайдено реліз";
            }
            return "🔎 знайдено релізів: %d".formatted(releases.size());
        } else {
            return "📄 сторінка %d".formatted(page + 1);
        }
    }

    private static Map<String, String> buildSearchButtons(MetadataSearchRequest searchRequest) {
        Map<String, String> buttons = new LinkedHashMap<>();
        addYoutubeButton(buttons, searchRequest.getYoutubeUrl());
        addDiscogsButton(buttons, searchRequest.getDiscogsUrl());
        addBandcampButton(buttons, searchRequest.getBandcampUrl());
        return buttons;
    }

    private static void addYoutubeButton(Map<String, String> buttons, String url) {
        buttons.put("📺", "URL:" + url);
    }

    private static void addDiscogsButton(Map<String, String> buttons, String url) {
        buttons.put("💿", "URL:" + url);
    }

    private static void addBandcampButton(Map<String, String> buttons, String url) {
        buttons.put("📼", "URL:" + url);
    }
}
