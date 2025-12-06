package com.sashkomusic.mainagent.domain.service.download;

import com.sashkomusic.mainagent.api.telegram.dto.BotResponse;
import com.sashkomusic.mainagent.domain.model.DownloadEngine;
import com.sashkomusic.mainagent.domain.model.DownloadOption;

import java.util.List;

public interface DownloadSourceHandler {

    boolean shouldAutoDownload(List<DownloadOptionsAnalyzer.OptionReport> reports);

    BotResponse buildSearchResultsResponse(String formattedText, String releaseId, DownloadEngine currentSource);

    String formatDownloadConfirmation(DownloadOption option);
}
