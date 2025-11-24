package com.sashkomusic.mainagent.domain.service;

import com.sashkomusic.mainagent.ai.service.AiService;
import com.sashkomusic.mainagent.api.telegram.dto.BotResponse;
import com.sashkomusic.mainagent.domain.model.MusicSearchMetadata;
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
    private final SearchContextService contextService;

    public List<BotResponse> search(long chatId, String rawInput) {
        var query = analyzer.buildMusicBrainzSearchQuery(rawInput);

        var releases = musicMetadataService.searchReleases(query);
        if (releases.isEmpty()) {
            String artist = extractFieldFromQuery(query, "artist");
            String title = extractFieldFromQuery(query, "release");
            if (title.isEmpty()) {
                title = extractFieldFromQuery(query, "recording");
            }

            var buttons = buildSearchButtons(artist, title);
            return List.of(BotResponse.withButtons("😔 нич не знайшов в musicbrainz", buttons));
        }
        contextService.saveSearchResults(chatId, releases);

        return generatePageResponse(releases, 0);
    }

    public List<BotResponse> handlePagination(long chatId, int page) {
        var releases = contextService.getSearchResults(chatId);

        if (releases == null || releases.isEmpty()) {
            return List.of(BotResponse.text("⚠️ сесія пошуку застаріла. зробіть новий запит."));
        }

        return generatePageResponse(releases, page);
    }

    private List<BotResponse> generatePageResponse(List<MusicSearchMetadata> releases, int page) {
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

    private static BotResponse buildReleaseCard(MusicSearchMetadata release) {
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

        Map<String, String> buttons = buildSearchButtons(release.artist(), release.title());
        buttons.put("⬇️", "DL:" + release.id());

        return BotResponse.card(
                cardText,
                release.getCoverArtUrl(),
                buttons);
    }

    private static BotResponse buildPageNavigation(List<MusicSearchMetadata> releases, int page, int end) {
        int nextPage = page + 1;
        int remaining = releases.size() - end;

        Map<String, String> navButtons = new LinkedHashMap<>();
        navButtons.put("➡️ показати ще %d".formatted(Math.min(remaining, PAGE_SIZE)), "PAGE:" + nextPage);

        String navText = "залишилось ще %d релізів".formatted(remaining);
        return BotResponse.withButtons(navText, navButtons);
    }

    private static String resolveFoundReleasesMessage(List<MusicSearchMetadata> releases, int page) {
        if (page == 0) {
            if (releases.size() == 1) {
                return "🔎 знайдено реліз";
            }
            return "🔎 знайдено релізів: %d".formatted(releases.size());
        } else {
            return "📄 сторінка %d".formatted(page + 1);
        }
    }

    private static Map<String, String> buildSearchButtons(String artist, String title) {
        Map<String, String> buttons = new LinkedHashMap<>();
        buttons.put("📺", "URL:" + buildYoutubeUrl(artist, title));
        buttons.put("💿", "URL:" + buildDiscogsUrl(artist, title));
        buttons.put("📼", "URL:" + buildBandcampUrl(artist, title));
        return buttons;
    }

    private static String buildYoutubeUrl(String artist, String title) {
        String query = artist + " " + title + " full album";
        return "https://www.youtube.com/results?search_query=" + encode(query);
    }

    private static String buildDiscogsUrl(String artist, String title) {
        String query = artist + " " + title;
        return "https://www.discogs.com/search/?q=" + encode(query) + "&type=release";
    }

    private static String buildBandcampUrl(String artist, String title) {
        String query = artist + " " + title;
        return "https://bandcamp.com/search?q=" + encode(query);
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String extractFieldFromQuery(String query, String fieldName) {
        var pattern = java.util.regex.Pattern.compile(fieldName + ":\"([^\"]+)\"");
        var matcher = pattern.matcher(query);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }
}
