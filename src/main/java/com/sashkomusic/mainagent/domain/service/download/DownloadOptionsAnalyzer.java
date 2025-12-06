package com.sashkomusic.mainagent.domain.service.download;

import com.sashkomusic.mainagent.domain.model.DownloadOption;

import java.util.List;

public interface DownloadOptionsAnalyzer {

    AnalysisResult analyzeAll(List<DownloadOption> options, String releaseId, long chatId);

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
