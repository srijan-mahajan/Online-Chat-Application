package com.example.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class GeminiAiService {

    @Value("${gemini.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiAiService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8000); // 8 seconds connect timeout
        factory.setReadTimeout(20000);   // 20 seconds read timeout for live AI generation
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Responds to user prompts with Live AI Generation (Google Gemini / Free LLM) or Knowledge Fallback.
     */
    public String askAi(String prompt) {
        String cleanPrompt = (prompt != null) ? prompt.trim() : "";
        if (cleanPrompt.isEmpty()) {
            return "Hello! I am **Meta AI**. How can I help you today?";
        }

        // 1. Try Google Gemini API if key is provided
        if (apiKey != null && !apiKey.trim().isEmpty() && !apiKey.contains("YOUR_GEMINI_API_KEY")) {
            String geminiRes = callGeminiApi(cleanPrompt);
            if (geminiRes != null && !geminiRes.trim().isEmpty()) {
                return geminiRes;
            }
        }

        // 2. Try 100% Free Live LLM Generation for ANY question (Zero API key needed)
        String liveAiRes = callFreeLiveAi(cleanPrompt);
        if (liveAiRes != null && !liveAiRes.trim().isEmpty()) {
            return liveAiRes;
        }

        // 3. Fallback Knowledge Base
        return generateFallbackResponse(cleanPrompt);
    }

    private String callGeminiApi(String cleanPrompt) {
        String structuredPrompt = 
            "System Directive: You are Meta AI, an intelligent AI Assistant in a real-time chat application. " +
            "Provide a helpful, well-structured, clear explanation using Markdown formatting (bold text, bullet points, and code blocks where appropriate).\n\n" +
            "User Query: " + cleanPrompt;

        String cleanKey = apiKey.trim();
        String[] models = {"gemini-1.5-flash", "gemini-2.0-flash", "gemini-1.5-pro"};

        for (String model : models) {
            try {
                String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + cleanKey;

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                Map<String, Object> textPart = Map.of("text", structuredPrompt);
                Map<String, Object> partsContainer = Map.of("parts", List.of(textPart));
                Map<String, Object> requestBody = Map.of("contents", List.of(partsContainer));

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    JsonNode rootNode = objectMapper.readTree(response.getBody());
                    JsonNode textNode = rootNode.path("candidates")
                            .get(0)
                            .path("content")
                            .path("parts")
                            .get(0)
                            .path("text");
                    if (!textNode.isMissingNode() && !textNode.asText().isEmpty()) {
                        return textNode.asText();
                    }
                }
            } catch (Exception e) {
                System.err.println("Gemini API call failed for [" + model + "]: " + e.getMessage());
            }
        }
        return null;
    }

    private String callFreeLiveAi(String prompt) {
        try {
            String url = "https://text.pollinations.ai/";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> systemMsg = Map.of(
                "role", "system",
                "content", "You are Meta AI, an intelligent AI assistant in a real-time chat application. Format your response cleanly using Markdown formatting."
            );
            Map<String, Object> userMsg = Map.of(
                "role", "user",
                "content", prompt
            );

            Map<String, Object> body = Map.of("messages", List.of(systemMsg, userMsg));
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody().trim();
            }
        } catch (Exception e) {
            System.err.println("Free Live AI API call failed: " + e.getMessage());
        }
        return null;
    }

    private String generateFallbackResponse(String prompt) {
        String cleanPrompt = (prompt != null) ? prompt.trim() : "";
        String lower = cleanPrompt.toLowerCase();
        
        if (lower.contains("jwt") || lower.contains("json web token")) {
            return "🔐 **JSON Web Token (JWT)** is an open standard (RFC 7519) for securely transmitting information between client and server as a JSON object.\n\n" +
                   "📌 **Structure of a JWT:**\n" +
                   "1. **Header:** Algorithm used (e.g. HS256/RS256) & token type.\n" +
                   "2. **Payload:** Claims/User Data (e.g. `sub: shyam`, `exp: 1788539826`).\n" +
                   "3. **Signature:** Created by hashing Header + Payload with a secret key on the server.\n\n" +
                   "💡 **Why use JWT?** Statetess authentication! The server does not need to query session store on every request.";
        }

        if (lower.contains("javascript") || lower.contains("js")) {
            return "⚡ **JavaScript (JS)** is a high-level, multi-paradigm programming language that powers dynamic interactive web applications and Node.js backend servers.";
        }

        if (lower.contains("java") || lower.equals("j")) {
            return "☕ **Java** is a class-based, object-oriented programming language designed for platform-independent enterprise applications via JVM.";
        }

        if (lower.contains("spring") || lower.contains("boot")) {
            return "🌱 **Spring Boot** is a Java-based framework used to rapidly build production-ready backend microservices and Web APIs.";
        }

        if (lower.contains("websocket") || lower.contains("ws")) {
            return "🔌 **WebSockets** provide full-duplex, persistent real-time communication channels over a single TCP connection.";
        }

        if (lower.contains("hi") || lower.contains("hello") || lower.contains("hey")) {
            return "Hello! I am **Meta AI**. How can I help you today?";
        }

        return "💡 **Meta AI Assistant**\n\n" +
               "I am Meta AI, here to assist you with any questions! Ask me about JWT, Java, Spring Boot, WebSockets, JavaScript, or any topic!";
    }
}
