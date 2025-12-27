package com.sashkomusic.mainagent.domain.service.process;

import com.sashkomusic.mainagent.api.telegram.dto.BotResponse;
import com.sashkomusic.mainagent.domain.model.Language;
import com.sashkomusic.mainagent.domain.model.MetadataSearchRequest;
import com.sashkomusic.mainagent.domain.model.ReleaseMetadata;
import com.sashkomusic.mainagent.domain.model.SearchEngine;
import com.sashkomusic.mainagent.domain.service.search.SearchContextService;
import com.sashkomusic.mainagent.infrastracture.client.bandcamp.BandcampClient;
import com.sashkomusic.mainagent.infrastracture.client.discogs.DiscogsClient;
import com.sashkomusic.mainagent.infrastracture.client.musicbrainz.MusicBrainzClient;
import com.sashkomusic.mainagent.messaging.producer.ProcessLibraryTaskProducer;
import com.sashkomusic.mainagent.messaging.producer.dto.ProcessLibraryTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.sashkomusic.mainagent.domain.model.SearchEngine.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProcessFolderFlowService {

    private final MusicBrainzClient musicBrainzClient;
    private final DiscogsClient discogsClient;
    private final BandcampClient bandcampClient;
    private final ReleaseIdentifierService identifierService;
    private final SearchContextService searchContextService;
    private final ProcessFolderContextHolder contextHolder;
    private final ProcessLibraryTaskProducer libraryTaskProducer;
    private final PathMappingService pathMappingService;
    private final com.sashkomusic.mainagent.domain.service.download.DownloadContextHolder downloadContextHolder;

    @Value("${downloads.base-path:/Users/okravch/my/sm/sm-download-agent/downloads}")
    private String downloadsBasePath;

    private static final Set<String> AUDIO_EXTENSIONS = Set.of(
            "mp3", "flac", "m4a", "ogg", "wav", "opus", "aac"
    );

    public List<BotResponse> handleProcessCommand(long chatId, String rawInput) {
        String folderName = extractFolderName(rawInput);
        return process(chatId, folderName);
    }

    public List<BotResponse> process(long chatId, String folderName) {
        return process(chatId, folderName, "");
    }

    public List<BotResponse> process(long chatId, String folderName, String additionalContext) {
        try {
            String processPath = pathMappingService.mapProcessPath(folderName);
            FolderResolveResult folder = resolveFolder(processPath);

            List<BotResponse> validationError = validateFolder(folder.path(), folder.name());
            if (validationError != null) return validationError;

            List<String> audioFiles = getAudioFiles(folder.path());
            if (audioFiles.isEmpty()) {
                return List.of(BotResponse.text("❌ В папці немає аудіо-файлів"));
            }

            log.info("Processing folder: {}, found {} audio files", folder.name(), audioFiles.size());

            MetadataSearchRequest searchRequest = buildSearchRequest(folder.name(), audioFiles, additionalContext);

            List<BotResponse> requestError = validateSearchRequest(searchRequest, folder.name());
            if (requestError != null) return requestError;

            SearchResults searchResults = searchAllSources(searchRequest);

            if (searchResults.isEmpty()) {
                return List.of(
                        BotResponse.text(String.format("📄 %d файлів, знайдено метадані:", audioFiles.size())),
                        BotResponse.text("❌ нема шось метаданих")
                );
            }

            saveSearchContext(chatId, folder.name(), searchRequest, searchResults, folder.path(), audioFiles);

            return List.of(
                    BotResponse.text(String.format("📄 %d файлів, знайдено метадані:", audioFiles.size())),
                    buildOptionsMessage(searchResults.mbResults(), searchResults.discogsResults(),
                            searchResults.bandcampResults())
            );

        } catch (Exception e) {
            log.error("Error processing folder: {}", e.getMessage(), e);
            return List.of(BotResponse.text("❌ помилка обробки папки: " + e.getMessage()));
        }
    }

    private FolderResolveResult resolveFolder(String input) {
        String folderName = input.trim();

        Path inputPath = Paths.get(folderName);
        Path folderPath = inputPath.isAbsolute()
                ? inputPath
                : Paths.get(downloadsBasePath, folderName);

        String cleanedFolderName = cleanFolderName(folderPath.getFileName().toString());
        return new FolderResolveResult(cleanedFolderName, folderPath);
    }

    private List<BotResponse> validateFolder(Path folderPath, String folderName) {
        if (!Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
            return List.of(BotResponse.text("❌ папка не знайдена: `" + folderName + "`"));
        }
        return null;
    }

    private MetadataSearchRequest buildSearchRequest(String folderName, List<String> audioFiles, String additionalContext) {
        var releaseInfoFromTags = identifierService.identifyFromAudioFile(audioFiles.getFirst());

        if (releaseInfoFromTags != null) {
            log.info("Using release info from audio file tags");
            return MetadataSearchRequest.create(
                    null, withAdditionalContext(releaseInfoFromTags.album(), additionalContext),
                    null, null, null, null, null, null, null, null, null, Language.EN);
        }

        log.info("No tags in audio file, parsing folder name");
        return identifierService.identifyFromFolderName(withAdditionalContext(folderName, additionalContext));
    }

    private static String withAdditionalContext(String filterValue, String additionalContext) {
        return filterValue + " " + additionalContext;
    }

    private List<BotResponse> validateSearchRequest(MetadataSearchRequest searchRequest, String folderName) {
        if (searchRequest == null || searchRequest.release().isEmpty()) {
            return List.of(BotResponse.text(String.format("""
                    ❌ не вдалося розпізнати назву релізу з папки: `%s`
                    
                    допиши контексту:
                    • /process Artist - Album
                    • /process Artist - Album касета 1990 україна
                    """, folderName)));
        }
        return null;
    }

    private SearchResults searchAllSources(MetadataSearchRequest searchRequest) {
        String title = searchRequest.getTitle();

        return new SearchResults(
                searchSource(() -> musicBrainzClient.searchReleases(searchRequest), title, 4),
                searchSource(() -> discogsClient.searchReleases(searchRequest), title, 4),
                searchSource(() -> bandcampClient.searchReleases(searchRequest), title, 3)
        );
    }

    private void saveSearchContext(long chatId, String folderName, MetadataSearchRequest searchRequest,
                                   SearchResults searchResults, Path folderPath, List<String> audioFiles) {
        List<ReleaseMetadata> allResults = searchResults.allResults();

        SearchEngine primarySource = !searchResults.mbResults().isEmpty() ? MUSICBRAINZ :
                (!searchResults.discogsResults().isEmpty() ? DISCOGS : BANDCAMP);

        searchContextService.saveSearchContext(chatId, primarySource, folderName, searchRequest, allResults);
        storeSearchResults(chatId, allResults);
        storeChatContext(chatId, folderPath, audioFiles);
    }

    private record FolderResolveResult(String name, Path path) {
    }

    private record SearchResults(List<ReleaseMetadata> mbResults,
                                 List<ReleaseMetadata> discogsResults,
                                 List<ReleaseMetadata> bandcampResults) {
        List<ReleaseMetadata> allResults() {
            List<ReleaseMetadata> all = new ArrayList<>();
            all.addAll(mbResults);
            all.addAll(discogsResults);
            all.addAll(bandcampResults);
            return all;
        }

        boolean isEmpty() {
            return allResults().isEmpty();
        }
    }

    public List<BotResponse> handleMetadataSelection(long chatId, String rawInput) {
        String trimmedInput = rawInput.trim();

        if (additionalContextIncluded(trimmedInput)) {
            String additionalContext = trimmedInput.substring(1).trim();

            String contextKey = contextHolder.getChatContextKey(chatId);
            if (contextKey == null) {
                return List.of(BotResponse.text("❌ сесія закінчилась. спробуй /process ще раз"));
            }
            ProcessFolderContextHolder.ProcessFolderContext folderContext = contextHolder.get(contextKey);
            if (folderContext == null) {
                return List.of(BotResponse.text("❌ контекст втрачено. спробуй /process ще раз"));
            }

            downloadContextHolder.clearSession(chatId);
            return process(chatId, folderContext.directoryPath(), additionalContext);
        }

        Integer optionNumber = parseNumberFromInput(trimmedInput);
        if (optionNumber == null) {
            return List.of(BotResponse.text("❌ невірний номер. спробуй ще раз"));
        }

        String contextKey = contextHolder.getChatContextKey(chatId);
        if (contextKey == null) {
            return List.of(BotResponse.text("❌ сесія закінчилась. спробуй /process ще раз"));
        }

        String releaseId = contextHolder.getReleaseIdByOption(chatId, optionNumber);
        if (releaseId == null) {
            return List.of(BotResponse.text("❌ невірний номер. спробуй ще раз"));
        }

        ProcessFolderContextHolder.ProcessFolderContext folderContext = contextHolder.get(contextKey);

        if (folderContext == null) {
            return List.of(BotResponse.text("❌ контекст втрачено. спробуй /process ще раз"));
        }

        ReleaseMetadata metadata = searchContextService.getMetadataWithTracks(releaseId, chatId);
        if (metadata == null) {
            return List.of(BotResponse.text("❌ метадані не знайдено"));
        }

        ProcessLibraryTaskDto libraryTask = ProcessLibraryTaskDto.of(
                chatId,
                folderContext.directoryPath(),
                folderContext.audioFiles(),
                metadata
        );

        libraryTaskProducer.send(libraryTask);

        contextHolder.remove(contextKey);
        contextHolder.clearChatSelection(chatId);

        log.info("Sent library processing task: chatId={}, directory={}",
                chatId, folderContext.directoryPath());

        return List.of(BotResponse.text("🚀 опрацьовую..."));
    }

    private static boolean additionalContextIncluded(String trimmedInput) {
        return trimmedInput.startsWith("+");
    }

    private Integer parseNumberFromInput(String input) {
        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean hasActiveContext(long chatId) {
        return contextHolder.getChatContextKey(chatId) != null;
    }

    private void storeChatContext(long chatId, Path folderPath, List<String> audioFiles) {
        String contextKey = contextHolder.generateShortKey();
        contextHolder.store(contextKey, folderPath.toString(), audioFiles);
        contextHolder.storeChatContext(chatId, contextKey);
    }

    private void storeSearchResults(long chatId, List<ReleaseMetadata> allResults) {
        List<String> allReleaseIds = new ArrayList<>();
        for (ReleaseMetadata result : allResults) {
            searchContextService.saveReleaseMetadata(result);
            allReleaseIds.add(result.id());
        }
        contextHolder.storeReleaseIds(chatId, allReleaseIds);
    }

    private List<ReleaseMetadata> searchSource(SourceSearcher searcher, String title, int limit) {
        try {
            List<ReleaseMetadata> results = searcher.search();
            log.info("Found {} results", results.size());

            List<ReleaseMetadata> filtered = filterByTitle(results, title);
            if (filtered.size() != results.size()) {
                log.info("{} results after filtering by title", filtered.size());
            }

            return filtered.stream().limit(limit).toList();
        } catch (Exception e) {
            log.error("Search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<ReleaseMetadata> filterByTitle(List<ReleaseMetadata> results, String originalTitle) {
        if (originalTitle == null || originalTitle.isBlank()) {
            return results;
        }

        String normalizedOriginal = originalTitle.toLowerCase().trim();
        Set<String> originalWords = new HashSet<>(java.util.Arrays.asList(normalizedOriginal.split("\\s+")));

        return results.stream()
                .filter(release -> {
                    String releaseTitle = release.title().toLowerCase();
                    if (releaseTitle.contains(normalizedOriginal) || normalizedOriginal.contains(releaseTitle)) {
                        return true;
                    }
                    return Stream.of(releaseTitle.split("\\s+"))
                            .anyMatch(originalWords::contains);
                })
                .toList();
    }

    @FunctionalInterface
    private interface SourceSearcher {
        List<ReleaseMetadata> search();
    }

    @NotNull
    private BotResponse buildOptionsMessage(List<ReleaseMetadata> mbResults,
                                            List<ReleaseMetadata> discogsResults,
                                            List<ReleaseMetadata> bandcampResults) {
        StringBuilder message = new StringBuilder();
        int optionIndex = 1;

        if (!mbResults.isEmpty()) {
            message.append("🎵 _musicbrainz:_\n");
            optionIndex = appendResults(message, mbResults, optionIndex);
            message.append("\n");
        }

        if (!discogsResults.isEmpty()) {
            message.append("💿 _discogs:_\n");
            optionIndex = appendResults(message, discogsResults, optionIndex);
            message.append("\n");
        }

        if (!bandcampResults.isEmpty()) {
            message.append("📼 _bandcamp:_\n");
            appendResults(message, bandcampResults, optionIndex);
            message.append("\n");
        }
        return BotResponse.text(message.toString());
    }

    private int appendResults(StringBuilder message, List<ReleaseMetadata> results, int startIndex) {
        int index = startIndex;
        for (ReleaseMetadata result : results) {
            String yearsDisplay = result.getYearsDisplay();
            String trackCountDisplay = result.getTrackCountDisplay();

            message.append(toEmojiNumber(index))
                    .append(" **")
                    .append(result.artist().toLowerCase())
                    .append(" - ")
                    .append(result.title().toLowerCase())
                    .append("**");

            if (!yearsDisplay.isEmpty()) {
                message.append(" • ").append(yearsDisplay);
            }

            if (!trackCountDisplay.isEmpty()) {
                message.append(" • ").append(trackCountDisplay).append(" тр.");
            }

            if (result.tags() != null && !result.tags().isEmpty()) {
                String tagsDisplay = result.getTagsDisplay().toLowerCase();
                message.append(" • ").append(tagsDisplay);
            }

            message.append("\n");

            index++;
        }

        return index;
    }

    private String toEmojiNumber(int number) {
        return switch (number) {
            case 1 -> "1️⃣";
            case 2 -> "2️⃣";
            case 3 -> "3️⃣";
            case 4 -> "4️⃣";
            case 5 -> "5️⃣";
            case 6 -> "6️⃣";
            case 7 -> "7️⃣";
            case 8 -> "8️⃣";
            case 9 -> "9️⃣";
            case 10 -> "🔟";
            default -> number + ".";
        };
    }

    private List<String> getAudioFiles(Path folderPath) throws IOException {
        try (Stream<Path> files = Files.walk(folderPath)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(this::isAudioFile)
                    .filter(this::isNotHiddenMacFile)
                    .map(Path::toString)
                    .toList();
        }
    }

    private boolean isAudioFile(Path file) {
        String filename = file.getFileName().toString().toLowerCase();
        return AUDIO_EXTENSIONS.stream().anyMatch(ext -> filename.endsWith("." + ext));
    }

    private boolean isNotHiddenMacFile(Path file) {
        String filename = file.getFileName().toString();
        return !filename.startsWith("._");
    }

    private String extractFolderName(String rawInput) {
        String folderName = rawInput.substring("/process ".length()).trim();
        return stripQuotes(folderName);
    }

    private static String stripQuotes(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }

        String trimmed = path.trim();

        if (trimmed.startsWith("'") && trimmed.endsWith("'") && trimmed.length() > 1) {
            return trimmed.substring(1, trimmed.length() - 1);
        }

        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() > 1) {
            return trimmed.substring(1, trimmed.length() - 1);
        }

        return trimmed;
    }

    private static String cleanFolderName(String folderName) {
        return folderName
                .replaceAll("\\[.*?\\]", "")
                .replaceAll("[()]", "")
                .replaceAll("[^\\p{L}\\p{N}\\s]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
