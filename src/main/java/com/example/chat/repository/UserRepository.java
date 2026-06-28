package com.example.chat.repository;

import com.example.chat.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    
    // Find user by email, fully compatible with MongoRepository
    Optional<User> findByEmail(String email);
}
