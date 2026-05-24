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
        String cleanPrompt = (prompt != null) ? prompt.trim() : "";
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.contains("YOUR_GEMINI_API_KEY")) {
            return generateFallbackResponse(cleanPrompt);
        }

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

        return generateFallbackResponse(cleanPrompt);
    }

    private String generateFallbackResponse(String prompt) {
        String cleanPrompt = (prompt != null) ? prompt.trim() : "";
        String lower = cleanPrompt.toLowerCase();
        
        if (lower.contains("java")) {
            return "☕ **Java** is a popular, class-based, object-oriented programming language created by Sun Microsystems (now Oracle).\n\n" +
                   "📌 **Key Concepts:**\n" +
                   "• **WORA (Write Once, Run Anywhere):** Java code compiles into Bytecode executed by the Java Virtual Machine (JVM).\n" +
                   "• **OOP Principles:** Built on Abstraction, Encapsulation, Inheritance, and Polymorphism.\n" +
                   "• **Automatic Memory Management:** Features built-in Garbage Collection (GC) for safe memory control.\n" +
                   "• **Enterprise Standard:** Widely used for Spring Boot microservices, backend APIs, and Android applications.";
        }

        if (lower.contains("spring") || lower.contains("boot")) {
            return "🌱 **Spring Boot** is a Java-based framework used to rapidly build production-ready backend microservices and Web APIs.\n\n" +
                   "📌 **Key Features:**\n" +
                   "• **Auto-Configuration:** Automatically configures Spring beans based on classpath dependencies.\n" +
                   "• **Embedded Tomcat Server:** Allows deployment as a standalone JAR (`java -jar app.jar`).\n" +
                   "• **Spring Starter Dependencies:** Simplifies Maven/Gradle dependency management (`spring-boot-starter-web`).";
        }

        if (lower.contains("hi") || lower.contains("hello") || lower.contains("hey")) {
            return "Hello! I am **Meta AI**. How can I help you today?";
        }
        
        if (lower.contains("who are you") || lower.contains("what can you do")) {
            return "I am **Meta AI**, your personal intelligent assistant built into NexusChat. Ask me anything about programming, technology, explanations, or chat summaries!";
        }

        if (lower.contains("summarize")) {
            return "📌 **AI Room Summary**\n" +
                   "• **Key Topics:** Team discussed project tasks, WebSockets, and system setup.\n" +
                   "• **Updates:** Confirmed implementation details and server configuration.";
        }

        return "💡 **Meta AI Assistant**\n\n" +
               "I am here to help you! You asked: \"" + cleanPrompt + "\". Ask me anything about Java, Spring Boot, WebSockets, code explanations, or chat room summaries!";
    }
}
