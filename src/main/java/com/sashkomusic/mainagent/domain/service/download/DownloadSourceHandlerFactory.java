package com.sashkomusic.mainagent.domain.service.download;

import com.sashkomusic.mainagent.domain.model.DownloadEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DownloadSourceHandlerFactory {

    private final QobuzDownloadHandler qobuzHandler;
    private final SoulseekDownloadHandler soulseekHandler;

    public DownloadSourceHandler getHandler(DownloadEngine source) {
        return switch (source) {
            case QOBUZ -> qobuzHandler;
            case SOULSEEK -> soulseekHandler;
        };
    }
}
