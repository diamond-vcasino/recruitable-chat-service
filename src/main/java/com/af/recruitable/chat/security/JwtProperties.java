package com.af.recruitable.chat.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@ConfigurationProperties(prefix = "app.jwt")
@Validated
@Data
public class JwtProperties {
    @NotBlank
    @Size(min = 32, message = "JWT secret must be at least 32 characters (256 bits). "
            + "For HS512, use 64+ characters (512 bits).")
    private String secret;
    private String issuer = "recruitable-api";
    private String audience = "recruitable-client";
    private long clockSkewSeconds = 300;
}
