package com.example.demo.config;

import com.example.demo.security.JwtAuthenticationFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpMethod;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> writeError(response, 401,
                                "Se requiere un token valido", "UNAUTHORIZED"))
                        .accessDeniedHandler((request, response, ex) -> writeError(response, 403,
                                "El rol no tiene permiso para esta operacion", "FORBIDDEN")))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/login", "/api/health", "/actuator/health").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/public-works/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/public-works/projects", "/api/public-works/work-orders",
                                "/api/public-works/crews", "/api/public-works/street-closures").hasRole("PERSONAL_OBRAS")
                        .requestMatchers(HttpMethod.PUT, "/api/public-works/projects/{id}",
                                "/api/public-works/work-orders/{id}").hasRole("PERSONAL_OBRAS")
                        .requestMatchers(HttpMethod.PATCH, "/api/public-works/projects/{id}/submit-approval").hasRole("PERSONAL_OBRAS")
                        .requestMatchers(HttpMethod.PATCH, "/api/public-works/projects/{id}/approve",
                                "/api/public-works/projects/{id}/reject").hasRole("RESPONSABLE_AUTORIZADO")
                        .requestMatchers(HttpMethod.PATCH, "/api/public-works/work-orders/{id}/schedule",
                                "/api/public-works/work-orders/{id}/start", "/api/public-works/work-orders/{id}/pause").hasRole("JEFE_CUADRILLA")
                        .requestMatchers(HttpMethod.PATCH, "/api/public-works/work-orders/{id}/complete").hasRole("OPERARIO_CONTRATISTA")
                        .requestMatchers(HttpMethod.PATCH, "/api/public-works/work-orders/{id}/validate").hasRole("INSPECTOR_OBRA")
                        .requestMatchers("/api/**").denyAll()
                        .anyRequest().permitAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private static void writeError(HttpServletResponse response, int status, String message, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        // Both strings are fixed server constants, never request input.
        response.getWriter().write("{\"message\":\"" + message + "\",\"code\":\"" + code + "\",\"details\":[]}");
    }
}
