package com.chatbot;

import com.chatbot.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class ChatbotBackendApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void shouldResolveSupportedProviderNames() {
		assertEquals("NVIDIA", ChatService.resolveProviderName(null));
		assertEquals("NVIDIA", ChatService.resolveProviderName("nvidia"));
		assertEquals("GEMINI", ChatService.resolveProviderName("gemini"));
		assertEquals("GROQ", ChatService.resolveProviderName("groq"));
		assertEquals("NVIDIA", ChatService.resolveProviderName("unknown"));
	}

}
