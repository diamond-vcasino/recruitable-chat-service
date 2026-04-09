package com.af.recruitable.chat.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.messaging.StompSubProtocolHandler;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;

/**
 * Wires {@link ChatWebSocketErrorHandler} into the Spring-managed
 * {@link StompSubProtocolHandler} after all beans have been initialised.
 *
 * <p>We cannot set the error handler directly via {@code WebSocketMessageBrokerConfigurer}
 * because the interface does not expose that hook; instead we look up the
 * {@code subProtocolWebSocketHandler} bean after context refresh and inject
 * our custom handler into the STOMP sub-protocol handler it contains.</p>
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class WebSocketErrorHandlerConfigurer implements SmartInitializingSingleton {

    private final ChatWebSocketErrorHandler errorHandler;

    @Autowired(required = false)
    @Qualifier("subProtocolWebSocketHandler")
    private WebSocketHandler subProtocolWebSocketHandler;

    @Override
    public void afterSingletonsInstantiated() {
        if (!(subProtocolWebSocketHandler instanceof SubProtocolWebSocketHandler spHandler)) {
            log.warn("SubProtocolWebSocketHandler not found – custom STOMP error handler NOT registered");
            return;
        }

        spHandler.getProtocolHandlers().stream()
                .filter(h -> h instanceof StompSubProtocolHandler)
                .map(h -> (StompSubProtocolHandler) h)
                .findFirst()
                .ifPresentOrElse(
                        h -> {
                            h.setErrorHandler(errorHandler);
                            log.info("ChatWebSocketErrorHandler registered in StompSubProtocolHandler");
                        },
                        () -> log.warn("StompSubProtocolHandler not found – custom error handler NOT registered")
                );
    }
}

