package com.sashkomusic.mainagent.domain.service.djtag;

import com.sashkomusic.mainagent.infrastracture.client.api.dto.TrackDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class DjTagContextHolder {
    private final Map<Long, DjTagContext> contexts = new ConcurrentHashMap<>();

    public void setTrackContext(long chatId, TrackDto track, String navidromeId, boolean waitingForComment) {
        contexts.put(chatId, new DjTagContext(track, navidromeId, waitingForComment));
        log.debug("Set track context for chat {}, track {}, waitingForComment={}",
                chatId, track.id(), waitingForComment);
    }

    public void activateCommentMode(long chatId) {
        DjTagContext existing = contexts.get(chatId);
        if (existing != null) {
            contexts.put(chatId, new DjTagContext(existing.track, existing.navidromeId, true));
            log.debug("Activated comment mode for chat {}, track {}", chatId, existing.track.id());
        } else {
            log.warn("Cannot activate comment mode for chat {} - no track context", chatId);
        }
    }

    public boolean isWaitingForComment(long chatId) {
        DjTagContext ctx = contexts.get(chatId);
        return ctx != null && ctx.waitingForComment;
    }

    public DjTagContext getContext(long chatId) {
        return contexts.get(chatId);
    }

    public void clearContext(long chatId) {
        contexts.remove(chatId);
        log.debug("Cleared context for chat {}", chatId);
    }

    public void clearAllContexts() {
        contexts.clear();
        log.info("Cleared all DJ tag contexts");
    }

    public record DjTagContext(TrackDto track, String navidromeId, boolean waitingForComment) {
        public Long trackId() {
            return track.id();
        }
    }
}
