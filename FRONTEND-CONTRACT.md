# Contrato para primera entrega

API local: http://localhost:8080/api
Swagger local: http://localhost:8080/swagger-ui/index.html
OpenAPI local: http://localhost:8080/v3/api-docs
Archivo exportado: openapi.json (generado desde el backend en ejecucion).

La URL desplegada y los datos de produccion/demo remoto todavia deben confirmarse
con Infra. Las pruebas y datos descriptos aqui corresponden a PostgreSQL local.
Ningun archivo del front fue modificado.

## Endpoints disponibles

| Metodo | Ruta (desde el host, sin agregar otro /api) | Resultado |
|---|---|---|
| GET | /api/health | 200, {"status":"ok","service":"obras-publicas-backend","database":"up"}; 503 si falla DB |
| GET | /v3/api-docs | 200, OpenAPI |
| GET | /swagger-ui/index.html | 200, Swagger UI |
| GET | /api/public-works/projects | 200, pagina de proyectos |
| GET | /api/public-works/projects/{id} | 200, proyecto |
| POST | /api/public-works/projects | 201, proyecto creado |
| PUT | /api/public-works/projects/{id} | 200, proyecto actualizado |
| PATCH | /api/public-works/projects/{id}/submit-approval | 200, proyecto |
| PATCH | /api/public-works/projects/{id}/approve | 200, proyecto |
| PATCH | /api/public-works/projects/{id}/reject | 200, proyecto |
| GET | /api/public-works/work-orders | 200, pagina de ordenes |
| GET | /api/public-works/work-orders/{id} | 200, orden |
| POST | /api/public-works/work-orders | 201, orden creada |
| PUT | /api/public-works/work-orders/{id} | 200, orden actualizada |
| PATCH | /api/public-works/work-orders/{id}/schedule | 200, orden |
| PATCH | /api/public-works/work-orders/{id}/start | 200, orden |
| PATCH | /api/public-works/work-orders/{id}/pause | 200, orden |
| PATCH | /api/public-works/work-orders/{id}/complete | 200, orden |
| PATCH | /api/public-works/work-orders/{id}/validate | 200, orden |
| GET | /api/public-works/dashboard/summary | 200, resumen |
| GET | /api/public-works/resources | 200, {crews, materials, machinery} |
| GET | /api/public-works/street-closures | 200, pagina de cortes |
| POST | /api/public-works/street-closures | 201, solicitud pendiente |
| GET / POST | /api/public-works/crews | listado / alta de cuadrilla |

Todos los endpoints minimos pedidos estan implementados localmente. Auth queda
fuera de esta entrega tecnica: no existen /api/auth/login, /me ni /logout,
usuarios, tokens o roles aplicados en el servidor. No enviar Authorization.
El selector de roles del front no equivale a autenticacion.

## Paginacion y filtros

Proyectos: ?search=&status=&page=0&size=10
Ordenes: ?search=&status=&priority=&origin=&page=0&size=10
Cortes: ?page=0&size=10

Ordenamiento de cortes: id, closureRequestId, location, status, requestedFrom,
requestedTo o reason (ejemplo: sort=requestedFrom,desc). Otros campos devuelven
400 VALIDATION_ERROR.

```json
{"content":[],"page":0,"size":10,"totalElements":0,"totalPages":0}
```

Enviar page y size explicitamente. El valor predeterminado de size es 20.
Spring Pageable normaliza page invalida a 0; no hay validacion estricta de esos
parametros. Para orden estable enviar sort=id,asc. No basarse en orden implicito.
search busca nombre/descripcion en proyectos y descripcion/ubicacion en ordenes.
Filtros vacios se omiten; enums admiten mayusculas/minusculas. Actualmente un
status desconocido en proyectos no devuelve coincidencias; enums desconocidos
en filtros de ordenes se ignoran. Usar solamente los valores de este contrato.

## Proyectos

POST/PUT (campos obligatorios: name, estimatedBudget > 0, estimatedStartDate,
estimatedDurationDays > 0):

```json
{
  "name":"Repavimentacion Av. Lima",
  "description":"Recuperacion de calzada",
  "scope":"Fresado y carpeta asfaltica",
  "location":"Av. Lima 700",
  "estimatedBudget":1000000,
  "approvedBudget":800000,
  "usedBudget":200000,
  "estimatedStartDate":"2026-09-15",
  "estimatedDurationDays":30,
  "approvedDeadlineDays":28,
  "physicalProgress":25,
  "technicalManager":"Ing. Demo",
  "contractor":"Constructora Demo"
}
```

Respuesta: campos del ejemplo excepto usedBudget, mas id, status y budgetProgress.
budgetProgress = usedBudget / approvedBudget * 100, redondeo entero; 0 si no hay
presupuesto aprobado o es cero. physicalProgress va de 0 a 100. Presupuestos
aprobado/usado no negativos. PUT requiere los obligatorios, no es un PATCH:
reenviar los datos generales que se quieran conservar. usedBudget y physicalProgress
omitidos conservan su valor en PUT.

Los tres PATCH de aprobacion no llevan body:

- BORRADOR --submit-approval--> PENDIENTE_APROBACION.
- PENDIENTE_APROBACION --approve--> estado interno APROBADO, status visible SIN_INICIAR.
- PENDIENTE_APROBACION --reject--> RECHAZADO.

status combina aprobacion y actividad. El filtro APROBADO incluye los proyectos
aprobados independientemente de su actividad. Valores de actividad preparados:
SIN_INICIAR, EN_EJECUCION, PAUSADA, FINALIZADA. Aun no hay endpoints para modificar
actividad del proyecto. No usar APROBADA/RECHAZADA del mock del front.
El evento de aprobacion por ahora se registra en log; no se publica en Azure.

## Ordenes de trabajo

POST/PUT (obligatorios origin, description, priority):

```json
{"sourceRequestId":"demo-ticket-1","origin":"ATENCION_CIUDADANA","description":"Reparar bache","interventionType":"Calzada","location":"Av. Lima 717","priority":"ALTA","estimatedDurationHours":6,"crew":"Demo Norte"}
```

origin: MANUAL, ATENCION_CIUDADANA, INSPECCION.
priority: BAJA, MEDIA, ALTA. CRITICA/Ambiente/Transito requieren un acuerdo posterior.
crew es el nombre de una cuadrilla existente, no su ID. Puede omitirse.
sourceRequestId, crew, scheduledDate, outcome y otros opcionales pueden ser null
en respuestas. hasEvidence es boolean calculado; no hay carga HTTP de fotos aun.
outcome es texto libre, no un enum SUCCESS/REQUIRES_REVISION.

Crear sin cuadrilla produce PENDIENTE; con cuadrilla, ASIGNADA. PUT actualiza datos
generales y ajusta asignacion solo en estados tempranos; no reinicia una orden
en ejecucion o finalizada. Omitir crew en PUT quita la asignacion.

| Accion | Desde | Body | Resultado |
|---|---|---|---|
| schedule | PENDIENTE, PROGRAMADA, ASIGNADA | {"scheduledDate":"2026-09-16","crew":"Demo Norte"} | ASIGNADA con cuadrilla, PROGRAMADA sin cuadrilla |
| start | PROGRAMADA, ASIGNADA, PAUSADA, REABIERTA | sin body | EN_EJECUCION |
| pause | EN_EJECUCION | sin body | PAUSADA |
| complete | EN_EJECUCION, PAUSADA | opcional {"outcome":"Reparacion realizada"} | COMPLETADA |
| validate | COMPLETADA | {"approved":true,"observations":"Conforme"} | VALIDADA; approved=false produce REABIERTA |

schedule permite cambiar fecha antes de iniciar. crew omitido/null conserva la
cuadrilla; vacio devuelve 400. No permite reprogramar EN_EJECUCION ni estados
posteriores. No agregar DEMORADA o REPROGRAMADA como estados: las demoras son
un indicador y la reprogramacion cambia la fecha.

## Cortes de calle

POST:

```json
{"workOrderId":1,"location":"Av. Lima 700","affectedSections":["Av. Lima 700-760"],"requestedFrom":"2026-09-15","requestedTo":"2026-09-16","reason":"Reparacion de calzada"}
```

Todos los campos son obligatorios; workOrderId debe existir. Se admiten cortes
de un mismo dia; requestedTo no puede ser anterior a requestedFrom. Las fechas
son dias ISO, sin horarios. No se exige que sean futuras para permitir carga demo/historica.
Respuesta: mismos campos mas id, closureRequestId (UUID), sourceModule="public-works"
y status="PENDIENTE". GET usa PageResponse.

Decision provisional: esta primera version vincula el corte a una OT existente.
La futura vinculacion directa a un proyecto requiere ampliar el contrato.
Registrar no significa enviar a Transito ni obtener autorizacion: no se simulan
AUTORIZADO/RECHAZADO, condiciones ni respuestas externas. No hay adapter externo
hasta acordar el transporte y contrato con el otro modulo.

## Dashboard

GET /api/public-works/dashboard/summary devuelve:

- asOfDate: fecha del servidor; fijar zona horaria del despliegue con Infra.
- totalProjects: todos los proyectos.
- activeProjects: aprobados que no estan FINALIZADA (incluye SIN_INICIAR y PAUSADA).
- openWorkOrders: todas excepto COMPLETADA y VALIDADA; incluye REABIERTA.
- externalWorkOrders: todas las ordenes cuyo origin no es MANUAL, incluso terminadas.
  Son ordenes externas, no un registro de alertas o eventos recibidos.
- delayedWorkOrders: abiertas cuya scheduledDate es anterior a asOfDate.
  Aproximacion diaria; no se mide demora horaria ni retraso historico de cerradas.
- delayedProjects: activos cuya fecha estimada de inicio + plazo aprobado (o
  duracion estimada cuando falta plazo) es anterior a asOfDate.
- averagePhysicalProgress: media de todos los proyectos, redondeada; 0 si no hay.
- estimatedBudget, approvedBudget, usedBudget: sumas globales, null se considera 0.
- budgetProgress: usedBudget / approvedBudget * 100 global, redondeado; 0 si divisor 0.
- crewLoads: [{crewId, name, openWorkOrders}], incluye cuadrillas sin asignaciones.

No se inventa porcentaje de disponibilidad/carga sin capacidad definida, ni
cumplimiento historico sin fechas reales de finalizacion. Para barras por obra
usar GET projects y physicalProgress. Implementacion simple en memoria a partir
de los repositorios, apropiada al volumen demo; no esta optimizada para gran volumen.

## Recursos y cuadrillas

GET resources: {crews:[{id,nombre}], materials:[{id,nombre,unidad}], machinery:[{id,nombre}]}.
Son catalogos; no tienen stock, mantenimiento ni disponibilidad implementados.
GET crews devuelve un array; POST crews recibe {"nombre":"Cuadrilla nueva"}.

## Errores

```json
{"message":"Los datos enviados no son validos","code":"VALIDATION_ERROR","details":["name: El nombre es obligatorio"]}
```

400 VALIDATION_ERROR: validacion, enums en escritura, JSON/ID mal formado.
404 NOT_FOUND: entidad relacionada o solicitada inexistente.
409 INVALID_STATE_TRANSITION: accion no permitida en el estado actual.
422 BUSINESS_RULE_VIOLATION: fechas de corte invertidas o cuadrilla duplicada.
500 INTERNAL_ERROR: error no previsto; no incluye stack trace.

## CORS, despliegue y demo

CORS_ALLOWED_ORIGINS acepta origenes separados por comas, incluyendo esquema y
puerto, sin rutas. Por defecto: http://localhost:3000,http://localhost:5173.
Configurar la URL real del front desplegado ademas de las locales. No puede
confirmarse CORS remoto hasta conocer y probar el dominio. Swagger esta fuera de /api.

Infra debe configurar DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD y opcional
SERVER_PORT (8080). PostgreSQL es persistente, ddl-auto=update crea/ajusta tablas.
No usar credenciales locales como datos de despliegue.

Integracion con develop: se conservan Docker, CI, Actuator y el health check de
PostgreSQL de DevOps. DB_URL y DB_USERNAME tienen prioridad sobre DB_HOST/DB_USER;
PORT tiene prioridad sobre SERVER_PORT. DB_PASSWORD es obligatorio y no incluye
un valor por defecto en Git. /actuator/health mantiene las probes de infraestructura.

APP_DEMO_ENABLED=true carga materiales/maquinaria solo si sus catalogos estan vacios.
verify-demo.ps1 carga otra tanda de 4 proyectos/8 ordenes; -VerifyOnly no los crea.
verify-delivery.ps1 agrega cortes demo idempotentes y verifica lo nuevo.
Los datos demo no son datos municipales reales. No hay usuarios de prueba.

Se pueden conectar los endpoints de la tabla con este contrato y el OpenAPI
exportado. Auth, integraciones externas, actividad de proyectos y evidencias
quedan pendientes; no se promete inmutabilidad de futuras ampliaciones.
