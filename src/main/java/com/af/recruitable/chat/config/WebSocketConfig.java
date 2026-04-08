package com.af.recruitable.chat.config;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final WebSocketAuthInterceptor authInterceptor;
    private final ObjectMapper objectMapper;
    @Value("${app.websocket.broker-relay-enabled:false}")
    private boolean brokerRelayEnabled;
    @Value("${spring.rabbitmq.host:localhost}")
    private String rabbitHost;
    @Value("${app.websocket.stomp-relay-port:61613}")
    private int stompRelayPort;
    @Value("${spring.rabbitmq.username:guest}")
    private String rabbitUser;
    @Value("${spring.rabbitmq.password:guest}")
    private String rabbitPass;
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
        log.info("WebSocket STOMP endpoint registered at /ws");
    }
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        if (brokerRelayEnabled) {
            registry.enableStompBrokerRelay("/topic", "/queue")
                    .setRelayHost(rabbitHost).setRelayPort(stompRelayPort)
                    .setClientLogin(rabbitUser).setClientPasscode(rabbitPass)
                    .setSystemLogin(rabbitUser).setSystemPasscode(rabbitPass);
            log.info("STOMP broker relay enabled -> {}:{}", rabbitHost, stompRelayPort);
        } else {
            registry.enableSimpleBroker("/topic", "/queue");
            log.info("Simple in-memory STOMP broker enabled");
        }
    }
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }

    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        DefaultContentTypeResolver resolver = new DefaultContentTypeResolver();
        resolver.setDefaultMimeType(MimeTypeUtils.APPLICATION_JSON);
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        converter.setContentTypeResolver(resolver);
        messageConverters.add(converter);
        // return false so the default converters are NOT added (we use ours)
        return false;
    }
}
