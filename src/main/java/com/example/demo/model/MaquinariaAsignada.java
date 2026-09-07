package com.example.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "maquinaria_asignada")
public class MaquinariaAsignada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "orden_trabajo_id")
    private OrdenTrabajo ordenTrabajo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "maquinaria_id")
    private Maquinaria maquinaria;

    @Column(nullable = false)
    private Integer horasAsignadas;

    protected MaquinariaAsignada() {
        // JPA
    }

    public MaquinariaAsignada(OrdenTrabajo ordenTrabajo, Maquinaria maquinaria, Integer horasAsignadas) {
        this.ordenTrabajo = ordenTrabajo;
        this.maquinaria = maquinaria;
        this.horasAsignadas = horasAsignadas;
    }

    public Long getId() {
        return id;
    }

    public OrdenTrabajo getOrdenTrabajo() {
        return ordenTrabajo;
    }

    public Maquinaria getMaquinaria() {
        return maquinaria;
    }

    public Integer getHorasAsignadas() {
        return horasAsignadas;
    }
}
