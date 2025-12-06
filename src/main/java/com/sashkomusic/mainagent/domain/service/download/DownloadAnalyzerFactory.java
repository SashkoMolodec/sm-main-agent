package com.sashkomusic.mainagent.domain.service.download;

import com.sashkomusic.mainagent.domain.model.DownloadEngine;
import com.sashkomusic.mainagent.domain.model.DownloadOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DownloadAnalyzerFactory {

    private final SoulseekDownloadOptionsAnalyzer soulseekAnalyzer;
    private final QobuzDownloadOptionsAnalyzer qobuzAnalyzer;

    public DownloadOptionsAnalyzer getAnalyzer(List<DownloadOption> options) {
        if (options.isEmpty()) {
            throw new IllegalArgumentException("Cannot determine analyzer for empty options list");
        }

        DownloadEngine source = options.get(0).source();

        return switch (source) {
            case SOULSEEK -> soulseekAnalyzer;
            case QOBUZ -> qobuzAnalyzer;
        };
    }
}
