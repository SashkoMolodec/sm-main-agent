package com.sashkomusic.mainagent.domain.service.download;

import com.sashkomusic.mainagent.api.telegram.dto.BotResponse;
import com.sashkomusic.mainagent.domain.model.DownloadEngine;
import com.sashkomusic.mainagent.domain.model.DownloadOption;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class BandcampDownloadFlowHandler implements DownloadFlowHandler {

    @Override
    public AnalysisResult analyzeAll(List<DownloadOption> options, String releaseId, long chatId) {
        if (options.isEmpty()) {
            return new AnalysisResult(List.of(), "");
        }

        var reports = options.stream()
                .map(opt -> new OptionReport(opt, Suitability.GOOD))
                .toList();

        return new AnalysisResult(reports, "");
    }

    @Override
    public BotResponse buildSearchResultsResponse(String formattedText, String releaseId, DownloadEngine currentSource) {
        return BotResponse.text(formattedText);
    }

    @Override
    public String formatDownloadConfirmation(DownloadOption option) {
        return "✅ *ок, качаю з bandcamp:*\n%s".formatted(option.displayName());
    }

    @Override
    public Optional<DownloadEngine> getFallbackDownloadEngine() {
        return Optional.empty();
    }
}
