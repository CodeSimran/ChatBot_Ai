package com.chatbot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class ChatService {

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.model:llama3-8b-8192}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();

    // Groq uses an OpenAI-compatible API endpoint
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

    public String chat(String userMessage) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        // Build OpenAI-compatible request body
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", userMessage);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", List.of(message));
        requestBody.put("max_tokens", 1024);
        requestBody.put("temperature", 0.7);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    GROQ_URL,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            // Parse OpenAI-compatible response: choices[0].message.content
            Map body = response.getBody();
            if (body != null && body.containsKey("choices")) {
                List<Map> choices = (List<Map>) body.get("choices");
                if (!choices.isEmpty()) {
                    Map choice = choices.get(0);
                    Map msg = (Map) choice.get("message");
                    if (msg != null) {
                        return (String) msg.get("content");
                    }
                }
            }
            return "Sorry, I could not get a response.";
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate content: " + e.getMessage(), e);
        }
    }
}
