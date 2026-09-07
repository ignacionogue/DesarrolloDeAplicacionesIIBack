package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;

public class CuadrillaRequest {

    @NotBlank(message = "El nombre de la cuadrilla es obligatorio")
    private String nombre;

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }
}
