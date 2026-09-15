# Entrega del backend para revisión de DevOps

Repositorio: ignacionogue/DesarrolloDeAplicacionesIIBack.
Rama: feature/backend-migraciones-entrega, creada desde develop (662ad1e).
El backend anterior se recuperó sin modificar main, master ni develop.

## Funcionalidad

Proyectos y aprobación, órdenes y transiciones, cuadrillas, catálogo de recursos,
dashboard y solicitudes de corte pendientes. DTOs, paginación, CORS configurable,
errores centralizados y OpenAPI. Se conserva el health check PostgreSQL de DevOps.
El contrato detallado está en FRONTEND-CONTRACT.md; no se cambió el frontend.

## Impacto operativo

- Java objetivo 17; Spring Boot 4.1.0; Maven Wrapper 3.9.16.
- PostgreSQL mediante DB_URL, DB_USERNAME y DB_PASSWORD obligatorios.
- Puerto: PORT, alternativamente SERVER_PORT; default 8080.
- Health: /api/health (200 DB up; 503 DB down). Actuator conservado.
- Dockerfile: raíz; contexto raíz; sin modificaciones al Dockerfile ni workflows.
- Migración requerida: V1, 11 tablas de dominio más historial Flyway.
- Hibernate pasa de update a validate. No hay modificaciones manuales de Azure.
- Flyway se ejecuta en arranque como propuesta; DevOps debe aprobar permisos y
  mecanismo, o definir ejecución separada. Revisar docs/MIGRACIONES.md.
- CORS_ALLOWED_ORIGINS y APP_DEMO_ENABLED son opcionales; demo desactivada por defecto.
- Front y backend bajo el mismo origen `/api` dependen del enrutamiento existente
  administrado por DevOps. No se requieren cambios de URL en código frontend.
- .env.example documenta variables; .env, claves y archivos locales están ignorados.

## Cómo verificar

1. Base PostgreSQL temporal vacía y variables TEST_DB_*: ejecutar Maven con perfil postgres-test.
2. Base de ejecución vacía y variables DB_*: arrancar JAR/imagen y comprobar health.
3. Consultar /v3/api-docs, /api/public-works/projects, /work-orders, /resources,
   /dashboard/summary y /street-closures (todas las rutas de dominio bajo /api/public-works).
4. Crear proyecto y recorrer aprobación; crear OT y programar/iniciar/completar/validar.
5. Crear corte con OT existente; comprobar PENDIENTE y rechazo de fechas invertidas.
6. Reiniciar: Flyway valida V1 sin recrear tablas y conserva los datos.

## Resultados locales de la adaptación (7 de septiembre de 2026)

- Maven verify con H2: 13 tests, 0 fallos, JAR generado.
- Maven verify con perfil postgres-test sobre PostgreSQL 18.6 temporal vacía:
  13 tests, 0 fallos; V1 aplicada, entidades validadas y segunda migración sin DDL.
- JAR empaquetado ejecutado con las variables DB_* obligatorias sobre esa misma
  PostgreSQL: 8 consultas HTTP exitosas (health, proyectos, órdenes, recursos,
  dashboard, cortes, Swagger y OpenAPI). El reinicio conservó el esquema y datos.
- OpenAPI exportado desde ese JAR; server normalizado a `/` para evitar vincular
  el archivo a un puerto temporal de prueba.
- Docker build intentado: **pendiente de validación**, el motor Docker Desktop
  local devuelve HTTP 500 al consultar `dockerDesktopLinuxEngine/_ping`.
  El fallo ocurre antes de construir la imagen, no se modificó Dockerfile ni CI
  para evitarlo. El PR queda en borrador hasta validar contenedor/CI.
- Sin cambios de Azure, Terraform, workflows, permisos, secretos ni frontend.

Estos resultados no implican validación de Azure, aprobación funcional del PO
ni autorización de despliegue. La forma de ejecutar V1 requiere revisión de DevOps.
