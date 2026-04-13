package com.af.recruitable.chat.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8082}")
    private int serverPort;

    @Bean
    public OpenAPI chatServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Recruitable Chat Service API")
                        .version("1.0")
                        .description("""
                                Real-time chat microservice for the Recruitable platform.
                                
                                ## Authentication
                                All REST endpoints require a valid JWT access token issued by the main `recruitable-api` service.
                                Send it as:
                                - `Authorization: Bearer <token>` header, **or**
                                - `rct_at` cookie (set by the main app on login)
                                
                                ## WebSocket (STOMP)
                                Connect to `/ws` with SockJS. Pass the JWT in the STOMP CONNECT frame `Authorization` header.
                                
                                ## JSON Format
                                All request/response bodies use **snake_case** property names (e.g. `room_id`, `sender_name`, `member_user_ids`).
                                """)
                        .contact(new Contact()
                                .name("Recruitable Team")
                                .url("https://recruit.auroraforge.com.np")))
                .servers(List.of(
                        new Server().url("http://localhost:" + serverPort).description("Local dev")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT access token from recruitable-api login")));
    }
}

