package com.sashkomusic.mainagent.messaging.consumer;

import com.sashkomusic.mainagent.api.telegram.TelegramChatBot;
import com.sashkomusic.mainagent.messaging.consumer.dto.ReprocessReleaseResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReprocessReleaseResultListener {

    private final TelegramChatBot chatBot;

    @KafkaListener(topics = "reprocess-release-complete", groupId = "main-agent-group")
    public void handleReprocessResult(ReprocessReleaseResultDto result) {
        log.info("Received reprocess result: chatId={}, success={}, filesProcessed={}",
                result.chatId(), result.success(), result.filesProcessed());

        String message = buildResultMessage(result);
        chatBot.sendMessage(result.chatId(), message);
    }

    private String buildResultMessage(ReprocessReleaseResultDto result) {
        String[] artistAndRelease = extractArtistAndRelease(result.directoryPath());
        String artist = artistAndRelease[0];
        String releaseFolder = artistAndRelease[1];

        if (result.success()) {
            return String.format("""
                    ✅ **репроцеснуто!**
                    📁 _%s_ → _%s_
                    🎵 %d файлів оновлено
                    """,
                    artist,
                    releaseFolder,
                    result.filesProcessed()
            ).trim();
        } else {
            return String.format("""
                    ❌ **помилка репроцесингу!(**
                    📁 _%s_ → _%s_
                    %s
                    """,
                    artist,
                    releaseFolder,
                    result.message()
            ).trim();
        }
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
