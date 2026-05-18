package com.example.chat.config;

import com.example.chat.handler.ChatWebSocketHandler;
import com.example.chat.repository.MessageRepository;
import com.example.chat.repository.UserRepository;
import com.example.chat.service.GeminiAiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final GeminiAiService aiService;
    private final AuthHandshakeInterceptor authHandshakeInterceptor;

    @Autowired
    public WebSocketConfig(UserRepository userRepository, MessageRepository messageRepository,
                           GeminiAiService aiService, AuthHandshakeInterceptor authHandshakeInterceptor) {
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.aiService = aiService;
        this.authHandshakeInterceptor = authHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler(), "/chat")
                .addInterceptors(authHandshakeInterceptor)
                .setAllowedOrigins("*");
    }

    @Bean
    public ChatWebSocketHandler chatWebSocketHandler() {
        return new ChatWebSocketHandler(userRepository, messageRepository, aiService);
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(10 * 1024 * 1024);
        container.setMaxBinaryMessageBufferSize(10 * 1024 * 1024);
        return container;
    }
}
