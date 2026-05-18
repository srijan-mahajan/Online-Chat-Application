package com.example.chat.controller;

import com.example.chat.model.Message;
import com.example.chat.repository.MessageRepository;
import com.example.chat.service.GeminiAiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final MessageRepository messageRepository;
    private final GeminiAiService aiService;

    @Autowired
    public AiController(MessageRepository messageRepository, GeminiAiService aiService) {
        this.messageRepository = messageRepository;
        this.aiService = aiService;
    }

    @GetMapping("/summarize-room")
    public ResponseEntity<Map<String, String>> summarizeRoom(@RequestParam String roomCode) {
        if (roomCode == null || roomCode.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Room code is required"));
        }

        String formattedCode = roomCode.trim().toUpperCase();
        List<Message> history = messageRepository.findByRoomCodeOrderByTimestampAsc(formattedCode);

        if (history.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                "summary", "No messages found in room #" + formattedCode + " to summarize.",
                "roomCode", formattedCode
            ));
        }

        int startIndex = Math.max(0, history.size() - 20);
        List<Message> recentMessages = history.subList(startIndex, history.size());

        String formattedLog = recentMessages.stream()
                .map(m -> m.getSender() + ": " + (m.getContent() != null ? m.getContent() : "[Media Shared]"))
                .collect(Collectors.joining("\n"));

        String prompt = "You are an AI Chat Summarizer. Analyze the following group chat log and generate a clean, structured summary using this exact format:\n\n" +
                "📌 **Room #" + formattedCode + " Summary**\n" +
                "• **Key Topics Discussed:** [Brief summary bullet]\n" +
                "• **Decisions & Updates:** [Brief summary bullet]\n" +
                "• **Action Items:** [Brief summary bullet]\n\n" +
                "Group Chat Log:\n" + formattedLog;

        String aiSummary = aiService.askAi(prompt);

        return ResponseEntity.ok(Map.of(
            "summary", aiSummary,
            "roomCode", formattedCode
        ));
    }
}
