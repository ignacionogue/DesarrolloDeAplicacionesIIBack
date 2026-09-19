package com.example.demo.controller;

import com.example.demo.config.AuthProperties;
import com.example.demo.dto.request.LoginRequest;
import com.example.demo.dto.response.LoginResponse;
import com.example.demo.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.codec.Utf8;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthProperties properties;
    private final JwtService jwtService;

    public AuthController(AuthProperties properties, JwtService jwtService) {
        this.properties = properties;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        boolean validUser = MessageDigest.isEqual(Utf8.encode(request.username()), Utf8.encode(properties.username()));
        boolean validPassword = MessageDigest.isEqual(Utf8.encode(request.password()), Utf8.encode(properties.password()));
        if (!validUser || !validPassword) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(new LoginResponse(
                jwtService.issueToken(properties.username(), properties.role()),
                properties.username(), properties.role()));
    }
}
