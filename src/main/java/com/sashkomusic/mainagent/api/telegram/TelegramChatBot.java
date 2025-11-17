package com.sashkomusic.mainagent.api.telegram;

import com.sashkomusic.mainagent.ai.service.AiService;
import com.sashkomusic.mainagent.domain.service.MusicMetadataService;
import com.sashkomusic.mainagent.messaging.consumer.SearchFilesTaskProducer;
import com.sashkomusic.mainagent.messaging.dto.SearchFilesTaskDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
public class TelegramChatBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {
    private final AiService aiService;
    private final MusicMetadataService musicMetadataService;
    private final SearchFilesTaskProducer searchFilesTaskProducer;
    private final TelegramClient client;
    private final String botToken;

    public TelegramChatBot(@Value("${telegram.bot.token}") String token, AiService aiService, MusicMetadataService musicMetadataService, SearchFilesTaskProducer searchFilesTaskProducer) {
        botToken = token;
        this.aiService = aiService;
        this.musicMetadataService = musicMetadataService;
        this.searchFilesTaskProducer = searchFilesTaskProducer;
        client = new OkHttpTelegramClient(botToken);
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
        if (update.hasMessage() && update.getMessage().hasText()) {
            String input = update.getMessage().getText();
            var chatId = update.getMessage().getChatId();

            var query = aiService.extractSearchQuery(input);
            var metadata = musicMetadataService.search(query.artist(), query.title());

            searchFilesTaskProducer.send(SearchFilesTaskDto.of(chatId, metadata.artist(), metadata.title()));

            var chatBotResponse = "Found %d different releases for title: %s, artist: %s. Querying sources...".formatted(
                    metadata.releasesFound(),
                    query.title(), query.artist());

            SendMessage message = SendMessage
                    .builder()
                    .chatId(chatId)
                    .text(chatBotResponse)
                    .build();
            try {
                client.execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }
}
