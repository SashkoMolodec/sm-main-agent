package com.sashkomusic.mainagent.domain.service;

import com.sashkomusic.mainagent.api.telegram.dto.BotResponse;
import com.sashkomusic.mainagent.domain.service.djtag.DjTagContextHolder;
import com.sashkomusic.mainagent.domain.service.download.DownloadContextHolder;
import com.sashkomusic.mainagent.domain.service.process.ProcessFolderContextHolder;
import com.sashkomusic.mainagent.domain.service.search.SearchContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UtilFlowService {

    private final SearchContextService searchContextService;
    private final DownloadContextHolder downloadContextHolder;
    private final ProcessFolderContextHolder processFolderContextHolder;
    private final DjTagContextHolder djTagContextHolder;

    public List<BotResponse> clearAllCaches() {
        log.info("Clearing all in-memory caches");

        searchContextService.clearAllCaches();
        downloadContextHolder.clearAllSessions();
        processFolderContextHolder.clearAllContexts();
        djTagContextHolder.clearAllContexts();

        return List.of(BotResponse.text("🧹 усі кеші очищено"));
    }
}
