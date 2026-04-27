package com.chatbot.config;

import org.springframework.context.annotation.Configuration;

// ChatClient (Spring AI) has been replaced with direct Gemini REST API calls in ChatService.
// This config class is kept as a placeholder.
@Configuration
public class ChatbotConfig {
    // No beans needed — ChatService uses RestTemplate directly
}
