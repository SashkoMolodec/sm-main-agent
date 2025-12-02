package com.sashkomusic.mainagent.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramCommandsConfig {

    private final TelegramClient telegramClient;

    @PostConstruct
    public void registerCommands() {
        try {
            List<BotCommand> commands = List.of(
                    new BotCommand("process", "обробити папку з музикою (вкажи назву папки)")
            );

            SetMyCommands setMyCommands = SetMyCommands.builder()
                    .commands(commands)
                    .scope(new BotCommandScopeDefault())
                    .build();

            telegramClient.execute(setMyCommands);
            log.info("✅ Bot commands registered successfully");
        } catch (Exception e) {
            log.error("❌ Failed to register bot commands: {}", e.getMessage(), e);
        }
    }
}