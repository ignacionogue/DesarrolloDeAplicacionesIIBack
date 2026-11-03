package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Keeps the local numeric key while exposing stable UUIDs to other modules. */
public class V10__public_projects_integration_identity extends BaseJavaMigration {
    private static final java.util.List<String> POSTGRESQL_UUID_STATEMENTS = java.util.List.of(
            "ALTER TABLE proyecto_obra ADD COLUMN integration_id UUID DEFAULT gen_random_uuid()",
            "UPDATE proyecto_obra SET integration_id = gen_random_uuid() WHERE integration_id IS NULL",
            "ALTER TABLE proyecto_obra ADD COLUMN integration_correlation_id UUID DEFAULT gen_random_uuid()",
            "UPDATE proyecto_obra SET integration_correlation_id = gen_random_uuid() WHERE integration_correlation_id IS NULL");
    private static final java.util.List<String> H2_UUID_STATEMENTS = POSTGRESQL_UUID_STATEMENTS.stream()
            .map(statement -> statement.replace("gen_random_uuid()", "RANDOM_UUID()"))
            .toList();

    @SuppressWarnings("java:S2077") // Every dynamic statement comes from an immutable hard-coded allow-list.
    @Override public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        String product = connection.getMetaData().getDatabaseProductName();
        java.util.List<String> uuidStatements = switch (product) {
            case "PostgreSQL" -> POSTGRESQL_UUID_STATEMENTS;
            case "H2" -> H2_UUID_STATEMENTS;
            default -> throw new IllegalStateException("Unsupported migration database");
        };
        try (var sql = connection.createStatement()) {
            for (String statement : uuidStatements) sql.execute(statement);
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
