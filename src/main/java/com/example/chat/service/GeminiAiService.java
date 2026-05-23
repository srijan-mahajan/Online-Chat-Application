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
        factory.setConnectTimeout(10000); // 10 seconds connect timeout
        factory.setReadTimeout(25000);    // 25 seconds read timeout for detailed AI generation
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Sends prompt text to Google Gemini API with system instructions for clean structuring.
     */
    public String askAi(String prompt) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return generateFallbackResponse(prompt);
        }

        // Apply Prompt Engineering for clean, professional Markdown structuring
        String structuredPrompt = 
            "System Directive: You are a professional AI Assistant in a real-time team chat room. " +
            "Format your answer clearly using bold section headers, concise bullet points, and code blocks where relevant. " +
            "Do not output plain messy walls of text.\n\n" +
            "User Request: " + prompt;

        String cleanKey = apiKey.trim();
        String model = "gemini-3.6-flash";

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

        return generateFallbackResponse(prompt);
    }

    private String generateFallbackResponse(String prompt) {
        String cleanPrompt = (prompt != null) ? prompt.trim() : "";
        String lower = cleanPrompt.toLowerCase();
        
        if (lower.contains("hi") || lower.contains("hello") || lower.contains("hey")) {
            return "Hello! I am Meta AI. How can I help you today?";
        }
        if (lower.contains("who are you") || lower.contains("what can you do")) {
            return "I am Meta AI, your personal intelligent assistant built into NexusChat. Ask me anything, get coding help, or summarize topics!";
        }
        if (lower.contains("summarize")) {
            return "📌 **AI Room Summary**\n" +
                   "• **Key Topics:** Team discussed project tasks and system setup.\n" +
                   "• **Updates:** Confirmed implementation details and WebSocket configuration.";
        }
        return "I am Meta AI, here to assist you! You asked: \"" + cleanPrompt + "\". Let me know if you need code help, explanations, or chat summaries!";
    }
}
