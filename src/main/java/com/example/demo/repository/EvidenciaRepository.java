package com.example.demo.repository;

import com.example.demo.model.Evidencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface EvidenciaRepository extends JpaRepository<Evidencia, Long> {
    List<Evidencia> findByOrdenTrabajoId(Long ordenTrabajoId);

    boolean existsByOrdenTrabajoId(Long ordenTrabajoId);

    /** Usado para calcular 'hasEvidence' en lote al listar OTs, evitando N+1. */
    List<Evidencia> findByOrdenTrabajoIdIn(Collection<Long> ordenTrabajoIds);
}
