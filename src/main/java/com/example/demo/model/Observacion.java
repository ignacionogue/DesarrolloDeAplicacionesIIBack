package com.example.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "observacion")
public class Observacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "orden_trabajo_id")
    private OrdenTrabajo ordenTrabajo;

    @Column(nullable = false, length = 1000)
    private String texto;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    protected Observacion() {
        // JPA
    }

    public Observacion(OrdenTrabajo ordenTrabajo, String texto) {
        this.ordenTrabajo = ordenTrabajo;
        this.texto = texto;
    }

    public Long getId() {
        return id;
    }

    public OrdenTrabajo getOrdenTrabajo() {
        return ordenTrabajo;
    }

    public String getTexto() {
        return texto;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }
}
