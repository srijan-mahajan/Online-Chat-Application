package com.example.chat.handler;

import com.example.chat.model.ChatMessage;
import com.example.chat.model.Message;
import com.example.chat.model.User;
import com.example.chat.repository.MessageRepository;
import com.example.chat.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    // Map of WebSocketSession ID to Nickname
    private final Map<String, String> sessionToNickname = new ConcurrentHashMap<>();
    // Map of Nickname to WebSocketSession
    private final Map<String, WebSocketSession> nicknameToSession = new ConcurrentHashMap<>();

    // Map of Room Code to Set of WebSocket Sessions
    private final Map<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(UserRepository userRepository, MessageRepository messageRepository) {
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        resetAllUsersToOffline();
    }

    private void resetAllUsersToOffline() {
        try {
            List<User> users = userRepository.findAll();
            for (User u : users) {
                u.setStatus("OFFLINE");
            }
            userRepository.saveAll(users);
            System.out.println("Initialized database: reset " + users.size() + " users to OFFLINE.");
        } catch (Exception e) {
            System.err.println("Could not reset user statuses on startup: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Retrieve the authenticated username from the handshake session attributes
        String username = (String) session.getAttributes().get("username");

        if (username == null) {
            System.out.println("Closing session: Missing authenticated username in handshake attributes");
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        System.out.println("WebSocket session opened and authenticated for user: " + username + " (session ID: " + session.getId() + ")");

        // Register session mappings
        sessionToNickname.put(session.getId(), username);
        nicknameToSession.put(username, session);

        // Update database user status to ONLINE
        try {
            userRepository.findById(username).ifPresent(user -> {
                user.setStatus("ONLINE");
                userRepository.save(user);
            });
        } catch (Exception e) {
            System.err.println("Failed to update ONLINE status for user " + username + ": " + e.getMessage());
        }

        // Acknowledge connection
        ChatMessage response = new ChatMessage("CONNECTED", "SERVER", "Welcome to NexusChat, " + username + "!");
        response.setSender(username);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));

        // Broadcast updated user lists
        broadcastGlobalUserList();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        ChatMessage chatMessage = objectMapper.readValue(payload, ChatMessage.class);

        System.out.println("Received message: " + chatMessage.getType() + " from session: " + session.getId());

        switch (chatMessage.getType()) {
            case "JOIN_ROOM":
                handleJoinRoom(session, chatMessage);
                break;
            case "LEAVE_ROOM":
                handleLeaveRoom(session, chatMessage);
                break;
            case "SEND_ROOM_MSG":
                handleSendRoomMsg(session, chatMessage);
                break;
            case "SEND_PRIVATE_MSG":
                handleSendPrivateMsg(session, chatMessage);
                break;
            case "GET_HISTORY":
                handleGetHistory(session, chatMessage);
                break;
            default:
                sendError(session, "Unknown message type: " + chatMessage.getType());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String nickname = sessionToNickname.remove(session.getId());
        if (nickname != null) {
            nicknameToSession.remove(nickname);
            System.out.println("User disconnected: " + nickname);

            // Update user status to OFFLINE in H2 DB
            try {
                userRepository.findById(nickname).ifPresent(user -> {
                    user.setStatus("OFFLINE");
                    userRepository.save(user);
                });
            } catch (Exception e) {
                System.err.println("Failed to update status for disconnected user: " + e.getMessage());
            }

            // Remove from rooms
            Iterator<Map.Entry<String, Set<WebSocketSession>>> iterator = roomSessions.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Set<WebSocketSession>> entry = iterator.next();
                String roomCode = entry.getKey();
                Set<WebSocketSession> sessions = entry.getValue();

                if (sessions.remove(session)) {
                    if (sessions.isEmpty()) {
                        iterator.remove();
                    } else {
                        broadcastRoomUpdate(roomCode);
                    }
                }
            }

            // Notify everyone of updated user list (online & offline status)
            broadcastGlobalUserList();
        }
    }

    private void handleJoinRoom(WebSocketSession session, ChatMessage chatMessage) throws IOException {
        String nickname = sessionToNickname.get(session.getId());
        if (nickname == null) {
            sendError(session, "You must register a nickname before joining a room");
            return;
        }

        String roomCode = chatMessage.getRoomCode();
        if (roomCode == null || roomCode.trim().isEmpty()) {
            sendError(session, "Room code cannot be empty");
            return;
        }

        roomCode = roomCode.trim().toUpperCase();

        roomSessions.putIfAbsent(roomCode, new CopyOnWriteArraySet<>());
        roomSessions.get(roomCode).add(session);

        System.out.println("User " + nickname + " joined room: " + roomCode);

        // Send a joining notification message to the room
        ChatMessage systemMsg = new ChatMessage("ROOM_JOIN_NOTIFY", "SERVER", nickname + " joined the room");
        systemMsg.setRoomCode(roomCode);
        broadcastToRoom(roomCode, systemMsg);

        // Broadcast the updated list of users in this room
        broadcastRoomUpdate(roomCode);
    }

    private void handleLeaveRoom(WebSocketSession session, ChatMessage chatMessage) throws IOException {
        String nickname = sessionToNickname.get(session.getId());
        String roomCode = chatMessage.getRoomCode();

        if (roomCode != null && nickname != null) {
            roomCode = roomCode.trim().toUpperCase();
            Set<WebSocketSession> sessions = roomSessions.get(roomCode);
            if (sessions != null && sessions.remove(session)) {
                if (sessions.isEmpty()) {
                    roomSessions.remove(roomCode);
                } else {
                    ChatMessage systemMsg = new ChatMessage("ROOM_LEAVE_NOTIFY", "SERVER", nickname + " left the room");
                    systemMsg.setRoomCode(roomCode);
                    broadcastToRoom(roomCode, systemMsg);
                    broadcastRoomUpdate(roomCode);
                }
            }
        }
    }

    private void handleSendRoomMsg(WebSocketSession session, ChatMessage chatMessage) throws IOException {
        String nickname = sessionToNickname.get(session.getId());
        if (nickname == null) {
            sendError(session, "Unauthorized");
            return;
        }

        String roomCode = chatMessage.getRoomCode();
        if (roomCode == null) {
            sendError(session, "Room code required");
            return;
        }

        roomCode = roomCode.trim().toUpperCase();
        Set<WebSocketSession> sessions = roomSessions.get(roomCode);
        if (sessions == null || !sessions.contains(session)) {
            sendError(session, "You are not a member of room: " + roomCode);
            return;
        }

        // 1. Save room message to H2 Database
        Message dbMsg = new Message(
                nickname,
                null,
                roomCode,
                chatMessage.getContent(),
                chatMessage.getMediaData(),
                chatMessage.getMediaName(),
                chatMessage.getMediaType()
        );
        try {
            messageRepository.save(dbMsg);
        } catch (Exception e) {
            System.err.println("Failed to persist room message to database: " + e.getMessage());
        }

        // 2. Broadcast message to online members
        ChatMessage forwardMsg = new ChatMessage("CHAT_ROOM", nickname, chatMessage.getContent());
        forwardMsg.setRoomCode(roomCode);
        forwardMsg.setMediaData(chatMessage.getMediaData());
        forwardMsg.setMediaName(chatMessage.getMediaName());
        forwardMsg.setMediaType(chatMessage.getMediaType());
        forwardMsg.setTimestamp(dbMsg.getTimestamp());

        broadcastToRoom(roomCode, forwardMsg);
    }

    private void handleSendPrivateMsg(WebSocketSession session, ChatMessage chatMessage) throws IOException {
        String senderNickname = sessionToNickname.get(session.getId());
        if (senderNickname == null) {
            sendError(session, "Unauthorized");
            return;
        }

        String recipient = chatMessage.getRecipient();
        if (recipient == null || recipient.trim().isEmpty()) {
            sendError(session, "Recipient nickname required");
            return;
        }

        recipient = recipient.trim();

        // 1. ALWAYS persist message to the H2 database (so they can read it offline)
        Message dbMsg = new Message(
                senderNickname,
                recipient,
                null,
                chatMessage.getContent(),
                chatMessage.getMediaData(),
                chatMessage.getMediaName(),
                chatMessage.getMediaType()
        );
        try {
            messageRepository.save(dbMsg);
        } catch (Exception e) {
            System.err.println("Failed to persist direct message to database: " + e.getMessage());
        }

        // 2. If the recipient is ONLINE, forward the message immediately
        WebSocketSession recipientSession = nicknameToSession.get(recipient);
        if (recipientSession != null && recipientSession.isOpen()) {
            ChatMessage forwardMsg = new ChatMessage("CHAT_PRIVATE", senderNickname, chatMessage.getContent());
            forwardMsg.setRecipient(recipient);
            forwardMsg.setMediaData(chatMessage.getMediaData());
            forwardMsg.setMediaName(chatMessage.getMediaName());
            forwardMsg.setMediaType(chatMessage.getMediaType());
            forwardMsg.setTimestamp(dbMsg.getTimestamp());

            recipientSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(forwardMsg)));
        }

        // 3. Send confirmation back to the sender
        ChatMessage confirmMsg = new ChatMessage("CHAT_PRIVATE", senderNickname, chatMessage.getContent());
        confirmMsg.setRecipient(recipient);
        confirmMsg.setMediaData(chatMessage.getMediaData());
        confirmMsg.setMediaName(chatMessage.getMediaName());
        confirmMsg.setMediaType(chatMessage.getMediaType());
        confirmMsg.setTimestamp(dbMsg.getTimestamp());
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(confirmMsg)));
    }

    private void handleGetHistory(WebSocketSession session, ChatMessage chatMessage) throws IOException {
        String requester = sessionToNickname.get(session.getId());
        if (requester == null) {
            sendError(session, "Unauthorized");
            return;
        }

        List<Message> history;
        String roomCode = chatMessage.getRoomCode();
        String recipient = chatMessage.getRecipient();

        try {
            if (roomCode != null && !roomCode.trim().isEmpty()) {
                roomCode = roomCode.trim().toUpperCase();
                history = messageRepository.findByRoomCodeOrderByTimestampAsc(roomCode);
            } else if (recipient != null && !recipient.trim().isEmpty()) {
                recipient = recipient.trim();
                history = messageRepository.findDirectMessages(requester, recipient);
            } else {
                sendError(session, "Room code or recipient nickname required for history lookup");
                return;
            }

            // Convert to ChatMessage Transfer Objects
            List<ChatMessage> chatHistory = history.stream().map(m -> {
                String type = (m.getRoomCode() != null) ? "CHAT_ROOM" : "CHAT_PRIVATE";
                ChatMessage msg = new ChatMessage(type, m.getSender(), m.getContent());
                msg.setRoomCode(m.getRoomCode());
                msg.setRecipient(m.getRecipient());
                msg.setMediaData(m.getMediaData());
                msg.setMediaName(m.getMediaName());
                msg.setMediaType(m.getMediaType());
                msg.setTimestamp(m.getTimestamp());
                return msg;
            }).collect(Collectors.toList());

            // Send historical records back to request owner
            ChatMessage historyResponse = new ChatMessage("CHAT_HISTORY", "SERVER", "");
            historyResponse.setRoomCode(roomCode);
            historyResponse.setRecipient(recipient);
            historyResponse.setHistory(chatHistory);

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(historyResponse)));

        } catch (Exception e) {
            sendError(session, "Failed to load chat logs from database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void broadcastToRoom(String roomCode, ChatMessage message) throws IOException {
        Set<WebSocketSession> sessions = roomSessions.get(roomCode);
        if (sessions != null) {
            String json = objectMapper.writeValueAsString(message);
            TextMessage textMessage = new TextMessage(json);
            for (WebSocketSession s : sessions) {
                if (s.isOpen()) {
                    s.sendMessage(textMessage);
                }
            }
        }
    }

    private void broadcastRoomUpdate(String roomCode) throws IOException {
        Set<WebSocketSession> sessions = roomSessions.get(roomCode);
        if (sessions != null) {
            Set<String> usersInRoom = sessions.stream()
                    .map(s -> sessionToNickname.get(s.getId()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            ChatMessage updateMsg = new ChatMessage("ROOM_UPDATE", "SERVER", "");
            updateMsg.setRoomCode(roomCode);
            updateMsg.setUsers(usersInRoom);

            broadcastToRoom(roomCode, updateMsg);
        }
    }

    private void broadcastGlobalUserList() throws IOException {
        try {
            List<User> allUsers = userRepository.findAll();

            ChatMessage updateMsg = new ChatMessage("USER_LIST", "SERVER", "");
            updateMsg.setUserProfiles(allUsers);

            String json = objectMapper.writeValueAsString(updateMsg);
            TextMessage textMessage = new TextMessage(json);

            for (WebSocketSession session : nicknameToSession.values()) {
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to broadcast global user list: " + e.getMessage());
        }
    }

    private void sendError(WebSocketSession session, String errorMsg) throws IOException {
        ChatMessage error = new ChatMessage("ERROR", "SERVER", errorMsg);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
    }
}
