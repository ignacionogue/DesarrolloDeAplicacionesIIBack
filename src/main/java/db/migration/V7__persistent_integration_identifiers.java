package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Preserve REST primary keys while assigning stable inter-module identities. */
public class V7__persistent_integration_identifiers extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        String product = connection.getMetaData().getDatabaseProductName();
        try (var sql = connection.createStatement()) {
            if ("PostgreSQL".equals(product)) {
                sql.execute("ALTER TABLE app_user ADD COLUMN integration_id UUID DEFAULT gen_random_uuid()");
                sql.execute("UPDATE app_user SET integration_id = gen_random_uuid() WHERE integration_id IS NULL");
                sql.execute("ALTER TABLE app_user ALTER COLUMN integration_id SET NOT NULL");
                sql.execute("ALTER TABLE app_user ADD CONSTRAINT uq_app_user_integration_id UNIQUE (integration_id)");
                sql.execute("ALTER TABLE orden_trabajo ADD COLUMN integration_id UUID DEFAULT gen_random_uuid()");
                sql.execute("UPDATE orden_trabajo SET integration_id = gen_random_uuid() WHERE integration_id IS NULL");
                sql.execute("ALTER TABLE orden_trabajo ALTER COLUMN integration_id SET NOT NULL");
                sql.execute("ALTER TABLE orden_trabajo ADD CONSTRAINT uq_orden_trabajo_integration_id UNIQUE (integration_id)");
            } else if ("H2".equals(product)) {
                sql.execute("ALTER TABLE app_user ADD COLUMN integration_id UUID DEFAULT RANDOM_UUID()");
                sql.execute("UPDATE app_user SET integration_id = RANDOM_UUID() WHERE integration_id IS NULL");
                sql.execute("ALTER TABLE app_user ALTER COLUMN integration_id SET NOT NULL");
                sql.execute("ALTER TABLE app_user ADD CONSTRAINT uq_app_user_integration_id UNIQUE (integration_id)");
                sql.execute("ALTER TABLE orden_trabajo ADD COLUMN integration_id UUID DEFAULT RANDOM_UUID()");
                sql.execute("UPDATE orden_trabajo SET integration_id = RANDOM_UUID() WHERE integration_id IS NULL");
                sql.execute("ALTER TABLE orden_trabajo ALTER COLUMN integration_id SET NOT NULL");
                sql.execute("ALTER TABLE orden_trabajo ADD CONSTRAINT uq_orden_trabajo_integration_id UNIQUE (integration_id)");
            } else {
                throw new IllegalStateException("Unsupported migration database");
            }
        }
    }
}
