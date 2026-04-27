package com.chatbot.controller;

import com.chatbot.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatbotController {

    private final ChatService chatService;

    public ChatbotController(ChatService chatService) {
        this.chatService = chatService;
    }

    // Request body is plain text, response is plain text
    @PostMapping
    public ResponseEntity<String> chat(@RequestBody String message) {
        try {
            String reply = chatService.chat(message);
            return ResponseEntity.ok(reply);
        } catch (Exception e) {
            String errorMessage = "Error: " + e.getMessage();
            if (e.getCause() != null) {
                errorMessage += " | Cause: " + e.getCause().getMessage();
            }
            return ResponseEntity.status(500).body(errorMessage);
        }
    }

    // Handle GET request so the user doesn't see a Whitelabel Error Page in browser
    @GetMapping
    public ResponseEntity<String> checkStatus() {
        return ResponseEntity.ok("Backend is running successfully! Use POST to send messages.");
    }
}
