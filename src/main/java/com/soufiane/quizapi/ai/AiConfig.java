package com.soufiane.quizapi.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Builds the ChatClient from the auto-configured Anthropic chat model (settings in application.properties). */
@Configuration
public class AiConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}