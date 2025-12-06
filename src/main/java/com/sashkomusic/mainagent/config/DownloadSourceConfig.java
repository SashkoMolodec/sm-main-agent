package com.sashkomusic.mainagent.config;

import com.sashkomusic.mainagent.domain.model.DownloadEngine;
import com.sashkomusic.mainagent.domain.service.download.DownloadFlowHandler;
import com.sashkomusic.mainagent.domain.service.download.QobuzDownloadFlowHandler;
import com.sashkomusic.mainagent.domain.service.download.SoulseekDownloadFlowHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class DownloadSourceConfig {

    @Bean
    public Map<DownloadEngine, DownloadFlowHandler> downloadFlowHandlers(
            QobuzDownloadFlowHandler qobuzHandler,
            SoulseekDownloadFlowHandler soulseekHandler
    ) {
        return Map.of(
                DownloadEngine.QOBUZ, qobuzHandler,
                DownloadEngine.SOULSEEK, soulseekHandler
        );
    }
}
