package com.sashkomusic.mainagent.domain.service.download;

import com.sashkomusic.mainagent.domain.model.DownloadOption;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class QobuzDownloadOptionsAnalyzer implements DownloadOptionsAnalyzer {

    // Quality priority mapping (higher = better)
    private static final Map<String, Integer> QUALITY_PRIORITY = Map.of(
            "27", 4,  // 24-Bit/192 kHz
            "7", 3,   // 24-Bit/96 kHz
            "6", 2,   // 16-Bit/44.1 kHz
            "5", 1    // MP3 320
    );

    @Override
    public AnalysisResult analyzeAll(List<DownloadOption> options, String releaseId, long chatId) {
        if (options.isEmpty()) {
            return new AnalysisResult(List.of(), "");
        }

        var reports = options.stream()
                .map(opt -> new OptionReport(opt, Suitability.PERFECT))
                .sorted(Comparator.comparingInt(this::getQualityPriority).reversed())
                .toList();

        return new AnalysisResult(reports, "");
    }

    private int getQualityPriority(OptionReport report) {
        String quality = report.option().technicalMetadata().get("quality");
        return QUALITY_PRIORITY.getOrDefault(quality, 0);
    }
}
