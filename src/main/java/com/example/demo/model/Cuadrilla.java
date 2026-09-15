package com.example.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Equipo de trabajadores que puede asignarse a una OrdenTrabajo (punto 8).
 * Deliberadamente minima: NO modela empleados, horarios ni vacaciones.
 * Los indicadores de carga/disponibilidad mencionados en la consigna quedan
 * para una fase futura (se calculan sobre OrdenTrabajo.cuadrilla).
 */
@Entity
@Table(name = "cuadrilla")
public class Cuadrilla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String nombre;

    protected Cuadrilla() {
        // JPA
    }

    public Cuadrilla(String nombre) {
        this.nombre = nombre;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }
}
