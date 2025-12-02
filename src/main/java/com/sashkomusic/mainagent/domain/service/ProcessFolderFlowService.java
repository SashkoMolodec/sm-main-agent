package com.sashkomusic.mainagent.domain.service;

import com.sashkomusic.mainagent.ai.service.AiService;
import com.sashkomusic.mainagent.api.telegram.dto.BotResponse;
import com.sashkomusic.mainagent.domain.model.Language;
import com.sashkomusic.mainagent.domain.model.MetadataSearchRequest;
import com.sashkomusic.mainagent.domain.model.ReleaseMetadata;
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
    private final SearchContextHolder searchContextHolder;
    private final ProcessFolderContextHolder contextHolder;
    private final ProcessLibraryTaskProducer libraryTaskProducer;
    private final AiService aiService;

    @Value("${slskd.downloads.local-path:/Users/okravch/my/sm/sm-download-agent/slskd/downloads}")
    private String downloadsBasePath;

    private static final Set<String> AUDIO_EXTENSIONS = Set.of(
            "mp3", "flac", "m4a", "ogg", "wav", "opus", "aac"
    );

    public List<BotResponse> process(long chatId, String folderName) {
        try {
            Path folderPath = Paths.get(downloadsBasePath, folderName);
            if (!Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
                return List.of(BotResponse.text("❌ папка не знайдена: `" + folderName + "`"));
            }

            List<String> audioFiles = getAudioFiles(folderPath);
            if (audioFiles.isEmpty()) {
                return List.of(BotResponse.text("❌ В папці немає аудіо-файлів"));
            }

            log.info("Processing folder: {}, found {} audio files", folderName, audioFiles.size());

            ReleaseIdentifierService.ReleaseInfo releaseInfo = identifierService.identifyFromFolderName(folderName);
            if (releaseInfo == null) {
                return List.of(BotResponse.text(String.format("""
                        ❌ не вдалося розпізнати назву релізу з папки: `%s`
                        
                        спробуй тако:
                        • Artist - Album (Year)
                        • Artist - Album
                        • [Label] Artist - Album
                        """, folderName)));
            }

            List<BotResponse> responses = new ArrayList<>();
            responses.add(BotResponse.text(String.format("📄 %d файлів, знайдено метадані:", audioFiles.size())));

            MetadataSearchRequest searchRequest = MetadataSearchRequest.create(
                    releaseInfo.artist(), releaseInfo.album(), null, null, null, null, null, null, null, null, null, Language.EN);

            List<ReleaseMetadata> mbResults = searchSource(MUSICBRAINZ.name(), () ->
                    musicBrainzClient.searchReleases(searchRequest), 3);

            List<ReleaseMetadata> discogsResults = searchSource(DISCOGS.name(), () ->
                    discogsClient.searchReleases(searchRequest), 4);

            List<ReleaseMetadata> bandcampResults = searchSource(BANDCAMP.name(), () ->
                    bandcampClient.searchReleases(searchRequest), 3);

            List<ReleaseMetadata> allResults = new ArrayList<>();
            allResults.addAll(mbResults);
            allResults.addAll(discogsResults);
            allResults.addAll(bandcampResults);

            if (allResults.isEmpty()) {
                responses.add(BotResponse.text("❌ нема шось метаданих"));
                return responses;
            }

            storeSearchResults(chatId, allResults);
            storeChatContext(chatId, folderPath, audioFiles);

            responses.add(buildOptionsMessage(mbResults, discogsResults, bandcampResults));
            return responses;

        } catch (Exception e) {
            log.error("Error processing folder: {}", e.getMessage(), e);
            return List.of(BotResponse.text("❌ помилка обробки папки: " + e.getMessage()));
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

        ReleaseMetadata metadata = searchContextHolder.getReleaseMetadata(releaseId);
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
            searchContextHolder.saveReleaseMetadata(result);
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

        message.append("**обери варіант**");
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
}
