# M3 Obras ↔ M7 Tránsito — contrato final de cortes y accidentes

**Versión:** 1.0  
**Fecha:** 28/10/2026  
**Estado en M3:** aceptado e implementado; pendiente de registro correcto en Core y prueba E2E.

## Envelope y convenciones

Todos los eventos usan el envelope Core: `eventId`, `eventType`, `eventVersion: "1.0"`,
`occurredAt` con offset, `sourceModule`, `correlationId`, `causationId` cuando corresponda
y `data`. M3 publica con `sourceModule: "obras"`; M7 publica con
`sourceModule: "transito"`. Los identificadores técnicos que viajan entre módulos son UUID.

## M3 → M7: `streetClosureRequested`

Campos requeridos en `data`: `closureRequestId`, `sourceModule`, `sourceRef`, `reason`,
`affectedSections`, `requestedFrom`, `requestedTo`, `closureType` y `requestedAt`.

- `data.sourceModule` es `obras`.
- `sourceRef` es el UUID técnico de la orden.
- Cada `affectedSections[]` requiere `street`, `from`, `to` y `direction`
  (`FORWARD`, `REVERSE` o `BOTH`).
- Una prórroga informa `previousClosureRequestId` y conserva el `correlationId`.

## M7 → M3

| Evento | Regla |
| --- | --- |
| `streetClosureApproved` | Devuelve `streetClosureId`, solicitud, módulo solicitante, período autorizado, tramos, desvíos y condiciones. Conserva `correlationId`; `causationId` es el `eventId` de la solicitud. |
| `streetClosureRejected` | Devuelve solicitud, módulo solicitante y `rejectionReason`. Conserva `correlationId`; `causationId` es el `eventId` de la solicitud. |
| `streetClosureEnded` | Se emite al finalizar un corte `ACTIVE`, manual o automáticamente al vencer. Conserva `correlationId` y no informa `causationId`. |
| `roadAccidentRegistered` | Sólo se procesa cuando `involvesInfrastructureDamage=true`; `damageDescription` es obligatorio y `accidentId` es UUID. |

## Migración del nombre histórico

El nombre correcto es `streetClosureEnded`. El typo histórico
`streetClousureEnded` no se usa en código ni contratos de M3. La migración coordinada es:

1. Ambiente mantiene temporalmente ambas suscripciones.
2. M7 declara en Core la publicación correcta y cambia su productor.
3. M3 y M6 confirman recepción; Ambiente retira la suscripción histórica.

El tipo histórico puede quedar huérfano en el registro de Core porque no existe endpoint
de borrado. No debe volver a publicarse ni consumirse.

## Criterio de cierre

M3 recibe aprobación, rechazo y finalización con la correlación acordada; publica la
solicitud normal y la prórroga con UUID; Core no informa cables cortados para M3 ↔ M7;
y la secuencia se valida en una prueba E2E por RabbitMQ compartido.
