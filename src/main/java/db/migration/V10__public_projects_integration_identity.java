package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Keeps the local numeric key while exposing stable UUIDs to other modules. */
public class V10__public_projects_integration_identity extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        String product = connection.getMetaData().getDatabaseProductName();
        try (var sql = connection.createStatement()) {
            if ("PostgreSQL".equals(product)) {
                sql.execute("ALTER TABLE proyecto_obra ADD COLUMN integration_id UUID DEFAULT gen_random_uuid()");
                sql.execute("UPDATE proyecto_obra SET integration_id = gen_random_uuid() WHERE integration_id IS NULL");
                sql.execute("ALTER TABLE proyecto_obra ADD COLUMN integration_correlation_id UUID DEFAULT gen_random_uuid()");
                sql.execute("UPDATE proyecto_obra SET integration_correlation_id = gen_random_uuid() WHERE integration_correlation_id IS NULL");
            } else if ("H2".equals(product)) {
                sql.execute("ALTER TABLE proyecto_obra ADD COLUMN integration_id UUID DEFAULT RANDOM_UUID()");
                sql.execute("UPDATE proyecto_obra SET integration_id = RANDOM_UUID() WHERE integration_id IS NULL");
                sql.execute("ALTER TABLE proyecto_obra ADD COLUMN integration_correlation_id UUID DEFAULT RANDOM_UUID()");
                sql.execute("UPDATE proyecto_obra SET integration_correlation_id = RANDOM_UUID() WHERE integration_correlation_id IS NULL");
            } else {
                throw new IllegalStateException("Unsupported migration database");
            }
            sql.execute("ALTER TABLE proyecto_obra ALTER COLUMN integration_id SET NOT NULL");
            sql.execute("ALTER TABLE proyecto_obra ADD CONSTRAINT uq_proyecto_obra_integration_id UNIQUE (integration_id)");
            sql.execute("ALTER TABLE proyecto_obra ALTER COLUMN integration_correlation_id SET NOT NULL");
            sql.execute("ALTER TABLE proyecto_obra ADD COLUMN case_file_id UUID");
            sql.execute("ALTER TABLE proyecto_obra ADD COLUMN case_file_number VARCHAR(80)");
            sql.execute("ALTER TABLE proyecto_obra ADD COLUMN case_file_resolution VARCHAR(40)");
            sql.execute("ALTER TABLE proyecto_obra ADD COLUMN case_file_resolved_at TIMESTAMP WITH TIME ZONE");
        }
    }
}
