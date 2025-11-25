package com.sashkomusic.mainagent.domain.service;

import com.sashkomusic.mainagent.ai.service.AiService;
import com.sashkomusic.mainagent.api.telegram.dto.BotResponse;
import com.sashkomusic.mainagent.domain.model.Language;
import com.sashkomusic.mainagent.domain.model.ReleaseMetadata;
import com.sashkomusic.mainagent.domain.model.SearchRequest;
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
    private final MusicMetadataService musicMetadataService;
    private final SearchContextHolder contextService;

    public List<BotResponse> search(long chatId, String rawInput) {
        var searchRequest = analyzer.buildSearchRequest(rawInput);

        var releases = musicMetadataService.searchReleases(searchRequest.luceneQuery());
        if (releases.isEmpty()) {
            var buttons = buildSearchButtons(searchRequest);
            return List.of(BotResponse.withButtons("😔 нич не знайшов в musicbrainz", buttons));
        }

        contextService.saveSearchResults(chatId, searchRequest, releases);

        return generatePageResponse(releases, searchRequest, 0);
    }

    public List<BotResponse> handlePagination(long chatId, int page) {
        var releases = contextService.getSearchResults(chatId);
        var searchRequest = contextService.getSearchRequest(chatId);

        if (sessionOutdated(releases, searchRequest)) {
            return List.of(BotResponse.text("⚠️ сесія пошуку застаріла. зробіть новий запит."));
        }

        return generatePageResponse(releases, searchRequest, page);
    }

    private List<BotResponse> generatePageResponse(List<ReleaseMetadata> releases, SearchRequest searchRequest, int page) {
        List<BotResponse> responses = new ArrayList<>();

        int start = page * PAGE_SIZE;
        if (start >= releases.size()) {
            return List.of(BotResponse.text("більше результатів немає."));
        }

        responses.add(BotResponse.text(resolveFoundReleasesMessage(releases, page)));

        int end = Math.min(start + PAGE_SIZE, releases.size());
        for (int i = start; i < end; i++) {
            var release = releases.get(i);
            responses.add(buildReleaseCard(release));
        }

        if (end < releases.size()) {
            responses.add(buildPageNavigation(releases, page, end));
        }
        return responses;
    }

    private static BotResponse buildReleaseCard(ReleaseMetadata release) {
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
        addYoutubeButton(buttons, release.getYoutubeUrl());
        addDiscogsButton(buttons, release.getDiscogsUrl());
        addBandcampButton(buttons, release.getBandcampUrl());
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

    private static Map<String, String> buildSearchButtons(SearchRequest searchRequest) {
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

    private static boolean sessionOutdated(List<ReleaseMetadata> releases, SearchRequest searchRequest) {
        return releases == null || releases.isEmpty() || searchRequest == null;
    }
}
