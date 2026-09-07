package com.example.demo.repository;

import com.example.demo.model.OrdenTrabajo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OrdenTrabajoRepository extends JpaRepository<OrdenTrabajo, Long>,
        JpaSpecificationExecutor<OrdenTrabajo> {
}
