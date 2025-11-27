package com.sashkomusic.mainagent.domain.service.download;

import com.sashkomusic.mainagent.ai.service.AiService;
import com.sashkomusic.mainagent.api.telegram.dto.BotResponse;
import com.sashkomusic.mainagent.domain.model.ReleaseMetadata;
import com.sashkomusic.mainagent.domain.service.SearchContextHolder;
import com.sashkomusic.mainagent.messaging.consumer.dto.SearchFilesResultDto;
import com.sashkomusic.mainagent.messaging.producer.dto.DownloadFilesTaskDto;
import com.sashkomusic.mainagent.messaging.producer.dto.SearchFilesTaskDto;
import com.sashkomusic.mainagent.messaging.producer.DownloadTaskProducer;
import com.sashkomusic.mainagent.messaging.producer.SearchFilesTaskProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class MusicDownloadFlowService {

    private final AiService aiService;
    private final SearchFilesTaskProducer searchFilesProducer;
    private final DownloadTaskProducer downloadTaskProducer;
    private final SearchContextHolder contextService;
    private final DownloadOptionsAnalyzer analyzer;
    private final DownloadOptionsFormatter formatter;

    public BotResponse handleCallback(long chatId, String data) {
        if (data.startsWith("DL:")) {
            String releaseId = data.substring(3);
            log.info("User selected release ID: {}", releaseId);

            ReleaseMetadata metadata = contextService.getReleaseMetadata(releaseId);
            if (metadata == null) {
                return BotResponse.text("❌ шось ся не получило...найди реліз ше раз");
            }

            return startFileSearch(chatId, metadata);
        }

        return BotResponse.text("тєжко.");
    }

    private BotResponse startFileSearch(long chatId, ReleaseMetadata metadata) {
        searchFilesProducer.send(SearchFilesTaskDto.of(chatId, metadata.id(), metadata.artist(), metadata.title()));

        return BotResponse.text(
                "🔎 шукаю: _%s - %s_".formatted(
                        metadata.artist(),
                        metadata.title()).toLowerCase()
        );
    }

    public String handleSearchResults(SearchFilesResultDto dto) {
        log.info("Processing search results for chatId={}, releaseId={}", dto.chatId(), dto.releaseId());

        var analysisResult = analyzer.analyzeAll(dto.results(), dto.releaseId());
        var reports = analysisResult.reports();
        contextService.setDownloadOptionReports(reports);
        contextService.setChosenReleaseForDownload(dto.releaseId());

        reports.forEach(r -> log.info("{}", r));
        return formatter.format(reports, analysisResult.aiSummary());
    }

    public List<BotResponse> handleDownload(long chatId, String rawInput) {
        var reports = contextService.getDownloadOptionReports();
        if (reports.isEmpty()) {
            return List.of(BotResponse.text("😔 **варіанти пропали, нич нема, давай ше раз.**"));
        }

        Integer optionNumber = aiService.parseDownloadOptionNumber(rawInput);
        if (optionNumber == null || optionNumber < 1 || optionNumber > reports.size()) {
            return List.of(BotResponse.text("🤔 **незрозумілий зроз.**"));
        }

        var chosenReport = reports.get(optionNumber - 1);
        var option = chosenReport.option();
        String releaseId = contextService.getChosenReleaseForDownload();

        log.info("User chose option #{}: {} from {}", optionNumber, option.id(), option.distributorName());

        downloadTaskProducer.send(DownloadFilesTaskDto.of(chatId, releaseId, option));

        String message = "✅ **ок, качаю:**\n%s - %s\n📦 %d файлів, %d MB"
                .formatted(
                        option.distributorName(),
                        option.sourceName(),
                        option.files().size(),
                        option.totalSize()
                );

        return List.of(BotResponse.withButtons(
                message,
                Map.of("📊 переглянути статус", "URL:http://localhost:5030/downloads/")
        ));
    }
}
