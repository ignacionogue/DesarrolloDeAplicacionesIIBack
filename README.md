# Backend de Obras Públicas

API municipal en Java 17, Spring Boot 4.1.0 y PostgreSQL. Incluye proyectos,
órdenes, cuadrillas, recursos, dashboard y solicitudes de corte. El contrato HTTP
se describe en [FRONTEND-CONTRACT.md](FRONTEND-CONTRACT.md).

## Requisitos

- JDK 17 (versión objetivo de CI y Docker; también se ha probado con JDK 23).
- Maven 3.9.16 mediante el wrapper incluido.
- PostgreSQL para ejecución y validación de migraciones; H2 solo en tests rápidos.
- Docker para validar la imagen. No es necesario para las pruebas H2.

## Configuración

Usar [.env.example](.env.example) como referencia, nunca subir `.env` real.
Spring Boot **no carga `.env` automáticamente**: definir variables de entorno
en la terminal o IDE. En Docker se puede usar `--env-file .env`.

Obligatorias: `DB_URL` (JDBC), `DB_USERNAME`, `DB_PASSWORD`.
Ejemplo no sensible de URL: `jdbc:postgresql://localhost:5432/obras_publicas`.
La contraseña debe proporcionarla el desarrollador local o DevOps en Azure.
La aplicación no incluye credenciales ni valores alternativos de conexión.

Opcionales: `PORT` (prioridad), `SERVER_PORT` (8080), `DB_POOL_MAX_SIZE` (5),
`DB_POOL_MIN_IDLE` (0), `DB_CONNECTION_TIMEOUT_MS` (10000),
`CORS_ALLOWED_ORIGINS` (localhost 3000/5173), `APP_DEMO_ENABLED` (false).
La carga demo solo inserta catálogos vacíos; no crea tablas ni simula respuestas
de otros módulos. Para acceso del front por `/api/...` bajo el mismo origen,
el enrutamiento corresponde a DevOps; no se necesita una URL de backend en el front.

## Desarrollo local

```sh
git switch develop
git pull --ff-only
git switch -c feature/nombre-de-la-tarea
./mvnw spring-boot:run
```

En Windows usar `mvnw.cmd`. Si el wrapper falla al inspeccionar `.m2` en Windows,
se puede ejecutar un Maven 3.9.16 ya instalado con los mismos argumentos; no es
necesario editar el wrapper mantenido por DevOps.

Con las variables definidas y una base vacía, el arranque aplica Flyway V1 y
Hibernate valida el esquema. Para bases preexistentes leer
[docs/MIGRACIONES.md](docs/MIGRACIONES.md) **antes** de arrancar esta versión.

- Puerto por defecto: 8080.
- `GET /api/health`: consulta PostgreSQL; 200 con `database: up`, o 503 si falla.
- `GET /actuator/health`: health/probes de infraestructura existentes.
- `GET /swagger-ui/index.html`: documentación interactiva.
- `GET /v3/api-docs`: OpenAPI generado desde la versión ejecutada.

## Pruebas y empaquetado

```sh
./mvnw -B -ntp clean verify
```

Ejecuta tests H2 con la misma migración SQL y `ddl-auto=validate`; produce
`target/demo-0.0.1-SNAPSHOT.jar`. Incluye pruebas HTTP, reglas de negocio,
repetición de migración sin pérdida de datos y restricciones de esquema.

Para validar contra **una base PostgreSQL temporal y vacía**, exportar
`TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD` y ejecutar:

```sh
./mvnw -B -ntp -Dspring.profiles.active=postgres-test clean verify
```

Estos tests crean datos y no deben apuntar a Azure, a una base compartida ni a
la base demo del usuario. Recrear la base temporal entre ejecuciones completas:
algunos fixtures tienen nombres únicos. No se borra automáticamente ninguna base.
Los procesos de prueba se cierran al terminar; detener también la PostgreSQL
temporal o contenedor utilizado.

No hay un linter Java configurado por el equipo todavía. `git diff --check`
detecta errores de espacios, pero no reemplaza un linter. No se cambiaron controles de CI.

Con la API local y datos demo, PowerShell 7 permite verificaciones adicionales:

```powershell
./verify-demo.ps1                 # Agrega otra tanda de proyectos/órdenes
./verify-demo.ps1 -VerifyOnly     # Consulta y verifica los datos existentes
./verify-delivery.ps1             # Crea cortes demo si faltan y verifica dashboard
```

## Docker local

Se conserva el Dockerfile de DevOps. Construir sin publicar:

```sh
docker build -t obras-publicas-back:local .
docker run --rm --name obras-publicas-back-local --env-file .env -p 8080:8080 obras-publicas-back:local
```

Dentro del contenedor `localhost` es el contenedor: usar una dirección de DB
accesible desde Docker (por ejemplo `host.docker.internal` en Docker Desktop).
Verificar `/api/health`, `/api/public-works/projects` y `/v3/api-docs`, y detener
con `docker stop obras-publicas-back-local` al finalizar.

## Entrega

Trabajar en `feature/*` desde `develop` y abrir PR a `develop`; no push directo,
merge ni despliegue automático desde la PC. Ver [docs/DEVOPS-HANDOFF.md](docs/DEVOPS-HANDOFF.md).
Docker, pipelines, Azure, permisos y el momento de ejecución de migraciones son
coordinados con DevOps. Este repositorio entrega el esquema y documenta su impacto.
Auth, storage de evidencias e integraciones externas siguen pendientes.
