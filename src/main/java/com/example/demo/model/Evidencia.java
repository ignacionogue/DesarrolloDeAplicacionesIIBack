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

/**
 * Evidencia/fotografia asociada a una OrdenTrabajo (punto 10).
 * NO guarda el binario: 'storageReference' es una referencia opaca que el
 * mecanismo de storage concreto (Azure Blob Storage u otro, a definir por
 * el integrante de Azure) sabe resolver. Ver integration/EvidenceStoragePort.
 */
@Entity
@Table(name = "evidencia")
public class Evidencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "orden_trabajo_id")
    private OrdenTrabajo ordenTrabajo;

    @Column(nullable = false, length = 500)
    private String storageReference;

    @Column(length = 500)
    private String descripcion;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    protected Evidencia() {
        // JPA
    }

    public Evidencia(OrdenTrabajo ordenTrabajo, String storageReference, String descripcion) {
        this.ordenTrabajo = ordenTrabajo;
        this.storageReference = storageReference;
        this.descripcion = descripcion;
    }

    public Long getId() {
        return id;
    }

    public OrdenTrabajo getOrdenTrabajo() {
        return ordenTrabajo;
    }

    public String getStorageReference() {
        return storageReference;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }
}
