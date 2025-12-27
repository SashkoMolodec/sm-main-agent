package com.sashkomusic.mainagent.domain.service;

import com.sashkomusic.mainagent.api.telegram.dto.BotResponse;
import com.sashkomusic.mainagent.infrastracture.client.api.ApiClient;
import com.sashkomusic.mainagent.infrastracture.client.api.dto.TrackDto;
import com.sashkomusic.mainagent.infrastracture.client.navidrome.NavidromeClient;
import com.sashkomusic.mainagent.messaging.producer.RateTrackTaskProducer;
import com.sashkomusic.mainagent.messaging.producer.dto.RateTrackTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class NowPlayingFlowService {

    private final NavidromeClient navidromeClient;
    private final ApiClient apiClient;
    private final RateTrackTaskProducer rateTrackTaskProducer;

    public List<BotResponse> nowPlaying() {
        NavidromeClient.CurrentTrackInfo trackInfo = navidromeClient.getCurrentlyPlayingTrackInfo();

        if (trackInfo == null) {
            return List.of(BotResponse.text("зараз нич не грає 🥺"));
        }

        Optional<TrackDto> track = apiClient.findTrackByArtistAndTitle(trackInfo.artist(), trackInfo.title());

        TrackDto trackDto = track.orElseGet(TrackDto::empty);

        if (trackDto.id() == null) {
            return List.of(BotResponse.text("зараз грає: %s - %s, але трек не знайдено в БД".formatted(trackInfo.artist(), trackInfo.title())));
        }

        StringBuilder message = new StringBuilder();

        message.append("зараз лабанить ");

        if (trackDto.artistName() != null && !trackDto.artistName().isEmpty()) {
            message.append("_").append(trackDto.artistName()).append(" — ").append(trackDto.title()).append("_");
        } else {
            message.append("_").append(trackDto.title()).append("_");
        }

        if (trackDto.rating() != null) {
            String stars = convertWmpRatingToStars(trackDto.rating());
            message.append("\n").append(stars);
        }

        message.append("\n\n✏️ оціни:");

        Map<String, String> ratingButtons = createRatingButtons(trackDto.id(), trackInfo.navidromeId());
        return List.of(BotResponse.withButtons(message.toString().toLowerCase(), ratingButtons));
    }


    private String extractTrackTitle(String path) {
        // Беремо тільки ім'я файлу (напр. "03 - Outro45cut.flac")
        String filename = new java.io.File(path).getName();

        // Видаляємо розширення
        String nameWithoutExt = filename.replaceFirst("\\.[^.]+$", "");

        // Видаляємо номер треку і дефіс на початку (напр. "03 - ", "01. ")
        String title = nameWithoutExt.replaceFirst("^[A-Z]?\\d+[\\s.-]+", "");

        return title.trim();
    }

    private Map<String, String> createRatingButtons(Long trackId, String navidromeId) {
        Map<String, String> buttons = new LinkedHashMap<>();
        buttons.put("⭐ 1", "RATE:" + trackId + ":1:" + navidromeId);
        buttons.put("⭐ 2", "RATE:" + trackId + ":2:" + navidromeId);
        buttons.put("⭐ 3", "RATE:" + trackId + ":3:" + navidromeId);
        buttons.put("⭐ 4", "RATE:" + trackId + ":4:" + navidromeId);
        buttons.put("⭐ 5", "RATE:" + trackId + ":5:" + navidromeId);
        return buttons;
    }

    public List<BotResponse> rateTrack(long chatId, Long trackId, int rating) {
        log.info("Rating track {} with {} stars from chatId={}", trackId, rating, chatId);
        RateTrackTaskDto task = new RateTrackTaskDto(trackId, rating, chatId);
        rateTrackTaskProducer.send(task);
        return List.of();
    }

    private String convertWmpRatingToStars(String ratingStr) {
        try {
            int rating = Integer.parseInt(ratingStr);
            // Convert WMP format to stars
            if (rating == 0) return "☆☆☆☆☆";
            if (rating <= 51) return "★☆☆☆☆";   // 1 star
            if (rating <= 102) return "★★☆☆☆";  // 2 stars
            if (rating <= 153) return "★★★☆☆";  // 3 stars
            if (rating <= 204) return "★★★★☆";  // 4 stars
            return "★★★★★";                      // 5 stars
        } catch (NumberFormatException e) {
            log.warn("Invalid rating format: {}", ratingStr);
            return "";
        }
    }
}
