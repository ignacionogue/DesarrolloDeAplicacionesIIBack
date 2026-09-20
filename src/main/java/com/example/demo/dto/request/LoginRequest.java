package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @jakarta.validation.constraints.Size(max = 100) String username,
        @NotBlank @jakarta.validation.constraints.Size(max = 72) String password) {
}
