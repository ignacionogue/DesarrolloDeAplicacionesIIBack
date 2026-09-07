package com.example.demo.repository;

import com.example.demo.model.MaquinariaAsignada;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaquinariaAsignadaRepository extends JpaRepository<MaquinariaAsignada, Long> {
    List<MaquinariaAsignada> findByOrdenTrabajoId(Long ordenTrabajoId);
}
