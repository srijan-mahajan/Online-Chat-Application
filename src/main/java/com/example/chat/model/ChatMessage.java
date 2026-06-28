package com.example.chat.model;

import java.util.List;
import java.util.Set;

public class ChatMessage {
    private String type;
    private String sender;
    private String recipient;
    private String roomCode;
    private String content;
    private String timestamp;
    private Set<String> users; // Active users in a room
    private List<User> userProfiles; // Online/Offline user database profiles
    private List<ChatMessage> history; // Historical message list for loading logs

    // Media properties
    private String mediaData; // Base64 data URL
    private String mediaName; // Filename
    private String mediaType; // Mime type

    // Default constructor for Jackson JSON deserialization
    public ChatMessage() {
    }

    public ChatMessage(String type, String sender, String content) {
        this.type = type;
        this.sender = sender;
        this.content = content;
        this.timestamp = java.time.Instant.now().toString();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public void setRoomCode(String roomCode) {
        this.roomCode = roomCode;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Set<String> getUsers() {
        return users;
    }

    public void setUsers(Set<String> users) {
        this.users = users;
    }

    public List<User> getUserProfiles() {
        return userProfiles;
    }

    public void setUserProfiles(List<User> userProfiles) {
        this.userProfiles = userProfiles;
    }

    public List<ChatMessage> getHistory() {
        return history;
    }

    public void setHistory(List<ChatMessage> history) {
        this.history = history;
    }

    public String getMediaData() {
        return mediaData;
    }

    public void setMediaData(String mediaData) {
        this.mediaData = mediaData;
    }

    public String getMediaName() {
        return mediaName;
    }

    public void setMediaName(String mediaName) {
        this.mediaName = mediaName;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }
}
