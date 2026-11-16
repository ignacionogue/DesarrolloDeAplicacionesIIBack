package com.example.demo.security;

import com.example.demo.config.AuthProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceConfigurationTest {

    @Test
    void blankIssuerFailsFast() {
        var properties = new AuthProperties(
                "test-secret-for-jwt-signing-with-at-least-32-bytes",
                " ", 30, "", "", "", "");
        var service = new JwtService(properties);

        var failure = assertThrows(IllegalStateException.class, service::validateConfiguration);

        assertEquals("JWT_ISSUER no puede estar vacio.", failure.getMessage());
    }

    @Test
    void nullIssuerFailsFast() {
        var properties = new AuthProperties(
                "test-secret-for-jwt-signing-with-at-least-32-bytes",
                null, 30, "", "", "", "");
        var service = new JwtService(properties);

        assertThrows(IllegalStateException.class, service::validateConfiguration);
    }
}
