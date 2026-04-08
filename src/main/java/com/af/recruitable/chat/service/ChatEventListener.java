package com.af.recruitable.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Listens for chat events from RabbitMQ and broadcasts them
 * to WebSocket clients via the in-memory STOMP broker.
 * <p>
 * Each application instance creates its own anonymous queue,
 * so every instance receives every event and can relay it
 * to its locally-connected WebSocket sessions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @RabbitListener(queues = "#{chatEventsQueue.name}")
    public void onChatEvent(Map<String, Object> envelope) {
        try {
            String destination = (String) envelope.get("destination");
            Object payload = envelope.get("payload");

            if (destination == null || payload == null) {
                log.warn("Received malformed chat event: {}", envelope);
                return;
            }

            messagingTemplate.convertAndSend(destination, payload);
            log.debug("Relayed RabbitMQ event to WS: dest={}", destination);
        } catch (Exception e) {
            log.error("Error processing RabbitMQ chat event: {}", e.getMessage(), e);
        }
    }
}

