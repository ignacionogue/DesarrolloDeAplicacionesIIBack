package com.example.demo.repository;

import com.example.demo.model.Cuadrilla;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CuadrillaRepository extends JpaRepository<Cuadrilla, Long> {
    Optional<Cuadrilla> findByNombreIgnoreCase(String nombre);
}
