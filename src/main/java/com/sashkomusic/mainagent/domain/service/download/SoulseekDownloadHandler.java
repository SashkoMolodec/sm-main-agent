package com.sashkomusic.mainagent.domain.service.download;

import com.sashkomusic.mainagent.api.telegram.dto.BotResponse;
import com.sashkomusic.mainagent.domain.model.DownloadEngine;
import com.sashkomusic.mainagent.domain.model.DownloadOption;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SoulseekDownloadHandler implements DownloadSourceHandler {

    @Override
    public boolean shouldAutoDownload(List<DownloadOptionsAnalyzer.OptionReport> reports) {
        // Never auto-download from Soulseek - user should choose
        return false;
    }

    @Override
    public BotResponse buildSearchResultsResponse(String formattedText, String releaseId, DownloadEngine currentSource) {
        // No buttons for Soulseek - just show text
        return BotResponse.text(formattedText);
    }

    @Override
    public String formatDownloadConfirmation(DownloadOption option) {
        // Soulseek: show file count and size
        return "✅ **ок, качаю:**\n%s\n📦 %d файлів, %d MB"
                .formatted(
                        option.displayName(),
                        option.files().size(),
                        option.totalSize()
                );
    }
}
