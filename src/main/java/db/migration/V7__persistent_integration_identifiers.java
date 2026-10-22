package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Preserve REST primary keys while assigning stable inter-module identities. */
public class V7__persistent_integration_identifiers extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        String product = connection.getMetaData().getDatabaseProductName();
        String uuidFunction = switch (product) {
            case "PostgreSQL" -> "gen_random_uuid()";
            case "H2" -> "RANDOM_UUID()";
            default -> throw new IllegalStateException("Unsupported migration database");
        };
        try (var sql = connection.createStatement()) {
            for (String table : java.util.List.of("app_user", "orden_trabajo")) {
                sql.execute("ALTER TABLE " + table + " ADD COLUMN integration_id UUID DEFAULT " + uuidFunction);
                sql.execute("UPDATE " + table + " SET integration_id = " + uuidFunction + " WHERE integration_id IS NULL");
                sql.execute("ALTER TABLE " + table + " ALTER COLUMN integration_id SET NOT NULL");
                sql.execute("ALTER TABLE " + table + " ADD CONSTRAINT uq_" + table + "_integration_id UNIQUE (integration_id)");
            }
        }
    }
}
