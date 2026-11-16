package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectRejectionRequest(
        @NotBlank(message = "La observacion del rechazo es obligatoria")
        @Size(max = 1000, message = "La observacion no puede superar 1000 caracteres")
        String observations) {
}
