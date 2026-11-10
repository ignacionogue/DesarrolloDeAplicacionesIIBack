package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Preserve REST primary keys while assigning stable inter-module identities. */
public class V7__persistent_integration_identifiers extends BaseJavaMigration {
    private static final java.util.List<String> POSTGRESQL_STATEMENTS = java.util.List.of(
            "ALTER TABLE app_user ADD COLUMN integration_id UUID DEFAULT gen_random_uuid()",
            "UPDATE app_user SET integration_id = gen_random_uuid() WHERE integration_id IS NULL",
            "ALTER TABLE app_user ALTER COLUMN integration_id SET NOT NULL",
            "ALTER TABLE app_user ADD CONSTRAINT uq_app_user_integration_id UNIQUE (integration_id)",
            "ALTER TABLE orden_trabajo ADD COLUMN integration_id UUID DEFAULT gen_random_uuid()",
            "UPDATE orden_trabajo SET integration_id = gen_random_uuid() WHERE integration_id IS NULL",
            "ALTER TABLE orden_trabajo ALTER COLUMN integration_id SET NOT NULL",
            "ALTER TABLE orden_trabajo ADD CONSTRAINT uq_orden_trabajo_integration_id UNIQUE (integration_id)");
    private static final java.util.List<String> H2_STATEMENTS = POSTGRESQL_STATEMENTS.stream()
            .map(statement -> statement.replace("gen_random_uuid()", "RANDOM_UUID()"))
            .toList();

    @SuppressWarnings("java:S2077") // Every statement comes from the immutable allow-list above.
    @Override public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        String product = connection.getMetaData().getDatabaseProductName();
        java.util.List<String> statements = switch (product) {
            case "PostgreSQL" -> POSTGRESQL_STATEMENTS;
            case "H2" -> H2_STATEMENTS;
            default -> throw new IllegalStateException("Unsupported migration database");
        };
        try (var sql = connection.createStatement()) {
            for (String statement : statements) sql.execute(statement);
        }
    }
}
