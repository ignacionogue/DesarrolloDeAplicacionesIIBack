package com.example.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Relacion entre una OrdenTrabajo y un Material, distinguiendo la cantidad
 * requerida/asignada de la efectivamente consumida (punto 9). No implica un
 * sistema de stock: solo registra cuanto se pidio y cuanto se uso en esa OT.
 */
@Entity
@Table(name = "material_asignado")
public class MaterialAsignado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "orden_trabajo_id")
    private OrdenTrabajo ordenTrabajo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "material_id")
    private Material material;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cantidadRequerida;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cantidadConsumida = BigDecimal.ZERO;

    protected MaterialAsignado() {
        // JPA
    }

    public MaterialAsignado(OrdenTrabajo ordenTrabajo, Material material, BigDecimal cantidadRequerida) {
        this.ordenTrabajo = ordenTrabajo;
        this.material = material;
        this.cantidadRequerida = cantidadRequerida;
    }

    public void registrarConsumo(BigDecimal cantidad) {
        this.cantidadConsumida = cantidad;
    }

    public Long getId() {
        return id;
    }

    public OrdenTrabajo getOrdenTrabajo() {
        return ordenTrabajo;
    }

    public Material getMaterial() {
        return material;
    }

    public BigDecimal getCantidadRequerida() {
        return cantidadRequerida;
    }

    public BigDecimal getCantidadConsumida() {
        return cantidadConsumida;
    }
}
