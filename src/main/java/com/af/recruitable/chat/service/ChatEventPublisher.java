package com.af.recruitable.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Publishes chat events to RabbitMQ so every application instance
 * can broadcast them to its locally-connected WebSocket clients.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange:chat.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.routing-key:chat.broadcast}")
    private String routingKey;

    /**
     * Publish a payload to be broadcast to a WebSocket destination via RabbitMQ.
     *
     * @param destination WebSocket topic destination (e.g. /topic/org.{orgId}.room.{roomId})
     * @param payload     the object to send (will be JSON-serialized)
     */
    public void publish(String destination, Object payload) {
        try {
            Map<String, Object> envelope = Map.of(
                    "destination", destination,
                    "payload", payload
            );
            rabbitTemplate.convertAndSend(exchange, routingKey, envelope);
            log.debug("Published to RabbitMQ: dest={}", destination);
        } catch (Exception e) {
            log.error("Failed to publish to RabbitMQ: dest={}, error={}", destination, e.getMessage(), e);
        }
    }
}

