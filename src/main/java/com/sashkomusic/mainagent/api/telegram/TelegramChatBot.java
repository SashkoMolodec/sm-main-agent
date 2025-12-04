package com.sashkomusic.mainagent.api.telegram;

import com.sashkomusic.mainagent.api.telegram.dto.BotResponse;
import com.sashkomusic.mainagent.domain.exception.SearchSessionExpiredException;
import com.sashkomusic.mainagent.domain.service.UserInteractionOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class TelegramChatBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {
    private final UserInteractionOrchestrator orchestrator;
    private final TelegramClient client;
    private final String botToken;

    public TelegramChatBot(@Value("${telegram.bot.token}") String token,
                           UserInteractionOrchestrator orchestrator,
                           TelegramClient telegramClient) {
        this.botToken = token;
        this.client = telegramClient;
        this.orchestrator = orchestrator;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                var text = update.getMessage().getText();
                final long chatId = update.getMessage().getChatId();
                log.info("📩 Text from [{}]: {}", chatId, text);

                orchestrator.handleUserRequest(chatId, text)
                        .forEach(res -> sendResponse(chatId, res));

            } else if (update.hasCallbackQuery()) {
                var callback = update.getCallbackQuery();
                var data = callback.getData();
                final long chatId = callback.getMessage().getChatId();
                var queryId = callback.getId();

                log.info("👆 Click from [{}]: {}", chatId, data);
                answerCallback(queryId);

                orchestrator.handleCallback(chatId, data)
                        .forEach(response -> sendResponse(chatId, response));
            }
        } catch (SearchSessionExpiredException e) {
            log.warn("Session expired: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in consumer: ", e);
        }
    }

    public void sendResponse(long chatId, BotResponse response) {
        var keyboardMarkup = createKeyboard(response.buttons());
        boolean hasImage = response.imageUrl() != null && !response.imageUrl().isBlank();

        if (hasImage) {
            try {
                SendPhoto photo = SendPhoto.builder()
                        .chatId(chatId)
                        .photo(new InputFile(response.imageUrl()))
                        .caption(response.text())
                        .parseMode("Markdown")
                        .replyMarkup(keyboardMarkup)
                        .build();
                client.execute(photo);
                return;

            } catch (TelegramApiException e) {
                log.warn("⚠️ Failed to send photo to [{}]. URL: {}. Error: {}",
                        chatId, response.imageUrl(), e.getMessage());
            }
        }

        try {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId)
                    .text(response.text())
                    .parseMode("Markdown")
                    .replyMarkup(keyboardMarkup)
                    .build();
            client.execute(message);
        } catch (TelegramApiException e) {
            log.error("❌ Failed to send with Markdown parsing to [{}]: {}. Retrying as plain text",
                    chatId, e.getMessage());

            // Fallback: send as plain text without parsing
            try {
                SendMessage plainMessage = SendMessage.builder()
                        .chatId(chatId)
                        .text(response.text())
                        .replyMarkup(keyboardMarkup)
                        .build();
                client.execute(plainMessage);
                log.info("✅ Successfully sent as plain text");
            } catch (TelegramApiException ex) {
                log.error("❌ Failed to send even as plain text to [{}]: {}", chatId, ex.getMessage());
            }
        }
    }

    public void sendMessage(long chatId, String text) {
        sendResponse(chatId, BotResponse.text(text));
    }

    private InlineKeyboardMarkup createKeyboard(Map<String, String> buttons) {
        if (buttons == null || buttons.isEmpty()) {
            return null;
        }

        List<InlineKeyboardButton> rowButtons = new ArrayList<>();
        for (var entry : buttons.entrySet()) {
            String label = entry.getKey();
            String value = entry.getValue();
            var buttonBuilder = InlineKeyboardButton.builder().text(label);

            if (value.startsWith("URL:")) {
                buttonBuilder.url(value.substring(4));
            } else {
                buttonBuilder.callbackData(value);
            }
            rowButtons.add(buttonBuilder.build());
        }
        return new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(rowButtons)));
    }

    private void answerCallback(String queryId) {
        try {
            client.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(queryId)
                    .build());
        } catch (TelegramApiException e) {
            log.warn("⚠️ Could not answer callback: {}", e.getMessage());
        }
    }
}