package com.example.demo.repository;

import com.example.demo.model.ProyectoObra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProyectoObraRepository extends JpaRepository<ProyectoObra, Long>,
        JpaSpecificationExecutor<ProyectoObra> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from ProyectoObra p where p.integrationId = :integrationId")
    java.util.Optional<ProyectoObra> findLockedByIntegrationId(
            @org.springframework.data.repository.query.Param("integrationId") java.util.UUID integrationId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from ProyectoObra p where p.id = :id")
    java.util.Optional<ProyectoObra> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);
}
