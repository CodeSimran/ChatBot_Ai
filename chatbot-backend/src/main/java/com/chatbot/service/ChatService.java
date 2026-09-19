package com.chatbot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

@Service
public class ChatService {

    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("NVIDIA", "GEMINI", "GROQ");

    @Value("${ai.provider:NVIDIA}")
    private String provider = "NVIDIA";

    @Value("${nvidia.api.key:}")
    private String nvidiaApiKey;

    @Value("${nvidia.api.model:openai/gpt-oss-20b}")
    private String nvidiaModel;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.api.model:gemini-2.5-flash}")
    private String geminiModel;

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${groq.api.model:llama-3.1-8b-instant}")
    private String groqModel;

    private final RestTemplate restTemplate = createRestTemplate();
    private final List<Map<String, String>> aiLogs = Collections.synchronizedList(new ArrayList<>());

    private static final String NVIDIA_URL = "https://integrate.api.nvidia.com/v1/chat/completions";
    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

    public static String resolveProviderName(String requestedProvider) {
        String normalized = (requestedProvider == null) ? "NVIDIA" : requestedProvider.trim();
        if (normalized.isBlank()) {
            return "NVIDIA";
        }

        String upper = normalized.toUpperCase(Locale.ROOT);
        return SUPPORTED_PROVIDERS.contains(upper) ? upper : "NVIDIA";
    }

    public String getProvider() {
        return resolveProviderName(provider);
    }

    public void setProvider(String provider) {
        this.provider = resolveProviderName(provider);
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(15000);
        requestFactory.setReadTimeout(120000);
        return new RestTemplate(requestFactory);
    }

    public List<Map<String, String>> getAiLogs() {
        synchronized (aiLogs) {
            return new ArrayList<>(aiLogs);
        }
    }

    private void logAiEvent(String level, String message, String source) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("level", level);
        entry.put("message", message);
        entry.put("source", source);
        entry.put("timestamp", Instant.now().toString());

        synchronized (aiLogs) {
            aiLogs.add(0, entry);
            if (aiLogs.size() > 30) {
                aiLogs.subList(30, aiLogs.size()).clear();
            }
        }
    }

    public String chat(String userMessage) {
        return chat(userMessage, null);
    }

    public String chat(String userMessage, String providerOverride) {
        String activeProvider = resolveProviderName(providerOverride != null ? providerOverride : provider);

        if ("GEMINI".equals(activeProvider)) {
            return chatWithGemini(userMessage);
        }

        if ("GROQ".equals(activeProvider)) {
            return chatWithGroq(userMessage);
        }

        return chatWithNvidia(userMessage);
    }

    private String chatWithNvidia(String userMessage) {
        if (nvidiaApiKey == null || nvidiaApiKey.isBlank()) {
            logAiEvent("WARN", "NVIDIA_API_KEY is missing or empty. AI service is unavailable.", "ChatService");
            return localFallback(userMessage, "NVIDIA");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(nvidiaApiKey);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", userMessage);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", nvidiaModel);
        requestBody.put("messages", List.of(message));
        requestBody.put("max_tokens", 512);
        requestBody.put("temperature", 0.7);
        requestBody.put("stream", false);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = null;
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    response = restTemplate.exchange(
                            NVIDIA_URL,
                            HttpMethod.POST,
                            entity,
                            Map.class);
                    break;
                } catch (HttpStatusCodeException e) {
                    String responseBody = e.getResponseBodyAsString();
                    String errorSummary = "NVIDIA API returned status " + e.getStatusCode() + ".";
                    if (responseBody != null && !responseBody.isBlank()) {
                        errorSummary += " Details: " + responseBody;
                    }

                    if (e.getStatusCode().value() == 429) {
                        logAiEvent("RATE_LIMIT", errorSummary, "NVIDIA API");
                    } else {
                        logAiEvent("ERROR", errorSummary, "NVIDIA API");
                    }

                    if (e.getStatusCode().is5xxServerError() && attempt < 2) {
                        Thread.sleep(1000L * attempt);
                        continue;
                    }
                    throw e;
                }
            }

            Map body = response.getBody();
            if (body != null && body.containsKey("choices")) {
                List<Map> choices = (List<Map>) body.get("choices");
                if (!choices.isEmpty()) {
                    Map choice = choices.get(0);
                    Map msg = (Map) choice.get("message");
                    if (msg != null) {
                        Object content = msg.get("content");
                        if (content instanceof String) {
                            logAiEvent("INFO", "AI response delivered successfully.", "ChatService");
                            return (String) content;
                        }
                        if (content instanceof List) {
                            StringBuilder builder = new StringBuilder();
                            for (Object part : (List<?>) content) {
                                if (part instanceof Map) {
                                    Object text = ((Map<?, ?>) part).get("text");
                                    if (text instanceof String) {
                                        builder.append(text);
                                    }
                                }
                            }
                            String combined = builder.toString();
                            if (!combined.isBlank()) {
                                logAiEvent("INFO", "AI response delivered successfully.", "ChatService");
                                return combined;
                            }
                        }
                    }
                }
            }

            String noResponseMessage = "NVIDIA returned no chat response.";
            logAiEvent("ERROR", noResponseMessage, "NVIDIA API");
            throw new IllegalStateException(noResponseMessage);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String messageText = "NVIDIA chat request timed out.";
            logAiEvent("ERROR", messageText, "ChatService");
            throw new IllegalStateException(messageText, e);
        } catch (Exception e) {
            String messageText = "NVIDIA chat is unavailable. Check the NVIDIA API key and model access.";
            logAiEvent("ERROR", messageText + " Root cause: " + e.getMessage(), "ChatService");
            throw new IllegalStateException(messageText, e);
        }
    }

    private String chatWithGemini(String userMessage) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            logAiEvent("WARN", "GEMINI_API_KEY is missing or empty. AI service is unavailable.", "ChatService");
            return localFallback(userMessage, "GEMINI");
        }

        String modelName = (geminiModel == null || geminiModel.isBlank()) ? "gemini-2.5-flash" : geminiModel;
        String requestUrl = String.format(GEMINI_URL, modelName, geminiApiKey);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", 0.7);
        generationConfig.put("maxOutputTokens", 512);

        Map<String, Object> part = new LinkedHashMap<>();
        part.put("text", userMessage);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("parts", List.of(part));

        requestBody.put("contents", List.of(content));
        requestBody.put("generationConfig", generationConfig);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(requestUrl, HttpMethod.POST, entity, Map.class);
            Map body = response.getBody();
            if (body != null && body.containsKey("candidates")) {
                List<Map> candidates = (List<Map>) body.get("candidates");
                if (!candidates.isEmpty()) {
                    Map candidate = candidates.get(0);
                    Map contentMap = (Map) candidate.get("content");
                    if (contentMap != null && contentMap.containsKey("parts")) {
                        List<Map> parts = (List<Map>) contentMap.get("parts");
                        if (!parts.isEmpty()) {
                            Object text = parts.get(0).get("text");
                            if (text instanceof String) {
                                logAiEvent("INFO", "Gemini response delivered successfully.", "ChatService");
                                return (String) text;
                            }
                        }
                    }
                }
            }

            String noResponseMessage = "Gemini returned no chat response.";
            logAiEvent("ERROR", noResponseMessage, "Gemini API");
            throw new IllegalStateException(noResponseMessage);
        } catch (HttpStatusCodeException e) {
            String responseBody = e.getResponseBodyAsString();
            String errorSummary = "Gemini API returned status " + e.getStatusCode() + ".";
            if (responseBody != null && !responseBody.isBlank()) {
                errorSummary += " Details: " + responseBody;
            }

            if (e.getStatusCode().value() == 429) {
                logAiEvent("RATE_LIMIT", errorSummary, "Gemini API");
            } else {
                logAiEvent("ERROR", errorSummary, "Gemini API");
            }

            throw new IllegalStateException("Gemini chat is unavailable. Check the Gemini API key and model access.",
                    e);
        } catch (Exception e) {
            String messageText = "Gemini chat is unavailable. Check the Gemini API key and model access.";
            logAiEvent("ERROR", messageText + " Root cause: " + e.getMessage(), "ChatService");
            throw new IllegalStateException(messageText, e);
        }
    }

    private String chatWithGroq(String userMessage) {
        if (groqApiKey == null || groqApiKey.isBlank()) {
            logAiEvent("WARN", "GROQ_API_KEY is missing or empty. AI service is unavailable.", "ChatService");
            return localFallback(userMessage, "GROQ");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", userMessage);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", (groqModel == null || groqModel.isBlank()) ? "llama-3.1-8b-instant" : groqModel);
        requestBody.put("messages", List.of(message));
        requestBody.put("max_tokens", 512);
        requestBody.put("temperature", 0.7);
        requestBody.put("stream", false);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = null;
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    response = restTemplate.exchange(GROQ_URL, HttpMethod.POST, entity, Map.class);
                    break;
                } catch (HttpStatusCodeException e) {
                    String responseBody = e.getResponseBodyAsString();
                    String errorSummary = "GROQ API returned status " + e.getStatusCode() + ".";
                    if (responseBody != null && !responseBody.isBlank()) {
                        errorSummary += " Details: " + responseBody;
                    }

                    if (e.getStatusCode().value() == 429) {
                        logAiEvent("RATE_LIMIT", errorSummary, "GROQ API");
                    } else {
                        logAiEvent("ERROR", errorSummary, "GROQ API");
                    }

                    if (e.getStatusCode().is5xxServerError() && attempt < 2) {
                        Thread.sleep(1000L * attempt);
                        continue;
                    }
                    throw e;
                }
            }

            Map body = response.getBody();
            if (body != null && body.containsKey("choices")) {
                List<Map> choices = (List<Map>) body.get("choices");
                if (!choices.isEmpty()) {
                    Map choice = choices.get(0);
                    Map msg = (Map) choice.get("message");
                    if (msg != null) {
                        Object content = msg.get("content");
                        if (content instanceof String) {
                            logAiEvent("INFO", "AI response delivered successfully.", "ChatService");
                            return (String) content;
                        }
                        if (content instanceof List) {
                            StringBuilder builder = new StringBuilder();
                            for (Object part : (List<?>) content) {
                                if (part instanceof Map) {
                                    Object text = ((Map<?, ?>) part).get("text");
                                    if (text instanceof String) {
                                        builder.append(text);
                                    }
                                }
                            }
                            String combined = builder.toString();
                            if (!combined.isBlank()) {
                                logAiEvent("INFO", "AI response delivered successfully.", "ChatService");
                                return combined;
                            }
                        }
                    }
                }
            }

            String noResponseMessage = "GROQ returned no chat response.";
            logAiEvent("ERROR", noResponseMessage, "GROQ API");
            throw new IllegalStateException(noResponseMessage);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String messageText = "GROQ chat request timed out.";
            logAiEvent("ERROR", messageText, "ChatService");
            throw new IllegalStateException(messageText, e);
        } catch (Exception e) {
            String messageText = "GROQ chat is unavailable. Check the GROQ API key and model access.";
            logAiEvent("ERROR", messageText + " Root cause: " + e.getMessage(), "ChatService");
            throw new IllegalStateException(messageText, e);
        }
    }

    private String localFallback(String userMessage, String providerName) {
        String message = userMessage == null ? "" : userMessage.trim();
        if (message.isEmpty()) {
            return "Please enter a message and try again.";
        }
        return providerName + " API key is not configured. Your message was: \"" + message
                + "\". Set the correct environment variable to enable AI replies.";
    }
}
