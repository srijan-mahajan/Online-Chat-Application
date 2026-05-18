package com.example.chat.config;

import com.example.chat.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    @Autowired
    public AuthHandshakeInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            String token = servletRequest.getServletRequest().getParameter("token");

            System.out.println("WebSocket Handshake Interceptor - verifying token: " + token);

            if (token == null || token.trim().isEmpty() || !jwtUtil.validateTokenOnly(token)) {
                System.out.println("WebSocket Handshake blocked: Missing or invalid JWT token");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            try {
                String username = jwtUtil.extractUsername(token);
                attributes.put("username", username);
                System.out.println("WebSocket Handshake approved for user: " + username);
                return true;
            } catch (Exception e) {
                System.out.println("WebSocket Handshake blocked: Failed to extract claims");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
        }
        
        response.setStatusCode(HttpStatus.BAD_REQUEST);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
