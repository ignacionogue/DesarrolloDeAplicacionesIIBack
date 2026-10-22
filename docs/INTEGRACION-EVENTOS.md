# Integración de eventos M3: estado y entrega

## Estado

Implementación local en feature/integracion-core-rabbitmq, desde origin/develop
73bf4495db9c296e0ac93d93683ef1128d971590. No se modificaron Frontend, Azure,
Terraform ni pipelines. No se debe declarar completa la integración compartida:
faltan credenciales AMQPS, habilitación de IP de salida y la prueba bilateral real.

| Área | Implementado y probado localmente | Pendiente externo |
| --- | --- | --- |
| Transporte | Listener manual, NACK sin requeue, JSON persistente, confirms/returns, sin declarar topología | RabbitMQ 4.3/Core compartido |
| Persistencia | Inbox/Outbox, recuperación, deduplicación, bloqueo concurrente y orden de publicación por gestión | Operación y monitoreo del ambiente |
| M2 | ticketUpdated, gestión única, actualización por updatedAt, seis variantes de salida y vínculo a órdenes | Prueba bilateral v1.78 |
| M6 | Tres handlers, monitoreo de árboles, Strategy AMBIENTE, Scheduled/Completed para solicitudes de reparación | Confirmar UUID de workOrderId; detalle de consumedMaterials no inventado |
| M7 | Solicitud/prórroga, aprobación/rechazo/finalización, ventanas autorizadas, proyección minimizada de accidentes | Confirmar sourceRef/accidentId/inspectorId; corregir nombre histórico en Core |
| M1 | Puerto existente ampliado para aprobación/finalización y puerto de resolución deshabilitado | Casing, envelope, payload y resultados de resolución |
| Core HTTP | Variables externas declaradas | Cliente pendiente del contrato completo de respuesta/errores; no interviene en AMQPS |

## Diagnóstico inicial y fuentes

El backend existente tenía REST, seguridad JWT multiusuario, Strategy para altas,
PostgreSQL/Flyway V1-V4 y un ProjectEventPublisher que solo registraba un log.
Los cortes usaban fechas sin hora y tramos de texto; no existían Inbox/Outbox.

Fuentes: contrato-m3-v0.6.md recibido, guía M2 v1.78 y su ZIP de schemas.
Antes de copiar se verificó el SHA-256:
f20347c6cea5e9197e11d73cb3aef77db6fffd4c342ab75232381775c671b27f

Las 47 entradas del paquete M2 quedaron en src/test/resources/contracts/m2-eventos-v1.78.
Los schemas requeridos por runtime se copian sin cambios a resources/contracts/m2-v1.78.
El validador original aceptó 33 ejemplos válidos y rechazó 68 inválidos.
El contrato M3 recibido se conserva en docs/contracts/contrato-m3-v0.6.md.

## Arquitectura y decisiones

- Controller recibe HTTP; servicios coordinan; handlers separados por eventType
  procesan eventos. Un registro selecciona el handler y comprueba el productor.
  RabbitMQ queda en infrastructure/integration, fuera de controladores y entidades.
- Se reutilizan las entidades, repositorios, servicios, seguridad y Strategy
  existentes. No se reemplazan los endpoints anteriores.
- Inbox confirma la recepción por separado. Procesar bloquea la fila y confirma
  conjuntamente el efecto y su resultado. Una interrupción queda recuperable.
  eventId único impide repetir efectos. Errores guardan códigos, no excepciones/payloads.
- Outbox exige la transacción de negocio. Conserva eventId al reintentar y usa
  confirms/returns antes de marcar PUBLISHED. Una caída tras confirmar en Rabbit
  puede repetir la entrega: se mantiene at-least-once, no se promete exactly-once.
- Un advisory lock PostgreSQL serializa secuencias de la misma gestión hasta commit.
  El publicador usa SKIP LOCKED y no adelanta mensajes pendientes de esa gestión.
- El envelope saliente contiene solo los campos acordados. correlationId siempre
  existe. Una acción HTTP posterior conserva la correlación, pero no inventa
  causationId a partir de un evento histórico.
- Los UUID técnicos de personas y órdenes son persistentes; REST conserva sus IDs
  numéricos. updatedBy se obtiene del usuario autenticado en base de datos, nunca
  del body; un proceso automático se identifica SYSTEM.
- Las mutaciones de órdenes bloquean la fila antes de validar la transición.
  Dos inicios simultáneos no producen dos efectos/eventos.

## M2: gestión y decisiones

Solo se procesa negocio para responsibleAreaId=M3. Otros destinos, ticketCreated
y RECLASSIFICATION se ignoran. Los tickets se consolidan por ticketId. ROUTED
hidrata categoría/subcategoría/reasignación; una derivación tardía no retrocede
el estado más reciente. Información, adjuntos y escalamiento conservan sus tiempos.
PRIORITY_CHANGED marca revisión de planificación; ESCALATION_CHANGED no cambia
el estado de la orden. No se inventa una equivalencia CRITICAL -> prioridad local.

ROUTED abre la gestión. El operador evalúa y crea la orden por el POST existente,
origin=ATENCION_CIUDADANA y sourceRequestId=ticketId. No se inventa una intervención
a partir de una categoría. Una gestión recibida admite una sola orden vinculada.
Referencias históricas sin proyección siguen compatibles.

Programar/iniciar/pausar generan PROGRESS, precedido de STARTED si corresponde.
Completar genera RESOLVED/ACTION_COMPLETED con un mensaje público fijo: no se
publica automáticamente el texto libre de outcome. M3 no cambia el estado de M2.
CANCELLED bloquea nuevas acciones pero no cancela la intervención iniciada;
pausarla/completarla sigue permitido sin emitir una resolución contradictoria.
REOPENED reactiva la misma gestión y reinicia el seguimiento de STARTED.

Nuevo endpoint, rol PERSONAL_OBRAS:
POST /api/public-works/tickets/{ticketId}/updates

Body: updateType, publicMessage/internalMessage opcionales, progress opcional,
details y attachments según el schema M2. La respuesta 202 confirma el encolado,
no la recepción por M2. Están soportados STARTED, PROGRESS, INFORMATION_REQUIRED,
RETURNED, RESOLVED y REJECTED. RESOLVED exige una intervención completada/validada.
Datos inválidos devuelven 400 con message/code/details; estados incompatibles, 409.

Ejemplo INFORMATION_REQUIRED:
~~~json
{
  "updateType": "INFORMATION_REQUIRED",
  "details": {
    "informationRequest": {"messageForCitizen": "Indique la ubicación exacta."}
  }
}
~~~

## M6: evaluación y órdenes

Cada handler conserva su referencia: requestId, containerId o treeId. Un evento
más antiguo no reemplaza la proyección reciente. requiresPublicWorks=false se
ignora; un árbol sin suggestedIntervention queda en monitoreo, sin crear trabajo.

Nuevo origin REST/OpenAPI: AMBIENTE. La Strategy requiere sourceRequestId UUID de
una gestión recibida y accionable. El operador indica el trabajo y su prioridad;
una segunda orden para la misma gestión se rechaza. Contenedores/árboles consolidan
la gestión por activo; el contrato no define todavía un identificador de incidencia
distinto para múltiples intervenciones históricas sobre el mismo activo.

Solo infrastructureRepairRequested genera workOrderScheduled/workOrderCompleted.
sourceRequestId conserva requestId y workOrderId usa el UUID persistido. Los campos
visibles opcionales no se fabrican. Los schemas M3 Draft 2020-12 tienen
additionalProperties=false y están en resources/contracts/m3.
No se emiten consumedMaterials/attachments sin datos y contratos apropiados.

M6_UUID_CONTRACT_CONFIRMED=false deshabilita esas salidas hasta confirmar UUID.
El flag debe habilitarse antes del ciclo bilateral; no reconstruye retroactivamente
salidas que se omitieron mientras el contrato estaba deshabilitado.

## M7: cortes y accidentes

El endpoint anterior POST /api/public-works/street-closures sigue siendo local y
acepta las fechas/tramos anteriores. No convierte datos viejos inventando horarios.

Nuevo endpoint de PERSONAL_OBRAS:
POST /api/public-works/street-closures/integration

~~~json
{
  "workOrderId": 1,
  "reason": "Reparación de calzada",
  "affectedSections": [
    {"street": "Av. Rivadavia", "from": "Av. La Plata", "to": "Muñiz", "direction": "BOTH"}
  ],
  "requestedFrom": "2026-10-21T08:00:00-03:00",
  "requestedTo": "2026-10-21T18:00:00-03:00",
  "closureType": "TOTAL"
}
~~~

Para prórroga se agrega previousClosureRequestId. Se crean nuevos requestId y
eventId, conservando la correlación. sourceRef se obtiene del UUID de la orden.
El endpoint devuelve 409 mientras M7_UUID_CONTRACT_CONFIRMED=false.

GET /api/public-works/street-closures/integration/{requestId} devuelve estado,
solicitud, autorización y finalización; requiere autenticación. El listado anterior
también refleja el estado de los cortes integrados.

Estados: PENDING, APPROVED, REJECTED, ACTIVE, EXPIRED, ENDED. Se conservan tramos,
desvíos, condiciones, rechazo y finalización. authorized endDate prevalece sobre
requestedTo. Solo una ventana activa permite iniciar/reanudar la orden.
Una prórroga pendiente no elimina una ventana anterior todavía vigente.
Finalizar el corte no cancela automáticamente la orden.
Se acepta causationId ausente/null de entrada. No se registra streetClousureEnded.

roadAccidentRegistered se ignora sin daño; con daño exige damageDescription.
Solo persiste datos necesarios para evaluar infraestructura: elimina personas,
vehículos e inspector antes de Inbox. accidentId admite temporalmente una referencia
de texto; M7_ACCIDENT_UUID_REQUIRED permite endurecer la validación cuando M7 lo
confirme. No se usa DNI como correlación ni se persiste/loguea su campo.
Los accidentes abren una proyección de evaluación; no se inventa una orden ni
un contrato definitivo a partir de los identificadores todavía pendientes.

## Migraciones nuevas

| Versión | Cambio |
| --- | --- |
| V5 | Inbox/Outbox JSONB y secuencias |
| V6 | Proyecciones M2 |
| V7 Java | UUID persistentes para usuarios/órdenes, backfill compatible PostgreSQL/H2 |
| V8 | Gestiones M6 y origen AMBIENTE |
| V9 | Extensión de StreetClosure y proyección minimizada de accidentes |

V1-V4 no se modifican. Flyway sigue siendo la única vía de schema;
ddl-auto=validate. V9 conserva fechas antiguas sin asignarles offset artificial.
Los datos estructurados de solicitud, autorización y finalización se conservan
en JSONB, además de las columnas temporales/identificadores usadas para bloquear.

## Configuración y observabilidad

Variables sin secretos en .env.example/application.properties:
MESSAGING_ENABLED, CORE_BASE_URL, CORE_MODULE_NAME, CORE_MACHINE_SECRET,
RABBITMQ_HOST, RABBITMQ_PORT, RABBITMQ_USERNAME, RABBITMQ_PASSWORD, RABBITMQ_VHOST,
RABBITMQ_TLS_ENABLED, CORE_EXCHANGE, CORE_QUEUE y flags de confirmación anteriores.

MESSAGING_ENABLED=false no crea conexión/listener Rabbit. Según la guía común AWS
recibida (20/10/2026), los valores predeterminados son host
core-uade-2026.duckdns.org, puerto 5671, vhost core, usuario module_obras,
exchange muni.inbox, routing key vacía y cola q.obras. La contraseña se suministra
privadamente mediante RABBITMQ_PASSWORD. Mantener MESSAGING_ENABLED=false hasta
recibirla y habilitar con Core la IP pública de salida del backend.

TLS es obligatorio: se rechaza RABBITMQ_TLS_ENABLED=false al habilitar mensajería.
Se utiliza SSLContext de Java con su almacén de confianza y verificación de hostname,
sin fijar un certificado ni aceptar certificados arbitrarios. Ver
[documentación TLS de RabbitMQ](https://www.rabbitmq.com/docs/ssl).
PostgreSQL sigue siendo obligatorio. No se declara exchange, cola ni bindings:
el contenedor consulta pasivamente q.obras y Core administra la topología.
Se eliminó MESSAGING_PARTIAL_INTEGRATION_ACKNOWLEDGED, que era una barrera provisional.

El token de POST /api/v1/auth/module-token es exclusivamente HTTP; nunca se usa
como contraseña AMQPS. CORE_BASE_URL, CORE_MODULE_NAME y CORE_MACHINE_SECRET
permanecen separados y no son necesarios para abrir la conexión AMQPS. La guía
indica el request module/secret y los nombres accessToken/expiresIn, pero falta
el contrato completo de respuesta/errores: el cliente y renovación siguen pendientes,
sin DTO inventado ni registro automático de publicaciones/suscripciones.

La guía describe un único ambiente compartido. Coordinar emisor, destinatarios,
payload ficticio y horario antes de publicar. Resolver con Core la diferencia
entre diez suscripciones registradas y las nueve esperadas, sin modificar alias
ni suscripciones automáticamente. Un publisher confirm acredita recepción por
RabbitMQ, no procesamiento de negocio. Conservar eventId en retransmisiones.
No se intentó ninguna conexión ni publicación al ambiente compartido.
Métricas Micrometer: obras.events por resultado y obras.outbox.backlog. Logs con
eventId/eventType/correlationId/sourceModule/resultado. No se registran tokens,
secretos, DNI ni payload completo. Los endpoints de métricas no se abren públicamente.

## Validación y límites

- ./mvnw.cmd test: todas las pruebas existentes conservadas y nuevas de eventos.
- Ajuste AWS (22/10): ./mvnw.cmd test aprobó 97 pruebas; verify final aprobó 98
  con la prueba adicional de mandatory return y generó el JAR.
- Pruebas nuevas: TLS obligatorio, hostname, credenciales AMQP independientes,
  contraseña vacía, configuración manual/pasiva y devolución aunque exista confirm.
- Validación previa del perfil postgres-test sobre base local desechable: 94 aprobadas,
  JAR generado. Este ajuste de transporte no cambia schema ni persistencia; no se repitió ese perfil.
- Upgrade desde V2/V4/V6 y creación desde base vacía, incluyendo usuarios/órdenes
  anteriores y cortes de fechas simples. Repetir migraciones no cambia sus UUID.
- Pruebas de deduplicación, rollback, recuperación, concurrencia, reintentos de
  Outbox, schemas, NACK(false), seguridad HTTP y ciclos M2/M6/M7.
- RabbitMQ probado con dobles del cliente. Docker no tenía motor activo; no hubo
  broker real/Testcontainers ni prueba bilateral. Ninguna prueba usó Azure.
- Los ajustes finales de correlación del cierre, validación HTTP y métricas
  quedaron incluidos en ambas verificaciones finales: 94 pruebas en cada motor.

## Para DevOps y Frontend

DevOps: revisar V5-V9, obtener contraseña AMQPS y habilitar la IP pública de salida;
proporcionar contrato completo HTTP/token, confirmar UUID con M6/M7
y el nombre streetClosureEnded; configurar variables como secretos del ambiente.
Autorizar luego despliegue y prueba bilateral. Este PR no despliega ni registra
contratos/topología en Core y no debe considerarse integración compartida completa.

Frontend: los endpoints anteriores siguen disponibles. AMBIENTE es un origen nuevo;
los endpoints adicionales de tickets/cortes figuran en OpenAPI. El corte integrado
requiere fechas con offset y tramos estructurados. 202 en decisiones de tickets
significa encolado. La UI debe seguir consumiendo rutas relativas /api/....
No se modificó su repositorio.
