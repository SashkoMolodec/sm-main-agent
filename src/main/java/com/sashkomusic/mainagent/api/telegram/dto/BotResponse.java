package com.sashkomusic.mainagent.api.telegram.dto;

import java.util.Map;

public record BotResponse(
        String text,
        String imageUrl,
        Map<String, String> buttons
) {
    public static BotResponse text(String text) {
        return new BotResponse(text, null, null);
    }

    public static BotResponse withButtons(String text, Map<String, String> buttons) {
        return new BotResponse(text, null, buttons);
    }

    public static BotResponse card(String text, String imageUrl, Map<String, String> buttons) {
        return new BotResponse(text, imageUrl, buttons);
    }
}