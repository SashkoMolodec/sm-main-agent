package com.sashkomusic.mainagent.config;

import com.sashkomusic.mainagent.ai.service.AiService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiServiceConfig {

    @Bean
    public AiService aiService(ChatModel chatModel) {
        return AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .build();
    }
}
