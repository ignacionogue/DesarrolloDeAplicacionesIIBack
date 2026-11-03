package com.example.demo;

import db.migration.V10__public_projects_integration_identity;
import db.migration.V7__persistent_integration_identifiers;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MigrationDatabaseGuardTests {

    @Test
    void v7RejectsAnUnsupportedDatabaseBeforeExecutingSql() throws Exception {
        var context = unsupportedContext();

        var error = assertThrows(IllegalStateException.class,
                () -> new V7__persistent_integration_identifiers().migrate(context));

        assertEquals("Unsupported migration database", error.getMessage());
    }

    @Test
    void v10RejectsAnUnsupportedDatabaseBeforeExecutingSql() throws Exception {
        var context = unsupportedContext();

        var error = assertThrows(IllegalStateException.class,
                () -> new V10__public_projects_integration_identity().migrate(context));

        assertEquals("Unsupported migration database", error.getMessage());
    }

    private Context unsupportedContext() throws Exception {
        var context = mock(Context.class);
        var connection = mock(Connection.class);
        var metadata = mock(DatabaseMetaData.class);
        when(context.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("UnsupportedDB");
        return context;
    }
}
