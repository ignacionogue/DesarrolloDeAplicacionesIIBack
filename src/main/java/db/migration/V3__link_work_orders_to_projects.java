package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.sql.SQLException;
import java.util.ArrayList;

/** V1 used an unnamed CHECK: PostgreSQL and H2 assign different names to it. */
public class V3__link_work_orders_to_projects extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        var constraints = new ArrayList<String>();
        try (var statement = connection.prepareStatement("""
                SELECT tc.constraint_name, cc.check_clause
                FROM information_schema.table_constraints tc
                JOIN information_schema.check_constraints cc
                  ON cc.constraint_catalog = tc.constraint_catalog
                 AND cc.constraint_schema = tc.constraint_schema
                 AND cc.constraint_name = tc.constraint_name
                WHERE LOWER(tc.table_name) = 'orden_trabajo'
                  AND tc.table_schema = CURRENT_SCHEMA
                  AND tc.constraint_type = 'CHECK'
                """); var rows = statement.executeQuery()) {
            while (rows.next()) {
                String clause = rows.getString(2).toLowerCase(java.util.Locale.ROOT);
                // PostgreSQL 18 exposes NOT NULL through this view as well.
                if (clause.matches("(?s).*\\borigin\\b.*") && clause.contains("'manual'")
                        && clause.contains("'atencion_ciudadana'") && clause.contains("'inspeccion'")) {
                    constraints.add(rows.getString(1));
                }
            }
        }
        if (constraints.size() != 1) throw new SQLException("Expected exactly one V1 origin CHECK");
        try (var sql = connection.createStatement()) {
            sql.execute("ALTER TABLE orden_trabajo DROP CONSTRAINT \"" + constraints.get(0).replace("\"", "\"\"") + "\"");
            sql.execute("ALTER TABLE orden_trabajo ADD CONSTRAINT ck_orden_origin CHECK (origin IN ('MANUAL','PROYECTO','ATENCION_CIUDADANA','INSPECCION'))");
            sql.execute("ALTER TABLE orden_trabajo ADD COLUMN project_id BIGINT REFERENCES proyecto_obra(id)");
            sql.execute("CREATE INDEX idx_orden_trabajo_project ON orden_trabajo(project_id)");
            sql.execute("ALTER TABLE orden_trabajo ADD CONSTRAINT ck_orden_project_origin CHECK ((origin = 'PROYECTO' AND project_id IS NOT NULL) OR (origin <> 'PROYECTO' AND project_id IS NULL))");
        }
    }
}
