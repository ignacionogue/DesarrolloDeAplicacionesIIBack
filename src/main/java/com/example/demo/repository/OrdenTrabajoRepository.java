package com.example.demo.repository;

import com.example.demo.model.OrdenTrabajo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OrdenTrabajoRepository extends JpaRepository<OrdenTrabajo, Long>,
        JpaSpecificationExecutor<OrdenTrabajo> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from OrdenTrabajo o where o.id = :id")
    java.util.Optional<OrdenTrabajo> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);
}
