package com.example.demo.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final com.example.demo.repository.AppUserRepository users;

    public JwtAuthenticationFilter(JwtService jwtService, com.example.demo.repository.AppUserRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parse(authorization.substring(7));
                String role = claims.get("role", String.class);
                var version = claims.get("ver", Long.class);
                var userId = claims.get("uid", Long.class);
                if (claims.getSubject() != null && !claims.getSubject().isBlank()
                        && role != null && JwtService.ROLES.contains(role) && claims.getExpiration() != null
                        && version != null && userId != null) {
                    users.findByUsername(claims.getSubject())
                            .filter(user -> user.isEnabled() && user.getId().equals(userId)
                                    && user.getRole().equals(role) && user.getTokenVersion() == version)
                            .ifPresent(user -> {
                                var authentication = new UsernamePasswordAuthenticationToken(
                                        user.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
                                SecurityContextHolder.getContext().setAuthentication(authentication);
                            });
                }
            } catch (JwtException | IllegalArgumentException exception) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
