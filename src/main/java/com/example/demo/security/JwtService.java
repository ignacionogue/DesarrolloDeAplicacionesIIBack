package com.example.demo.security;

import com.example.demo.config.AuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {

    public static final java.util.Set<String> ROLES = java.util.Set.of("PERSONAL_OBRAS", "RESPONSABLE_AUTORIZADO",
            "JEFE_CUADRILLA", "OPERARIO_CONTRATISTA", "INSPECTOR_OBRA", "INGENIERO_ARQUITECTO");

    private final AuthProperties properties;
    private SecretKey signingKey;

    public JwtService(AuthProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validateConfiguration() {
        if (properties.jwtSecret() == null || properties.jwtSecret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 caracteres.");
        }
        if (isBlank(properties.username()) || isBlank(properties.password()) || isBlank(properties.role())) {
            throw new IllegalStateException("AUTH_USERNAME, AUTH_PASSWORD y AUTH_ROLE son obligatorios.");
        }
        if (!ROLES.contains(properties.role()) || properties.jwtExpirationMinutes() <= 0) {
            throw new IllegalStateException("AUTH_ROLE debe ser un rol conocido y JWT_EXPIRATION_MINUTES debe ser positivo.");
        }
        signingKey = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public String issueToken(String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.jwtExpirationMinutes(), ChronoUnit.MINUTES)))
                .signWith(signingKey)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }
}
