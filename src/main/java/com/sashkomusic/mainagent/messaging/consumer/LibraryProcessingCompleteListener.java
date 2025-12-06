package com.sashkomusic.mainagent.messaging.consumer;

import com.sashkomusic.mainagent.api.telegram.TelegramChatBot;
import com.sashkomusic.mainagent.messaging.consumer.dto.LibraryProcessingCompleteDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class LibraryProcessingCompleteListener {

    private final TelegramChatBot chatBot;

    @KafkaListener(topics = "library-processing-complete", groupId = "main-agent-group")
    public void handleLibraryProcessingComplete(LibraryProcessingCompleteDto result) {
        log.info("Received library processing result: chatId={}, success={}, processedFiles={}",
                result.chatId(), result.success(), result.processedFiles().size());

        String message = buildResultMessage(result);
        chatBot.sendMessage(result.chatId(), message);
    }

    private String buildResultMessage(LibraryProcessingCompleteDto result) {
        String[] artistAndRelease = extractArtistAndRelease(result.directoryPath());
        String artist = artistAndRelease[0];
        String releaseFolder = artistAndRelease[1];

        if (result.success()) {
            String tracks = formatProcessedFiles(result.processedFiles());
            return "✅ **додано в лібку!**\n\n📁 _%s_ → _%s_\n%s".formatted(artist, releaseFolder, tracks);
        } else {
            return String.format("""
                    ❌ **помилка обробки релізу**
                    📁 _%s_ → _%s_
                    %s
                    %s
                    """,
                    artist,
                    releaseFolder,
                    result.message(),
                    result.errors().isEmpty() ? "" : "**помилки:**\n" + String.join("\n", result.errors())
            );
        }
    }

    private String formatProcessedFiles(java.util.List<LibraryProcessingCompleteDto.ProcessedFileDto> files) {
        if (files.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        files.stream()
                .sorted(java.util.Comparator.comparing(LibraryProcessingCompleteDto.ProcessedFileDto::trackNumber))
                .forEach(f -> sb.append("_%02d. %s_\n".formatted(f.trackNumber(), f.trackTitle().toLowerCase())));
        return sb.toString();
    }

    private String[] extractArtistAndRelease(String path) {
        if (path == null || path.isEmpty()) {
            return new String[]{"unknown", ""};
        }

        String cleanPath = path.endsWith("/") || path.endsWith("\\")
                ? path.substring(0, path.length() - 1)
                : path;

        int lastSlash = Math.max(cleanPath.lastIndexOf('\\'), cleanPath.lastIndexOf('/'));
        if (lastSlash < 0) {
            return new String[]{"unknown", cleanPath};
        }

        String releaseFolder = cleanPath.substring(lastSlash + 1);

        String parentPath = cleanPath.substring(0, lastSlash);
        int secondLastSlash = Math.max(parentPath.lastIndexOf('\\'), parentPath.lastIndexOf('/'));
        String artistFolder = secondLastSlash >= 0
                ? parentPath.substring(secondLastSlash + 1)
                : parentPath;

        return new String[]{artistFolder, releaseFolder};
    }
}
