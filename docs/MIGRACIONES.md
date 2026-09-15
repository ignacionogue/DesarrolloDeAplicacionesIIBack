# Migraciones de PostgreSQL

## Herramienta y esquema inicial

Flyway 12.4.0, gestionado por Spring Boot 4.1.0; soporte PostgreSQL incluido.
`src/main/resources/db/migration/V1__create_public_works_schema.sql` crea:

- proyecto_obra, orden_trabajo y cuadrilla.
- material, maquinaria y sus asignaciones.
- evidencia y observacion.
- street_closure y street_closure_affected_sections.

Incluye claves primarias identity, foráneas, unicidad, estados válidos e índices
para relaciones. No inserta datos personales ni datos demo. La tabla de control
`flyway_schema_history` registra versión, checksum y resultado de aplicación.

## Ejecución propuesta para revisión de DevOps

La aplicación ejecuta Flyway durante el arranque, antes de inicializar JPA:

```sh
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

Requiere `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Flyway valida checksums, aplica
V1 solo si falta y Hibernate usa `ddl-auto=validate`: **no crea ni ajusta tablas**.
En PostgreSQL el SQL de V1 es transaccional. No hay scripts manuales contra Azure.

**DevOps debe aprobar este mecanismo antes del despliegue.** La identidad que
ejecute V1 necesita permisos DDL en el esquema asignado. Si DevOps requiere un
job o usuario de migración separado, acordarlo antes de cambiar el proceso; no
se agregaron credenciales de migración ni se modificaron pipelines/permisos.

## Base existente: detenerse y revisar

`baseline-on-migrate=false` y `clean-disabled=true` evitan adoptar o borrar un
esquema accidentalmente. Para una base vacía, V1 se aplica normalmente.

Si ya existen tablas creadas por Hibernate u otro proceso y no hay historial de
Flyway, el arranque se rechaza. No usar baseline automático, `IF NOT EXISTS`,
`clean`, ni borrar tablas para sortear el error. DevOps debe confirmar:

1. Qué base/esquema corresponde al módulo y qué versión la creó.
2. Respaldo disponible y comparación del esquema real con V1, incluyendo tipos,
   nulabilidad, restricciones, índices e identidades.
3. Si corresponde una adopción controlada con baseline explícito o una migración
   de reconciliación. Requiere revisión separada; este PR no la ejecuta.

La base demo local generada anteriormente con `ddl-auto=update` se conserva sin
cambios. La prueba de V1 se realiza en otra base temporal y vacía.

## Versiones siguientes y reversión

Después de aplicar V1, no editarla: crear `V2__descripcion.sql`, etc. Probar cada
versión sobre PostgreSQL y documentar compatibilidad con la imagen anterior.
Una segunda ejecución de la misma versión debe validar y no repetir DDL.

No hay rollback destructivo automático ni script DROP. Si V1 falla durante su
transacción, PostgreSQL revierte esa transacción; revisar el log antes de reintentar.
Después de una migración exitosa, revertir la imagen no revierte la DB.
DevOps decide entre imagen compatible, nueva migración correctiva o restauración
de respaldo coordinada. Toda operación destructiva requiere revisión y respaldo.

## Validación reproducible

Ejecutar el perfil `postgres-test` del README sobre una PostgreSQL temporal vacía.
El arranque prueba V1 antes de validar entidades; la suite verifica endpoints,
claves foráneas, columnas obligatorias y que `migrate()` por segunda vez aplica
0 migraciones y conserva los registros existentes.
No convertir la base de prueba en base compartida. La versión PostgreSQL usada
y resultados de la ejecución se registran en `docs/DEVOPS-HANDOFF.md`.
