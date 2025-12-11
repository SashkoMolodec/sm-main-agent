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

                if (change.isNew()) {
                    sb.append("   ➕ ")
                      .append(tagDisplay)
                      .append(": `")
                      .append(change.newValue())
                      .append("`\n");
                } else {
                    sb.append("   ✏️ ")
                      .append(tagDisplay)
                      .append(": `")
                      .append(change.oldValue() != null ? change.oldValue() : "—")
                      .append("` → `")
                      .append(change.newValue())
                      .append("`\n");
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
            case "RATING" -> "rating";
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
}
