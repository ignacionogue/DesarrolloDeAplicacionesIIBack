# Verificacion local — 6 de septiembre de 2026

## Integracion con el repositorio remoto

Base: origin/develop, commit 662ad1e. Se preservaron Docker, CI, Actuator,
DatabaseHealthController y su prueba de conectividad. Se excluyo el HealthController
local para evitar duplicar /api/health. La configuracion admite DB_URL,
DB_USERNAME y PORT de DevOps; DB_PASSWORD debe definirse explicitamente.
Maven verify de esta version integrada: 11 tests sin fallos y JAR generado.
No se ejecuto Docker localmente. La validacion del contenedor corresponde al CI.

## Revision ampliada posterior

- 10 tests Java: todos pasan. Se agregaron dashboard vacio, presupuestos nulos/cero,
  redondeo, vencimientos de proyectos, entradas de corte mal formadas, fechas
  imposibles, limites de texto, cortes del mismo dia, paginas vacias y
  reprogramaciones fallidas que conservan datos.
- Las pruebas HTTP nuevas usan open-in-view=false, igual que el backend local.
- Se reprodujo y corrigio un 500 al ordenar cortes por un campo inexistente:
  ahora devuelve 400 VALIDATION_ERROR con lista de campos permitidos en el contrato.
- Tras reiniciar: 27 comprobaciones de verify-delivery (omite crear los 3 cortes
  que ya existen) y 44 de verify-demo -VerifyOnly, sin fallos. Se comprobo ademas
  el 400 corregido y la persistencia de los 3 cortes en PostgreSQL.
- El frontend sigue sin cambios. Esta revision verifica los casos descriptos;
  no certifica ausencia absoluta de errores ni verifica el entorno remoto.

## Actualizacion: endpoints de primera entrega

- Implementados dashboard/summary y GET/POST street-closures.
- schedule ahora admite PENDIENTE, PROGRAMADA y ASIGNADA; conserva crew cuando
  se omite. Reprogramacion de ordenes iniciadas sigue bloqueada.
- Maven: 6 tests sin fallos. Incluye pruebas HTTP contra H2 de cortes y schedule,
  y verificacion de demoras del dashboard.
- PostgreSQL: verify-delivery.ps1, 33 comprobaciones sin fallos;
  verify-demo.ps1 -VerifyOnly, 44 comprobaciones sin fallos.
- 3 cortes demo PENDIENTE agregados; proyectos, ordenes y catalogos previos conservados.
- OpenAPI actualizado exportado a openapi.json. Ejemplo real: dashboard-example.json.
- Contrato vigente: FRONTEND-CONTRACT.md. Sustituye las limitaciones historicas
  sobre dashboard, cortes y schedule que figuran mas abajo.
- Repositorio Frontend verificado con git status --short: sin cambios.

## Registro de la primera verificacion (anterior a esta actualizacion)

Backend: http://localhost:8080
Swagger: http://localhost:8080/swagger-ui/index.html
OpenAPI: http://localhost:8080/v3/api-docs
Base: PostgreSQL local, obras_publicas. Datos ficticios identificados como DEMO.

## Resultado

- Segunda ejecucion de verify-demo.ps1: 128 comprobaciones, 0 fallos.
- Maven: 3 tests, 0 fallos (contexto H2 y regresion del mapeo de proyectos).
- Se probaron altas, modificaciones, consultas, aprobacion/rechazo de proyectos,
  programacion/inicio/pausa/finalizacion/validacion/reapertura de ordenes,
  transiciones invalidas, IDs inexistentes, validaciones, JSON mal formado,
  filtros combinados, paginacion, recursos, Swagger y preflight CORS local.
- Los datos de la primera ejecucion continuaron disponibles despues del reinicio.
- Datos finales: 8 proyectos, 16 ordenes, 3 cuadrillas, 3 materiales y 2 maquinas.
  Hay dos tandas de proyectos/ordenes por la repeticion antes y despues del arreglo.

## Correcciones

- POST proyectos ahora conserva usedBudget y physicalProgress enviados, igual que PUT.
- JSON ilegible e IDs con tipo invalido devuelven 400 / VALIDATION_ERROR,
  conservando message, code y details, en lugar de 500.
- Carga opcional de materiales/maquinaria con APP_DEMO_ENABLED=true;
  solo inicializa cada catalogo cuando esta vacio. Desactivada por defecto.

## Repetir

Ejecutar desde PowerShell 7 con el backend activo:

```powershell
./verify-demo.ps1 -VerifyOnly
```

Verifica los datos existentes sin crear proyectos u ordenes. Para cargar otra
tanda y repetir los flujos de escritura, ejecutar sin -VerifyOnly; agrega 4
proyectos y 8 ordenes. No elimina datos. La carga completa presupone los
catalogos demo activados en el arranque. verification-results.json guarda el
resultado de la ultima ejecucion.

## Contrato y limites pendientes

- Un proyecto aprobado devuelve status SIN_INICIAR, no APROBADO. El filtro
  APROBADO selecciona por el estado interno de aprobacion. No se cambio esta
  decision de dominio. Aun no hay endpoints de ejecucion de proyectos.
- Spring Pageable usa pagina 0 si page no es numerico. Se verifico su comportamiento
  actual; no se introdujo una validacion estricta de paginacion.
- schedule solo admite PENDIENTE. Crear una OT con crew la deja ASIGNADA sin fecha,
  por lo que el front debe crear sin crew y luego programar con fecha y cuadrilla
  si necesita ambas. Reprogramar requiere definir/ampliar el contrato.
- Materiales y maquinaria son catalogos; su presencia no implica consumo o
  asignacion implementados. No hay flujo HTTP completo de evidencias.
- Dashboard, cortes de calle, auth y publicacion externa de eventos siguen pendientes.
- CORS verificado para http://localhost:5173. El dominio desplegado aun debe configurarse.
- Esto verifica el backend local y los casos del script, no el despliegue ni toda
  combinacion posible de entradas/reglas de negocio.
