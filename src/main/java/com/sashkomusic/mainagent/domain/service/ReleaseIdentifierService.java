package com.sashkomusic.mainagent.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashkomusic.mainagent.ai.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReleaseIdentifierService {

    private final AiService aiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Pattern[] FOLDER_PATTERNS = {
            // Artist - Album (Year)
            Pattern.compile("^(?<artist>.+?)\\s*-\\s*(?<album>.+?)\\s*\\((?<year>\\d{4})\\).*$"),
            // Artist - Album [Label]
            Pattern.compile("^(?<artist>.+?)\\s*-\\s*(?<album>.+?)\\s*\\[.+?\\].*$"),
            // [Label] Artist - Album
            Pattern.compile("^\\[.+?\\]\\s*(?<artist>.+?)\\s*-\\s*(?<album>.+?)$"),
            // Year Artist - Album
            Pattern.compile("^(?<year>\\d{4})\\s+(?<artist>.+?)\\s*-\\s*(?<album>.+?)$"),
            // Artist - Album
            Pattern.compile("^(?<artist>.+?)\\s*-\\s*(?<album>.+?)$")
    };

    public record ReleaseInfo(String artist, String album, String year) {
    }

    public ReleaseInfo identifyFromFolderName(String folderName) {
        if (folderName == null || folderName.isEmpty()) {
            log.warn("Empty folder name provided");
            return null;
        }

        log.debug("Attempting to parse folder name: {}", folderName);

        // AI first
        ReleaseInfo aiResult = parseWithAi(folderName);
        if (aiResult != null) {
            log.info("AI parsed folder name: artist='{}', album='{}', year='{}'",
                    aiResult.artist(), aiResult.album(), aiResult.year());
            return aiResult;
        }

        // Fallback to regex
        log.debug("AI parsing failed, trying regex patterns");
        return parseWithRegex(folderName);
    }

    private ReleaseInfo parseWithAi(String folderName) {
        try {
            String jsonResponse = aiService.parseFolderName(folderName);
            String cleanJson = removeMarkdown(jsonResponse);

            JsonNode node = objectMapper.readTree(cleanJson);

            String artist = node.has("artist") && !node.get("artist").isNull()
                    ? node.get("artist").asText() : null;
            String album = node.has("album") && !node.get("album").isNull()
                    ? node.get("album").asText() : null;
            String year = node.has("year") && !node.get("year").isNull()
                    ? node.get("year").asText() : null;

            // Both artist and album are required
            if (artist == null || artist.isEmpty() || album == null || album.isEmpty()) {
                log.warn("AI returned incomplete data: artist={}, album={}", artist, album);
                return null;
            }

            return new ReleaseInfo(artist, album, year);
        } catch (Exception e) {
            log.warn("AI parsing failed: {}", e.getMessage());
            return null;
        }
    }

    @NotNull
    private static String removeMarkdown(String jsonResponse) {
        String cleanJson = jsonResponse.trim();
        if (cleanJson.startsWith("```")) {
            cleanJson = cleanJson.replaceFirst("^```(?:json)?\\s*", "");
            cleanJson = cleanJson.replaceFirst("```\\s*$", "");
            cleanJson = cleanJson.trim();
        }
        return cleanJson;
    }

    private ReleaseInfo parseWithRegex(String folderName) {
        for (Pattern pattern : FOLDER_PATTERNS) {
            Matcher matcher = pattern.matcher(folderName.trim());
            if (matcher.matches()) {
                String artist = matcher.group("artist").trim();
                String album = matcher.group("album").trim();
                String year = null;

                try {
                    year = matcher.group("year");
                } catch (IllegalArgumentException e) {
                    // Year group doesn't exist in this pattern
                }

                log.info("Regex parsed folder name: artist='{}', album='{}', year='{}'", artist, album, year);
                return new ReleaseInfo(artist, album, year);
            }
        }

        log.warn("Could not parse folder name: {}", folderName);
        return null;
    }

    public ReleaseInfo identifyFromAudioFile(String audioFilePath) {
        try {
            log.debug("Attempting to read tags from audio file: {}", audioFilePath);

            AudioFile audioFile = AudioFileIO.read(new File(audioFilePath));
            Tag tag = audioFile.getTag();

            if (tag == null) {
                log.debug("No tags found in audio file");
                return null;
            }

            String artist = tag.getFirst(FieldKey.ARTIST);
            String album = tag.getFirst(FieldKey.ALBUM);
            String year = tag.getFirst(FieldKey.YEAR);

            if (artist == null || artist.trim().isEmpty() || album == null || album.trim().isEmpty()) {
                log.debug("Incomplete tags in audio file: artist='{}', album='{}'", artist, album);
                return null;
            }

            log.info("Read tags from audio file: artist='{}', album='{}', year='{}'",
                    artist.trim(), album.trim(), year);

            return new ReleaseInfo(
                    artist.trim(),
                    album.trim(),
                    year != null && !year.trim().isEmpty() ? year.trim() : null
            );

        } catch (Exception e) {
            log.debug("Could not read tags from audio file: {}", e.getMessage());
            return null;
        }
    }
}