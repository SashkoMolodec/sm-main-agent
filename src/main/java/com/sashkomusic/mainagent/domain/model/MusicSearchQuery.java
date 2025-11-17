package com.sashkomusic.mainagent.domain.model;

import dev.langchain4j.model.output.structured.Description;

public record MusicSearchQuery(
        @Description("release title") String title,
        String artist) {
}

