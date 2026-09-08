package com.example.demo.repository;

import com.example.demo.model.ProyectoObra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProyectoObraRepository extends JpaRepository<ProyectoObra, Long>,
        JpaSpecificationExecutor<ProyectoObra> {
}
