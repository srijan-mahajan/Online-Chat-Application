package com.example.chat.controller;

import com.example.chat.model.User;
import com.example.chat.repository.UserRepository;
import com.example.chat.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    public AuthController(UserRepository userRepository, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        Map<String, String> response = new HashMap<>();

        if (request.getUsername() == null || request.getUsername().trim().isEmpty() ||
            request.getEmail() == null || request.getEmail().trim().isEmpty() ||
            request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            response.put("error", "All fields are required");
            return ResponseEntity.badRequest().body(response);
        }

        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase();
        String password = request.getPassword();

        if (userRepository.findById(username).isPresent()) {
            response.put("error", "Username '" + username + "' is already taken");
            return ResponseEntity.badRequest().body(response);
        }

        if (userRepository.findByEmail(email).isPresent()) {
            response.put("error", "Email is already registered");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            String hashedPassword = passwordEncoder.encode(password);
            User user = new User(username, email, hashedPassword, "OFFLINE");
            userRepository.save(user);

            response.put("message", "Registration successful!");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", "Failed to register user: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        Map<String, String> response = new HashMap<>();

        if (request.getEmail() == null || request.getEmail().trim().isEmpty() ||
            request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            response.put("error", "Email and password are required");
            return ResponseEntity.badRequest().body(response);
        }

        String email = request.getEmail().trim().toLowerCase();
        String password = request.getPassword();

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            response.put("error", "Invalid email or password");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        User user = userOpt.get();

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            response.put("error", "Invalid email or password");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        try {
            String token = jwtUtil.generateToken(user.getUsername());
            response.put("token", token);
            response.put("username", user.getUsername());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", "Failed to generate session token: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    public static class RegisterRequest {
        private String username;
        private String email;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class LoginRequest {
        private String email;
        private String password;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
