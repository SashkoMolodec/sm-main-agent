package com.sashkomusic.mainagent.domain.service.download;

import com.sashkomusic.mainagent.api.telegram.dto.BotResponse;
import com.sashkomusic.mainagent.domain.model.DownloadEngine;
import com.sashkomusic.mainagent.domain.model.DownloadOption;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;

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
    public BotResponse buildAutoDownloadResponse(DownloadOption option, String releaseId) {
        String message = "✅ **знайшов то шо треба, для душі, качаю:**\n`%s`".formatted(option.displayName());

        var buttons = new LinkedHashMap<String, String>();
        buttons.put("❌", "CANCEL_DL:" + releaseId);

        return BotResponse.withButtons(message, buttons);
    }
}
