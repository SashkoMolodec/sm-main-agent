package com.sashkomusic.mainagent.domain.service.download;

import com.sashkomusic.mainagent.api.telegram.dto.BotResponse;
import com.sashkomusic.mainagent.domain.model.DownloadEngine;
import com.sashkomusic.mainagent.domain.model.DownloadOption;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;

@Component
public class QobuzDownloadHandler implements DownloadSourceHandler {

    @Override
    public boolean shouldAutoDownload(List<DownloadOptionsAnalyzer.OptionReport> reports) {
        return reports.size() == 1;
    }

    @Override
    public BotResponse buildSearchResultsResponse(String formattedText, String releaseId, DownloadEngine currentSource) {
        var buttons = new LinkedHashMap<String, String>();
        buttons.put("⛏️", "SEARCH_ALT:" + releaseId + ":SOULSEEK");
        return BotResponse.withButtons(formattedText, buttons);
    }

    @Override
    public String formatDownloadConfirmation(DownloadOption option) {
        return "✅ **ок, качаю:**\n%s".formatted(option.displayName());
    }
}
