package com.example.demo.repository;

import com.example.demo.model.Observacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ObservacionRepository extends JpaRepository<Observacion, Long> {
    List<Observacion> findByOrdenTrabajoId(Long ordenTrabajoId);
}
