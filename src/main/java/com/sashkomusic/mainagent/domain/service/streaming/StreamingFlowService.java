package com.sashkomusic.mainagent.domain.service.streaming;

import com.sashkomusic.mainagent.api.telegram.dto.BotResponse;
import com.sashkomusic.mainagent.domain.model.ReleaseMetadata;
import com.sashkomusic.mainagent.domain.service.search.SearchContextService;
import com.sashkomusic.mainagent.domain.util.SearchUrlUtils;
import com.sashkomusic.mainagent.infrastracture.client.songlink.SonglinkClient;
import com.sashkomusic.mainagent.infrastracture.client.songlink.SonglinkResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingFlowService {

    private static final String YOUTUBE_WATCH_URL_TEMPLATE = "https://www.youtube.com/watch?v=%s";

    private static final Map<String, String> PLATFORM_LABELS;

    static {
        PLATFORM_LABELS = new LinkedHashMap<>();
        PLATFORM_LABELS.put("spotify", "🟢");
        PLATFORM_LABELS.put("appleMusic", "🍏");
        PLATFORM_LABELS.put("soundcloud", "🧡");
        PLATFORM_LABELS.put("bandcamp", "📼");
        PLATFORM_LABELS.put("youtubeMusic", "🎵");
        PLATFORM_LABELS.put("youtube", "▶️");
    }

    private final SearchContextService searchContextService;
    private final SonglinkClient songlinkClient;

    public List<BotResponse> handleStreamingPlatforms(long chatId, String callbackData) {
        try {
            var platforms = handleStreamingCallback(chatId, callbackData);
            return List.of(BotResponse.withButtons("🤝 послухай туво", platforms));
        } catch (Exception e) {
            log.error("Error getting streaming platforms: {}", e.getMessage(), e);
            return List.of(BotResponse.text("Не вдалося знайти стрімінгові платформи 😔"));
        }
    }

    public Map<String, String> handleStreamingCallback(long chatId, String callbackData) {
        log.info("Handling streaming platforms request, callback={}", callbackData);

        String releaseId = callbackData.substring("STREAM:".length());

        if (releaseId.isEmpty()) {
            // No releaseId - get from search context
            return getPlatformLinksForSearch(chatId);
        } else {
            // Has releaseId - get from release metadata
            return getPlatformLinks(releaseId);
        }
    }

    public Map<String, String> getPlatformLinksForSearch(long chatId) {
        log.info("Getting platform links for search context, chatId={}", chatId);

        var searchRequest = searchContextService.getSearchRequest(chatId);
        String artist = searchRequest.artist();
        String title = searchRequest.getTitle();

        return buildPlatformSearchLinks(artist, title);
    }

    public Map<String, String> getPlatformLinks(String releaseId) {
        log.info("Getting platform links for releaseId={}", releaseId);

        ReleaseMetadata metadata = searchContextService.getReleaseMetadata(releaseId);
        if (metadata == null) {
            log.warn("No metadata found for releaseId={}", releaseId);
            return buildYouTubeFallback(null);
        }

        // Simple approach: generate search URLs for all platforms
        return buildSimplePlatformSearchLinks(metadata);

        // Complex Songlink-based approach (archived for future use)
        /*
        String searchQuery = buildSearchQuery(metadata);
        List<String> youtubeUrls = searchYouTubeWithDlp(searchQuery);

        if (youtubeUrls.isEmpty()) {
            log.warn("No YouTube URLs found, using fallback");
            return buildYouTubeFallback(metadata);
        }

        SonglinkResponse bestResponse = resolveBestSonglinkResponse(youtubeUrls);

        if (bestResponse == null || bestResponse.linksByPlatform() == null || bestResponse.linksByPlatform().isEmpty()) {
            log.warn("No Songlink response, using fallback");
            return buildYouTubeFallback(metadata);
        }

        Map<String, String> platforms = mapToPlatformButtons(bestResponse, metadata);
        searchContextService.cachePlatformLinks(releaseId, platforms);

        return platforms;
        */
    }

    private Map<String, String> buildSimplePlatformSearchLinks(ReleaseMetadata metadata) {
        return buildPlatformSearchLinks(metadata.artist(), metadata.title());
    }

    private Map<String, String> buildPlatformSearchLinks(String artist, String title) {
        Map<String, String> buttons = new LinkedHashMap<>();
        String query = artist + " " + title;

        String spotifyUrl = "https://open.spotify.com/search/" + SearchUrlUtils.encode(query);
        buttons.put("🟢", "URL:" + spotifyUrl);

        // Apple Music expects %20 for spaces (not +) and proper encoding for non-ASCII chars
        String appleQuery = (artist + " " + title).toLowerCase();
        String appleUrl = "https://music.apple.com/ua/search?l=uk&term=" + SearchUrlUtils.encode(appleQuery).replace("+", "%20");
        buttons.put("🍏", "URL:" + appleUrl);

        String soundcloudUrl = "https://soundcloud.com/search?q=" + SearchUrlUtils.encode(query);
        buttons.put("🧡", "URL:" + soundcloudUrl);

        String bandcampUrl = "https://bandcamp.com/search?q=" + SearchUrlUtils.encode(query);
        buttons.put("📼", "URL:" + bandcampUrl);

        String ytMusicUrl = "https://music.youtube.com/search?q=" + SearchUrlUtils.encode(query);
        buttons.put("🎵", "URL:" + ytMusicUrl);

        String youtubeUrl = buildYoutubeSearchUrl(artist, title);
        buttons.put("▶️", youtubeUrl);

        log.info("Generated {} platform search links", buttons.size());
        return buttons;
    }

    private String buildSearchQuery(ReleaseMetadata metadata) {
        String query = metadata.artist() + " album " + metadata.title();
        log.info("Built search query: {}", query);
        return query;
    }

    private SonglinkResponse resolveBestSonglinkResponse(List<String> youtubeUrls) {
        log.info("Trying {} YouTube URLs to find best Songlink response", youtubeUrls.size());

        SonglinkResponse bestResponse = null;
        int maxPlatforms = 0;

        for (String youtubeUrl : youtubeUrls) {
            SonglinkResponse response = songlinkClient.getLinks(youtubeUrl);

            if (response != null && response.linksByPlatform() != null) {
                int platformCount = response.linksByPlatform().size();
                log.info("URL {} has {} platforms", youtubeUrl, platformCount);

                if (platformCount > maxPlatforms) {
                    maxPlatforms = platformCount;
                    bestResponse = response;
                    log.info("New best response with {} platforms", platformCount);
                }
            }
        }

        return bestResponse;
    }

    private List<String> searchYouTubeWithDlp(String query) {
        try {
            log.info("Searching YouTube with yt-dlp for top 2 results: {}", query);

            ProcessBuilder pb = new ProcessBuilder(
                    "yt-dlp",
                    "ytsearch2:" + query,
                    "--print", "%(id)s|%(title)s",
                    "--quiet",
                    "--no-warnings"
            );

            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            List<String> urls = new java.util.ArrayList<>();

            // Extract artist and album from query for validation
            String[] queryParts = query.split(" album ");
            String artist = queryParts.length > 0 ? queryParts[0].toLowerCase() : "";
            String album = queryParts.length > 1 ? queryParts[1].toLowerCase() : "";

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("|")) {
                    String[] parts = line.split("\\|", 2);
                    if (parts.length == 2) {
                        String videoId = parts[0].trim();
                        String videoTitle = parts[1].trim();

                        // Validate: title should contain artist or album name
                        String titleLower = videoTitle.toLowerCase();
                        boolean isValid = titleLower.contains(artist) || titleLower.contains(album);

                        if (isValid) {
                            String url = String.format(YOUTUBE_WATCH_URL_TEMPLATE, videoId);
                            urls.add(url);
                            log.info("yt-dlp found valid video: {} - {}", videoId, videoTitle);
                        } else {
                            log.debug("Skipping video (title mismatch): {} - {}", videoId, videoTitle);
                        }
                    }
                }
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                log.error("yt-dlp failed with exit code: {}", exitCode);
                return List.of();
            }

            log.info("yt-dlp returned {} validated video URLs", urls.size());
            return urls;

        } catch (Exception e) {
            log.error("Error running yt-dlp: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private Map<String, String> mapToPlatformButtons(SonglinkResponse response, ReleaseMetadata metadata) {
        Map<String, String> buttons = new LinkedHashMap<>();

        // Map all platforms EXCEPT youtube from Songlink response
        for (Map.Entry<String, String> entry : PLATFORM_LABELS.entrySet()) {
            String platformKey = entry.getKey();
            String buttonLabel = entry.getValue();

            // Skip YouTube - we'll add it manually as search results
            if (platformKey.equals("youtube")) {
                continue;
            }

            if (response.linksByPlatform().containsKey(platformKey)) {
                String url = response.linksByPlatform().get(platformKey).url();
                if (url != null && !url.isEmpty()) {
                    buttons.put(buttonLabel, "URL:" + url);
                    log.debug("Added platform button: {} -> {}", buttonLabel, url);
                }
            }
        }

        // ALWAYS add YouTube as search results URL (not from Songlink)
        String youtubeSearchUrl = buildYoutubeSearchUrl(metadata.artist(), metadata.title());
        buttons.put("▶️", youtubeSearchUrl);

        log.info("Mapped {} platform buttons", buttons.size());
        return buttons;
    }

    private Map<String, String> buildYouTubeFallback(ReleaseMetadata metadata) {
        if (metadata == null) {
            return Map.of("▶️", "URL:https://youtube.com");
        }

        String youtubeUrl = buildYoutubeSearchUrl(metadata.artist(), metadata.title());
        return Map.of("▶️", youtubeUrl);
    }

    private String buildYoutubeSearchUrl(String artist, String title) {
        String albumWord = SearchUrlUtils.buildYoutubeAlbumWord(
                SearchUrlUtils.detectLanguage(artist, title)
        );
        String query = artist + " " + title + " " + albumWord;
        String url = "https://www.youtube.com/results?search_query=" + SearchUrlUtils.encode(query);
        return "URL:" + url;
    }
}
