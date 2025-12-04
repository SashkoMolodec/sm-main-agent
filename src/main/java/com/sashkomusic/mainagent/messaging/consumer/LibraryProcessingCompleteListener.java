package com.sashkomusic.mainagent.messaging.consumer;

import com.sashkomusic.mainagent.api.telegram.TelegramChatBot;
import com.sashkomusic.mainagent.messaging.consumer.dto.LibraryProcessingCompleteDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Comparator;

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
        if (result.success()) {
            return String.format("""
                    ✅ **додано в лібку!**
                    
                    📁 `%s`
                    %s
                    """,
                    extractFolderName(result.directoryPath()),
                    formatProcessedFiles(result.processedFiles())
            );
        } else {
            return String.format("""
                    ❌ **помилка обробки релізу**
                    📁 `%s`
                    %s
                    %s
                    """,
                    extractFolderName(result.directoryPath()),
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
        files.sort(Comparator.comparing(LibraryProcessingCompleteDto.ProcessedFileDto::trackNumber));
        files.forEach(f -> sb.append(String.format("_%02d. %s\n_",
                        f.trackNumber(),
                        f.trackTitle())));
        return sb.toString().toLowerCase();
    }

    private String extractFolderName(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        int lastSlash = Math.max(path.lastIndexOf('\\'), path.lastIndexOf('/'));
        if (lastSlash >= 0) {
            return path.substring(lastSlash + 1);
        }
        return path;
    }
}
