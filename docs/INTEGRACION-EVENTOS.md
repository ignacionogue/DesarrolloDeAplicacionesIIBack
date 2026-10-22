# Integración Core y RabbitMQ

## Estado al 21 de octubre de 2026

Trabajo en curso en `feature/integracion-core-rabbitmq`, creada desde
`origin/develop` (`73bf449`). Ya existen envelope, registro de handlers,
adaptador AMQP, Inbox/Outbox y proyección de ticketUpdated. No es una entrega
completa: faltan los casos de uso emisores y los handlers M6/M7.
MESSAGING_ENABLED=false por defecto. Se requiere además reconocimiento explícito
de integración parcial para activar el adaptador en un entorno aislado de pruebas.
REST, frontend e infraestructura del equipo no se modificaron en esta etapa.

## Fuentes recibidas y verificación

- Guía `Eventos M2 - V1.78.docx`, proporcionada por el equipo.
- Paquete `m2-eventos-v1.78-schemas.zip`.
- Consigna `M3_Prompt_Backend_Integracion_Completa_v1.78.txt`.
- Contrato M3 v0.6, conservado en `docs/contracts/contrato-m3-v0.6.md`;
  reemplaza v0.5 y distingue propuestas UUID de acuerdos confirmados.

SHA-256 comprobado antes de extraer el ZIP:

```text
f20347c6cea5e9197e11d73cb3aef77db6fffd4c342ab75232381775c671b27f
```

El contenido original se conserva sin cambios en
`src/test/resources/contracts/m2-eventos-v1.78/`. Incluye schemas Draft 2020-12,
ejemplos y el validador suministrado; no es una dependencia del runtime Java.
Los schemas `core-registration` son material de referencia: no se registraron
ni se enviaron a Core.

Se ejecutó el `validate.py` recibido, con validación de formatos habilitada:
33 ejemplos válidos aceptados y 68 casos inválidos rechazados. Esto verifica el
paquete contractual, no un consumidor/productor implementado en el backend.
Dependencias de esta ejecución local: jsonschema 4.26.0, referencing 0.37.0 y
rfc3339-validator 0.1.4. Para reproducir, instalar `jsonschema[format]` y
`referencing` en un entorno Python de pruebas y ejecutar el validador del paquete.

## Diagnóstico inicial del backend antes de estos cambios

- Hay un puerto ProjectEventPublisher; su implementación actual solo escribe logs.
- No hay AMQP, Inbox, Outbox ni cliente de autenticación Core.
- Flyway utiliza V1–V4. La próxima versión libre observada es V5; verificarla de
  nuevo al implementar para evitar colisiones con cambios de otros integrantes.
- Órdenes y usuarios tienen identificadores Long. Para integración se necesitan
  UUID persistentes adicionales, preservando IDs y contratos REST existentes.
- StreetClosure usa fechas LocalDate y tramos String. No se pueden convertir esos
  datos a ventanas con offset ni a objetos de tramo inventando zona, horarios o
  límites. La evolución debe conservar el REST vigente y distinguir solicitudes
  con información suficiente para publicar de las que requieren completarse.

## Decisiones contractuales verificadas de M2

- Consumir ticketUpdated y filtrar responsibleAreaId=M3; ticketCreated no inicia
  negocio de Obras. No persistir contenido de tickets ajenos en una Inbox genérica.
- category/subcategory están dentro de details.routing en ROUTED. updatedAt está
  en data y no se debe reemplazar por occurredAt para ordenar cambios de negocio.
- RECLASSIFICATION no habilita trabajo; una derivación ROUTED posterior sí.
- M2 publica snapshots ROUTED y deltas posteriores. Un control temporal global
  que descarte un ROUTED antiguo recibido después de un delta puede perder la
  información inicial: el diseño debe conservar datos base sin regresar estado.
- El envelope de entrada M2 permite correlationId/causationId ausentes o null.
  La salida M3 debe ser más estricta: correlationId siempre UUID y causationId
  omitido cuando no hay causa. No endurecer el schema M2 recibido unilateralmente.
- updateTicketStatus usa updateOccurredAt, updatedBy y updateType; no newStatus.
  updatedBy.id identifica a la persona mediante un UUID persistente. El paquete
  permite null por ausencia real de persona identificable; las acciones de una
  persona local identificada deben aportar su UUID, no un UUID nuevo por evento.
- STARTED y PROGRESS requieren orden operativo aun cuando RabbitMQ no garantice
  orden. La futura Outbox necesita secuenciación por gestión y confirmaciones;
  ordenar una consulta por createdAt solamente no resuelve concurrencia/reintentos.

## Pendientes externos

Se recibió M3 v0.6. Sus propuestas UUID M6/M7 y los contratos M1 todavía
requieren las confirmaciones bilaterales enumeradas en ese documento.

También falta el contrato exacto request/response de
`POST /api/v1/auth/module-token` y cómo se emplea ese token en la integración
AMQP/Core. Conocer la ruta, algoritmo y duración no define nombres de campos,
headers o credenciales AMQP; no deben inventarse.

M1 y las ambigüedades UUID de M7 permanecen pendientes explícitos de la consigna.
No se habilitarán aliases ni contratos provisionales por defecto.

## Implementación y verificaciones del punto de recuperación

- V5 agrega Inbox/Outbox JSONB. Recibir se confirma en una transacción separada;
  procesar bloquea la fila y confirma conjuntamente efectos y resultado. Una
  interrupción deja RECEIVED/ERROR recuperable. Errores no guardan payload ni
  mensajes internos de excepciones en logs.
- V6 agrega la proyección de tickets. ROUTED tardío hidrata sus datos base sin
  regresar el estado más reciente. Se conserva información/escalamiento con
  sus tiempos independientes; se marcan evaluación y planificación pendientes.
  Todavía no se consume esa evaluación para crear/vincular una orden.
- Outbox exige transacción de negocio, mantiene eventId, espera confirms/returns,
  serializa publicación por orderingKey y usa bloqueo SKIP LOCKED. En PostgreSQL
  un advisory lock también serializa asignación de secuencias hasta commit.
- El listener usa ACK manual y NACK sin requeue. No declara topología. Los tipos
  conocidos de M6/M7 todavía no implementados fallan explícitamente; no se ACKean
  como trabajo realizado. M1 no tiene contrato habilitado.
- Schema M2 se valida en Java desde copias exactas locales, con formatos activos.
- Test completo H2 inicial: 73 aprobados. Verify PostgreSQL temporal: 74 aprobados
  y JAR generado, incluyendo actualización desde V4 y base vacía. Verify H2 más
  reciente: 77 aprobados, sin fallos ni errores, JAR generado. Las últimas tres
  pruebas y ajustes de logs/serialización de append requieren repetir PostgreSQL.
- Pruebas AMQP actuales usan dobles del cliente, no un broker real. Docker no
  tenía motor activo. No se realizó prueba bilateral ni se obtuvo token de Core.

Faltan salidas updateTicketStatus y UUID persistentes de personas/órdenes,
vinculación de proyecciones a casos de uso, ciclos M6/M7, evolución de cortes,
cliente de autenticación Core, pruebas restantes y entrega final del PR.
La prueba bilateral con Core compartido sigue pendiente y es condición para
declarar completa la integración. No se hizo despliegue ni registro en Core.
