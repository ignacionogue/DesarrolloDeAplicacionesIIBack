# Entrega: órdenes por proyecto, Strategy y permisos

Rama `feature/flujo-ordenes-seguridad`, basada en `develop` commit `574a147`.
Integración mediante PR a develop; no incluye merge ni despliegue.

## Funcionalidad y contrato

- `POST/PUT /api/public-works/work-orders`: nuevo origen PROYECTO con projectId
  obligatorio, positivo y existente. MANUAL conserva órdenes independientes;
  Atención Ciudadana e Inspección conservan el alta HTTP anterior.
- Respuestas de órdenes, GET individual, listado y transiciones incluyen projectId.
- Strategy explícito con variantes manual/proyecto y resolver. MVC y persistencia
  separados; no se inventaron contratos de eventos M6/M7.
- Aprobación: body con approvedBudget, approvedDeadlineDays, approvedAt y observations;
  devuelve APROBADO y datos persistidos visibles en detalle/lista. Datos inválidos
  dan 400, transición incorrecta 409. PUT general no puede alterar términos aprobados.
- Permisos por rol aplicados a todas las escrituras, errores 401/403 consistentes,
  JWT vencidos/inválidos rechazados y Bearer documentado en Swagger.
- Dashboard: PROYECTO no se cuenta como origen externo.

Contrato completo en [FRONTEND-CONTRACT.md](../FRONTEND-CONTRACT.md), OpenAPI
exportado en [openapi.json](../openapi.json) y defensa en [ORDENES-STRATEGY.md](ORDENES-STRATEGY.md).

## Base de datos y variables

Requiere Flyway V3: relación nullable `orden_trabajo.project_id`, FK, índice y
restricciones de origen. V1/V2 no se modificaron. V3 está implementada como
migración Java dentro del JAR para localizar el CHECK sin nombre definido en V1
en PostgreSQL y H2. Ver [MIGRACIONES.md](MIGRACIONES.md).

No hay nuevas variables de entorno. Se conservan DB_URL, DB_USERNAME, DB_PASSWORD,
JWT_SECRET, AUTH_USERNAME, AUTH_PASSWORD, AUTH_ROLE y JWT_EXPIRATION_MINUTES.
Ahora AUTH_ROLE debe ser uno de los roles documentados y la duración JWT positiva.
CORS_ALLOWED_ORIGINS sigue controlando dominios autorizados.

**Punto pendiente de coordinación:** el login actual tiene una única cuenta y rol
por instancia. La autorización está implementada, pero eso no provee usuarios de
distintos roles para una demo completa. Hace falta acordar gestión multiusuario o
la integración de identidad. No habilitar privilegios extra a PERSONAL_OBRAS para
sortearlo, ni publicar JWT_SECRET para generar tokens desde el cliente.

No se tocaron frontend, Azure, Dockerfile, Terraform, workflows, permisos ni secretos.
DevOps conserva la decisión de despliegue y configuración del proxy `/api`.

## Verificaciones locales

- Maven verify: 50 pruebas con H2, 0 fallos, JAR empaquetado.
- Maven clean verify, perfil postgres-test: 50 pruebas sobre PostgreSQL 18.6,
  0 fallos. Incluye creación desde cero y actualización V2 → V3 con órdenes previas,
  preservación de datos, claves foráneas y segunda ejecución sin migraciones.
- JAR con PostgreSQL temporal y login real por cada rol: 131 controles de
  verify-demo.ps1 y 33 de verify-delivery.ps1, 0 fallos.
- Comprobaciones adicionales del JAR: projectId persistido, 401/403, CORS permitido
  y rechazo de origen no autorizado, esquemas OpenAPI y conservación tras reinicio.
- OpenAPI exportado desde el JAR, URL de servidor normalizada a `/`.
- Los tokens de prueba solo existieron en memoria. Para probar todos los roles se
  hicieron arranques locales separados con el rol configurado y una clave temporal;
  esto no equivale a disponer de varias cuentas en Azure.
- Docker local no se pudo validar: el motor de Docker Desktop no está disponible.
  La construcción de imagen debe verificarse mediante el pipeline del PR.

## Cómo verificar después del despliegue autorizado

1. Confirmar aplicación de V3 y `/api/health` con `database: up`.
2. Con PERSONAL_OBRAS, crear una orden MANUAL sin projectId y otra PROYECTO con un
   proyecto existente; comprobar el vínculo en POST, GET y listado filtrado.
3. Probar projectId faltante (400), inexistente (404) y rol incorrecto (403).
4. Enviar un proyecto a aprobación con PERSONAL_OBRAS; aprobar con
   RESPONSABLE_AUTORIZADO y body completo. Confirmar APROBADO y datos persistidos.
5. Programar/iniciar/pausar con JEFE_CUADRILLA; completar con OPERARIO_CONTRATISTA;
   validar/reabrir con INSPECTOR_OBRA. Transiciones inválidas devuelven 409.
6. Verificar `/swagger-ui/index.html`, su autorización Bearer y acceso desde el front
   mediante `/api/...` bajo el proxy configurado por DevOps.

Volver a una imagen antigua después de crear órdenes PROYECTO no es compatible
con su enum anterior. Coordinar corrección hacia adelante o recuperación con DevOps.
