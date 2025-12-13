package com.sashkomusic.mainagent.messaging.consumer;

import com.sashkomusic.mainagent.api.telegram.TelegramChatBot;
import com.sashkomusic.mainagent.messaging.consumer.dto.TagChangesNotificationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class TagChangesNotificationListener {

    private final TelegramChatBot chatBot;

    public static final String TOPIC = "tag-changes";

    @Value("${telegram.default-chat-id}")
    private Long defaultChatId;

    @KafkaListener(topics = TOPIC, groupId = "main-agent-group")
    public void handleTagChanges(TagChangesNotificationDto notification) {
        log.info("Received tag changes notification: {} tracks, {} total changes",
                notification.tracks().size(), notification.totalChanges());

        String message = buildNotificationMessage(notification);
        chatBot.sendMessage(defaultChatId, message);
    }

    private String buildNotificationMessage(TagChangesNotificationDto notification) {
        StringBuilder sb = new StringBuilder();

        sb.append("🎵 **оновлено теги треків**\n\n");

        for (TagChangesNotificationDto.TrackChanges track : notification.tracks()) {
            sb.append("📀 _")
              .append(track.artistName().toLowerCase())
              .append(" — ")
              .append(track.trackTitle().toLowerCase())
              .append("_\n");

            for (TagChangesNotificationDto.TagChangeInfo change : track.changes()) {
                String tagDisplay = formatTagName(change.tagName());
                String oldValueDisplay = formatTagValue(change.tagName(), change.oldValue());
                String newValueDisplay = formatTagValue(change.tagName(), change.newValue());

                if (change.isNew()) {
                    sb.append("   ➕ ")
                      .append(tagDisplay)
                      .append(": ")
                      .append(newValueDisplay)
                      .append("\n");
                } else {
                    sb.append("   ✏️ ")
                      .append(tagDisplay)
                      .append(": ")
                      .append(oldValueDisplay)
                      .append(" → ")
                      .append(newValueDisplay)
                      .append("\n");
                }
            }

            sb.append("\n");
        }

        sb.append("_всього змін: ")
          .append(notification.totalChanges())
          .append("_");

        return sb.toString();
    }

    private String formatTagName(String tagName) {
        return switch (tagName.toUpperCase()) {
            // Standard ID3 tags
            case "TBPM" -> "bpm";
            case "TKEY" -> "key";
            case "INITIALKEY" -> "тональність";
            case "RATING" -> "рейтинг";
            case "PUBLISHER" -> "лейбл";
            case "TIT2" -> "назва";
            case "TPE1" -> "виконавець";
            case "TALB" -> "альбом";
            case "TCON" -> "жанр";
            case "TDRC", "TYER" -> "рік";
            case "COMM" -> "коментар";
            case "TCOM" -> "композитор";
            case "GRP1", "GRPG" -> "групування";
            case "TRCK" -> "номер треку";
            case "TPOS" -> "номер диску";

            // Traktor custom tags
            case "TXXX:INITIALKEY" -> "initial key";
            case "TXXX:ENERGY" -> "energy";
            case "TXXX:COLOR" -> "color";
            case "TXXX:RATING" -> "rating (traktor)";
            case "TXXX:BPM" -> "bpm (traktor)";
            case "TXXX:KEY" -> "key (traktor)";

            default -> {
                if (tagName.startsWith("TXXX:")) {
                    yield tagName.substring(5).toLowerCase();
                }
                yield tagName.toLowerCase();
            }
        };
    }

    private String formatTagValue(String tagName, String value) {
        if (value == null || value.isEmpty()) {
            return "`—`";
        }

        if ("RATING".equalsIgnoreCase(tagName)) {
            return ratingToStars(value);
        }

        return "`" + value + "`";
    }

    private String ratingToStars(String ratingStr) {
        try {
            int rating = Integer.parseInt(ratingStr);
            if (rating == 0) return "☆☆☆☆☆";
            if (rating <= 51) return "★☆☆☆☆";   // 1 star (Traktor: 51)
            if (rating <= 102) return "★★☆☆☆";  // 2 stars (Traktor: 102)
            if (rating <= 153) return "★★★☆☆"; // 3 stars (Traktor: 153)
            if (rating <= 204) return "★★★★☆"; // 4 stars (Traktor: 204)
            return "★★★★★";                    // 5 stars (Traktor: 255)
        } catch (Exception e) {
            return "`" + ratingStr + "`";
        }
    }
}
