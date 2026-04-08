package com.af.recruitable.chat.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class RabbitConfig {

    @Value("${app.rabbitmq.exchange:chat.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.queue:chat.events}")
    private String queueName;

    @Value("${app.rabbitmq.routing-key:chat.broadcast}")
    private String routingKey;

    // ── Exchange ─────────────────────────────────────────────────────────────────

    @Bean
    public TopicExchange chatExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    // ── Queue (anonymous per instance for fan-out to all nodes) ──────────────────

    @Bean
    public Queue chatEventsQueue() {
        // Anonymous queue: exclusive, auto-delete → each app instance gets its own
        return new AnonymousQueue();
    }

    // ── Binding ──────────────────────────────────────────────────────────────────

    @Bean
    public Binding chatEventsBinding(Queue chatEventsQueue, TopicExchange chatExchange) {
        return BindingBuilder.bind(chatEventsQueue).to(chatExchange).with("chat.#");
    }

    // ── JSON message converter ───────────────────────────────────────────────────

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}

