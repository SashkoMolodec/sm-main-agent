package com.sashkomusic.mainagent.domain.service.process;

import com.sashkomusic.mainagent.ai.service.AiService;
import com.sashkomusic.mainagent.api.telegram.dto.BotResponse;
import com.sashkomusic.mainagent.domain.model.DateRange;
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
    private final AiService aiService;

    @Value("${slskd.downloads.local-path:/Users/okravch/my/sm/sm-download-agent/slskd/downloads}")
    private String downloadsBasePath;

    private static final Set<String> AUDIO_EXTENSIONS = Set.of(
            "mp3", "flac", "m4a", "ogg", "wav", "opus", "aac"
    );

    public List<BotResponse> handleProcessCommand(long chatId, String rawInput) {
        String folderName = extractFolderName(rawInput);
        return process(chatId, folderName);
    }

    public List<BotResponse> process(long chatId, String input) {
        try {
            FolderParseResult parseResult = parseFolderAndFilters(input);

            List<BotResponse> validationError = validateFolder(parseResult.folderPath(), parseResult.folderName());
            if (validationError != null) return validationError;

            List<String> audioFiles = getAudioFiles(parseResult.folderPath());
            if (audioFiles.isEmpty()) {
                return List.of(BotResponse.text("❌ В папці немає аудіо-файлів"));
            }

            log.info("Processing folder: {}, filters: {}, found {} audio files",
                    parseResult.folderName(), parseResult.filtersText(), audioFiles.size());

            MetadataSearchRequest searchRequest = buildSearchRequest(
                    parseResult.folderName(), parseResult.filtersText(), audioFiles);

            List<BotResponse> requestError = validateSearchRequest(searchRequest, parseResult.folderName());
            if (requestError != null) return requestError;

            SearchResults searchResults = searchAllSources(searchRequest);

            if (searchResults.isEmpty()) {
                return List.of(
                    BotResponse.text(String.format("📄 %d файлів, знайдено метадані:", audioFiles.size())),
                    BotResponse.text("❌ нема шось метаданих")
                );
            }

            saveSearchContext(chatId, parseResult.folderName(), searchRequest,
                    searchResults, parseResult.folderPath(), audioFiles);

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

    private FolderParseResult parseFolderAndFilters(String input) {
        String folderName = input.trim();
        String filtersText = null;
        Path folderPath = Paths.get(downloadsBasePath, folderName);

        if (!Files.exists(folderPath) && folderName.contains(" ")) {
            String[] words = folderName.split("\\s+");
            for (int i = words.length - 1; i > 0; i--) {
                String candidateFolder = String.join(" ", java.util.Arrays.copyOfRange(words, 0, i));
                Path candidatePath = Paths.get(downloadsBasePath, candidateFolder);
                if (Files.exists(candidatePath) && Files.isDirectory(candidatePath)) {
                    folderName = candidateFolder;
                    filtersText = String.join(" ", java.util.Arrays.copyOfRange(words, i, words.length));
                    folderPath = candidatePath;
                    break;
                }
            }
        }

        return new FolderParseResult(folderName, filtersText, folderPath);
    }

    private List<BotResponse> validateFolder(Path folderPath, String folderName) {
        if (!Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
            return List.of(BotResponse.text("❌ папка не знайдена: `" + folderName + "`"));
        }
        return null;
    }

    private MetadataSearchRequest buildSearchRequest(String folderName, String filtersText,
                                                      List<String> audioFiles) {
        MetadataSearchRequest baseRequest = getBaseSearchRequest(folderName, audioFiles);
        return mergeFilters(baseRequest, filtersText);
    }

    private MetadataSearchRequest getBaseSearchRequest(String folderName, List<String> audioFiles) {
        var releaseInfoFromTags = identifierService.identifyFromAudioFile(audioFiles.getFirst());

        if (releaseInfoFromTags != null) {
            log.info("Using release info from audio file tags");
            DateRange dateRange = parseDateRange(releaseInfoFromTags.year());
            return MetadataSearchRequest.create(
                    releaseInfoFromTags.artist(), releaseInfoFromTags.album(),
                    null, dateRange, null, null, null, null, null, null, null, Language.EN);
        }

        log.info("No tags in audio file, parsing folder name");
        return identifierService.identifyFromFolderName(folderName);
    }

    private DateRange parseDateRange(String year) {
        if (year != null && !year.isEmpty()) {
            try {
                int yearInt = Integer.parseInt(year);
                return new DateRange(yearInt, yearInt);
            } catch (NumberFormatException e) {
                log.warn("Could not parse year from tags: {}", year);
            }
        }
        return null;
    }

    private MetadataSearchRequest mergeFilters(MetadataSearchRequest baseRequest, String filtersText) {
        if (filtersText == null || filtersText.isEmpty() || baseRequest == null) {
            return baseRequest;
        }

        log.info("Parsing additional filters: {}", filtersText);
        MetadataSearchRequest additionalFilters = aiService.parseAdditionalFilters(filtersText);

        return MetadataSearchRequest.create(
                baseRequest.artist(),
                baseRequest.release(),
                baseRequest.recording(),
                additionalFilters.dateRange() != null && !additionalFilters.dateRange().isEmpty()
                        ? additionalFilters.dateRange() : baseRequest.dateRange(),
                !additionalFilters.format().isEmpty() ? additionalFilters.format() : baseRequest.format(),
                !additionalFilters.type().isEmpty() ? additionalFilters.type() : baseRequest.type(),
                !additionalFilters.country().isEmpty() ? additionalFilters.country() : baseRequest.country(),
                !additionalFilters.status().isEmpty() ? additionalFilters.status() : baseRequest.status(),
                !additionalFilters.style().isEmpty() ? additionalFilters.style() : baseRequest.style(),
                !additionalFilters.label().isEmpty() ? additionalFilters.label() : baseRequest.label(),
                !additionalFilters.catno().isEmpty() ? additionalFilters.catno() : baseRequest.catno(),
                Language.EN
        );
    }

    private List<BotResponse> validateSearchRequest(MetadataSearchRequest searchRequest, String folderName) {
        if (searchRequest == null || searchRequest.artist().isEmpty() || searchRequest.release().isEmpty()) {
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
        List<ReleaseMetadata> mbResults = searchSource(MUSICBRAINZ.name(),
                () -> musicBrainzClient.searchReleases(searchRequest), 4);
        List<ReleaseMetadata> discogsResults = searchSource(DISCOGS.name(),
                () -> discogsClient.searchReleases(searchRequest), 4);
        List<ReleaseMetadata> bandcampResults = searchSource(BANDCAMP.name(),
                () -> bandcampClient.searchReleases(searchRequest), 3);

        return new SearchResults(mbResults, discogsResults, bandcampResults);
    }

    private void saveSearchContext(long chatId, String folderName, MetadataSearchRequest searchRequest,
                                   SearchResults searchResults, Path folderPath, List<String> audioFiles) {
        List<ReleaseMetadata> allResults = searchResults.allResults();

        SearchEngine primaryEngine = !searchResults.mbResults().isEmpty() ? MUSICBRAINZ :
                (!searchResults.discogsResults().isEmpty() ? DISCOGS : BANDCAMP);

        searchContextService.saveSearchContext(chatId, primaryEngine, folderName, searchRequest, allResults);
        storeSearchResults(chatId, allResults);
        storeChatContext(chatId, folderPath, audioFiles);
    }

    private record FolderParseResult(String folderName, String filtersText, Path folderPath) {}

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
        Integer optionNumber = aiService.parseOptionNumber(rawInput);
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

    private List<ReleaseMetadata> searchSource(String sourceName, SourceSearcher searcher, int limit) {
        try {
            List<ReleaseMetadata> results = searcher.search();
            log.info("{}: found {} results", sourceName, results.size());
            return results.stream().limit(limit).toList();
        } catch (Exception e) {
            log.warn("{}: search failed: {}", sourceName, e.getMessage());
            return List.of();
        }
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
        try (Stream<Path> files = Files.list(folderPath)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(this::isAudioFile)
                    .map(Path::toString)
                    .toList();
        }
    }

    private boolean isAudioFile(Path file) {
        String filename = file.getFileName().toString().toLowerCase();
        return AUDIO_EXTENSIONS.stream().anyMatch(ext -> filename.endsWith("." + ext));
    }

    private String extractFolderName(String rawInput) {
        return rawInput.substring("/process ".length()).trim();
    }
}
