package com.sashkomusic.mainagent.domain.service.download;

import com.sashkomusic.mainagent.api.telegram.dto.BotResponse;
import com.sashkomusic.mainagent.domain.model.DownloadEngine;
import com.sashkomusic.mainagent.domain.model.DownloadOption;

import java.util.List;

public interface DownloadFlowHandler {

    AnalysisResult analyzeAll(List<DownloadOption> options, String releaseId, long chatId);

    boolean shouldAutoDownload(List<OptionReport> reports);

    BotResponse buildSearchResultsResponse(String formattedText, String releaseId, DownloadEngine currentSource);

    String formatDownloadConfirmation(DownloadOption option);

    record OptionReport(
            DownloadOption option,
            Suitability suitability
    ) {
    }

    record AnalysisResult(
            List<OptionReport> reports,
            String aiSummary
    ) {
    }

    enum Suitability {
        PERFECT("💎"),
        GOOD("🟢"),
        WARNING("🟡"),
        BAD("🔴");

        public final String icon;

        Suitability(String icon) {
            this.icon = icon;
        }
    }
}
