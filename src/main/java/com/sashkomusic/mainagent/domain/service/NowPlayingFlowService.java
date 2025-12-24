package com.sashkomusic.mainagent.domain.service;

import com.sashkomusic.mainagent.api.telegram.dto.BotResponse;
import com.sashkomusic.mainagent.infrastracture.client.api.ApiClient;
import com.sashkomusic.mainagent.infrastracture.client.api.dto.TrackDto;
import com.sashkomusic.mainagent.infrastracture.client.navidrome.NavidromeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NowPlayingFlowService {

    private final NavidromeClient navidromeClient;
    private final ApiClient apiClient;

    public List<BotResponse> nowPlaying() {

        String trackPath = navidromeClient.getCurrentlyPlayingTrackPath();
        Optional<TrackDto> track = apiClient.findTrackByTitle(extractTrackTitle(trackPath));

        TrackDto trackDto = track.orElseGet(TrackDto::empty);
        return List.of(BotResponse.text("зараз грає: %s, id: %d".formatted(trackDto.title(), trackDto.id())));
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
}
