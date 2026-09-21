package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String jwtSecret,
        long jwtExpirationMinutes,
        String username,
        String password,
        String role,
        String bootstrapUsers) {

    @Override public String toString() { return "AuthProperties[redacted]"; }
}
