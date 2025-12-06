package com.sashkomusic.mainagent.config;

import com.sashkomusic.mainagent.domain.model.DownloadEngine;
import com.sashkomusic.mainagent.domain.service.download.DownloadSourceService;
import com.sashkomusic.mainagent.domain.service.download.QobuzDownloadService;
import com.sashkomusic.mainagent.domain.service.download.SoulseekDownloadService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class DownloadSourceConfig {

    @Bean
    public Map<DownloadEngine, DownloadSourceService> downloadSources(
            QobuzDownloadService qobuzService,
            SoulseekDownloadService soulseekService
    ) {
        return Map.of(
                DownloadEngine.QOBUZ, qobuzService,
                DownloadEngine.SOULSEEK, soulseekService
        );
    }
}
