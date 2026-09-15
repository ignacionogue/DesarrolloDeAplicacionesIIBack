package com.example.demo.repository;

import com.example.demo.model.MaterialAsignado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaterialAsignadoRepository extends JpaRepository<MaterialAsignado, Long> {
    List<MaterialAsignado> findByOrdenTrabajoId(Long ordenTrabajoId);
}
