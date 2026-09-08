package com.example.demo;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MigrationTests {
    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;

    @Test void versionOneIsAppliedAndSecondMigrationPreservesData() {
        assertEquals("1", flyway.info().current().getVersion().toString());
        jdbc.update("INSERT INTO material(nombre, unidad) VALUES (?, ?)", "Migration preservation fixture", "kg");
        var count = jdbc.queryForObject("SELECT COUNT(*) FROM material", Long.class);
        flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals(count, jdbc.queryForObject("SELECT COUNT(*) FROM material", Long.class));
    }

    @Test void schemaEnforcesRelationsAndRequiredColumns() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO evidencia(orden_trabajo_id, storage_reference, fecha) VALUES (999999999, 'test', CURRENT_TIMESTAMP)"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO cuadrilla(nombre) VALUES (NULL)"));
    }
}
