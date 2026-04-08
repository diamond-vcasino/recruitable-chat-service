package com.af.recruitable.chat.security;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
@ConfigurationProperties(prefix = "app.jwt")
@Validated
@Data
public class JwtProperties {
    @NotBlank
    private String secret;
    private String issuer = "recruitable-api";
    private String audience = "recruitable-client";
    private long clockSkewSeconds = 300;
}
