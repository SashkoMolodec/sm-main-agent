package com.sashkomusic.mainagent.domain.service.download;

import com.sashkomusic.mainagent.api.telegram.dto.BotResponse;
import com.sashkomusic.mainagent.domain.model.ReleaseMetadata;
import com.sashkomusic.mainagent.domain.service.SearchContextHolder;
import com.sashkomusic.mainagent.messaging.dto.SearchFilesTaskDto;
import com.sashkomusic.mainagent.messaging.producer.SearchFilesTaskProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MusicDownloadFlowService {

    private final SearchFilesTaskProducer searchFilesProducer;
    private final SearchContextHolder contextService;

    public BotResponse handleCallback(long chatId, String data) {
        if (data.startsWith("DL:")) {
            String releaseId = data.substring(3);
            log.info("User selected release ID: {}", releaseId);

            ReleaseMetadata metadata = contextService.getReleaseMetadata(releaseId);
            if (metadata == null) {
                return BotResponse.text("❌ Не вдалося отримати дані про реліз. Спробуйте ще раз.");
            }

            return startFileSearch(chatId, metadata);
        }

        return BotResponse.text("Невідома команда.");
    }

    private BotResponse startFileSearch(long chatId, ReleaseMetadata metadata) {
        searchFilesProducer.send(SearchFilesTaskDto.of(chatId, metadata.id(), metadata.artist(), metadata.title()));

        return BotResponse.text(
                "🔎 шукаю: _%s - %s_".formatted(
                        metadata.artist(),
                        metadata.title()).toLowerCase()
        );
    }
}
