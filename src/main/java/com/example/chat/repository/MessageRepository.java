package com.example.chat.repository;

import com.example.chat.model.Message;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {

    // Fetch room chat history sorted chronologically
    List<Message> findByRoomCodeOrderByTimestampAsc(String roomCode);

    // Fetch direct private messages between two specific users, sorted chronologically
    @Query(value = "{$or: [{'sender': ?0, 'recipient': ?1}, {'sender': ?1, 'recipient': ?0}]}", sort = "{'timestamp': 1}")
    List<Message> findDirectMessages(String user1, String user2);
}
