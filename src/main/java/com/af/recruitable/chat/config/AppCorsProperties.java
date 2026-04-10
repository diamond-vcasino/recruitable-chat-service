package com.af.recruitable.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.cors")
@Validated
@Data
public class AppCorsProperties {
    /**
     * If true, allows all origins via allowed origin patterns.
     */
    private boolean allowAllOrigins = false;

    /**
     * Explicit allow-list used when allowAllOrigins is false.
     */
    private List<String> allowedOrigins = new ArrayList<>();

    /**
     * CORS preflight max-age in seconds.
     */
    private long maxAge = 3600;
}

