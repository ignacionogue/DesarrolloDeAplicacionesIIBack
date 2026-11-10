# Eventos del Módulo 2 · JSON Schemas v1.78 (envelope del Core)

Definición formal (JSON Schema Draft 2020-12) de los tres eventos de M2, adaptada al envelope oficial del Core (FAQ Core, RabbitMQ).
Reemplaza a los paquetes anteriores (v1.75 y v1.74). La v1.74 usaba `specVersion`, `producer` y `subject`: el Core no acepta campos extra y esos campos quedaron fuera.

> Este paquete es el contrato en formato máquina. Si algo de la guía de Eventos contradice a estos schemas, se corrige uno de los dos: hay que decidir cuál, no dejarlos divergir.

## Contenido

| Archivo | Para qué sirve |
|---|---|
| `schemas/envelope.schema.json` | Sobre del Core: `eventId`, `eventType`, `eventVersion`, `occurredAt`, `sourceModule`, `correlationId`, `causationId`, `data`. Sin campos extra. |
| `schemas/common.schema.json` | Tipos comunes (actor, attachment, location, escalation, estados, prioridades, ids). |
| `schemas/ticketCreated.schema.json` / `ticketUpdated.schema.json` / `updateTicketStatus.schema.json` | Evento completo (envelope + data) |
| `schemas/*.data.schema.json` | Validación de `data` con `$ref` a `common.schema.json`: requiere resolver las referencias del paquete. |
| `schemas/core-registration/*.data.schema.json` | Versiones **autocontenidas**, propuestas para registrar en Core cuando éste no soporte `$ref` externos. Verificar el alcance real del registro. |
| `schemas/event.schema.json` | Despachador: elige el schema según `eventType`. |
| `examples/valid/` | 28 ejemplos completos, uno por caso (ticket de bache TK-2026-000123 derivado a Obras). |
| `examples/guia/` | 5 ejemplos de la guía de Eventos reescritos con el envelope nuevo. |
| `validate.py` | Validador + 68 casos inválidos que deben ser rechazados. |

## Cómo validar

```
pip install "jsonschema[format]" referencing
python validate.py                 # 33 válidos deben pasar, 68 inválidos deben fallar, incluida una fecha imposible
python validate.py mi_evento.json  # valida un evento tuyo (envelope completo)
```

El schema de entrada es `schemas/event.schema.json`; los `$ref` entre archivos se resuelven por `$id`, por eso hace falta cargar todo `schemas/` en un registro (lo hace `validate.py`).

## Envelope (resumen)

| Campo | Tipo | Req. |
|---|---|---|
| `eventId` | UUIDv4 | Sí |
| `eventType` | `ticketCreated` / `ticketUpdated` / `updateTicketStatus` | Sí |
| `eventVersion` | string, `"1.0"` | No (default `"1.0"`; M2 siempre lo envía) |
| `occurredAt` | ISO-8601 con offset (`Z` o `±hh:mm`) | Sí |
| `sourceModule` | slug del Core | Sí |
| `correlationId` / `causationId` | UUID o null | No |
| `data` | objeto del evento | Sí |

`sourceModule`: `atencion-ciudadana` en `ticketCreated` y `ticketUpdated`; en `updateTicketStatus` es cualquier slug **distinto** de M2.

## Códigos Mx vs. slugs del Core

Dentro de `data` (`responsibleAreaId`, `areaId`) se siguen usando códigos `M1`…`M9`. En el envelope, `sourceModule` usa el slug del Core.

| Mx | slug | Módulo |
|---|---|---|
| M1 | ciudadanos | Ciudadanos |
| M2 | atencion-ciudadana | Atención ciudadana |
| M3 | obras | Obras públicas |
| M4 | habilitaciones | Habilitaciones |
| M5 | rentas | Rentas |
| M6 | ambiente | Ambiente |
| M7 | transito | Tránsito |
| M8 | desarrollo-social | Desarrollo social |
| M9 | core | Core |

Los slugs de M4, M5, M7 y M8 se tomaron de los nombres de cola del Core (q.habilitaciones, q.rentas, q.transito, q.desarrollo-social). La tabla también está en la guía de Eventos v1.78 §5.1.

## Qué valida el schema y qué no

Valida: estructura, tipos, obligatorios, enums, patrones y las reglas por `updateType` (por ejemplo `ROUTED` exige `details.routing`; `RESOLVED` exige `publicMessage` y `resolution.type`; `INFORMATION_PROVIDED` exige mensaje o adjunto; `RETURNED` exige `reasonCode` del enum cerrado `INVALID_INFORMATION | REQUEST_TYPE_MISMATCH | INSUFFICIENT_CONTEXT | OTHER`).

No puede validar (lo controla el consumidor, ver guía de Eventos §12):
- Que `sourceModule` (traducido a Mx) corresponda al `responsibleAreaId` del ticket.
- Que la transición de estado sea válida desde el estado actual.
- Idempotencia por `eventId`, orden de llegada y reenvíos: el Core sólo rutea, no detecta duplicados, y la entrega es at-least-once.
- Que exista un solo pedido de información activo.
- M1 mantiene una proyección informativa de los tickets identificados mediante eventos, sin reconciliación ni consultas adicionales a M2. Para updateTicketStatus, infiere el estado habitual desde updateType, deduplica por eventId y utiliza un control temporal aproximado. Se aceptan posibles diferencias, incluso persistentes, cuando M2 rechaza eventos o éstos llegan fuera de orden. M2 conserva la autoridad sobre el estado real del ticket.
- Que `causationId` apunte a un evento real (sólo se chequea que no sea igual a `eventId`).

Si el mensaje entregado por el Core trae campos propios (por ejemplo `receivedAt`), el consumidor los descarta antes de validar: el schema describe lo que envía el productor, que no admite campos extra.

## Reglas de consumo adoptadas

- Reenvío de un mismo hecho: mismo `eventId`. Todo consumidor deduplica por `eventId`.
- Transición inválida: se registra el rechazo y se confirma (ack).
- `PROGRESS` con el ticket todavía en `ROUTED` (prematuro): posible desorden frente a `STARTED`; sólo reintentar de forma acotada si Core permite la entrega diferida sin marcar el `eventId` como procesado. Si no llega `STARTED`, registrar rechazo y alertar. El área debe publicar `STARTED` antes de `PROGRESS`. La configuración de ACK/NACK, tiempos y DLQ requiere confirmación con Core.
- Hilo del trámite: se copia el `correlationId` del evento consumido y su `eventId` va en `causationId`; si el evento nace de una acción directa, `correlationId` nuevo.

## Pendiente de confirmar con el Core

1. Si el `jsonSchema` registrado valida el envelope completo o sólo `data` (los `*.data.schema.json` están pensados para `data`).
2. Endpoint y protocolo de publicación y URLs de los ambientes (la FAQ los da por confirmar).
3. Si el mensaje entregado incluye `receivedAt`.

Versión: Eventos v1.78 · eventVersion `"1.0"`.

## Aclaraciones de la consolidación v1.78

- `ROUTED` envía `category` y `subcategory` además de `requestType`; puede utilizarse otra vez tras una reclasificación excepcional autorizada (`details.routing.reassignment=true`), sin crear un segundo ticket con el mismo `ticketId`. No emitir `CONTENT_UPDATED` por el mismo acto de derivación.
- `ticketUpdated/INFORMATION_REQUIRED` y `INFORMATION_PROVIDED` pueden incluir `purpose=RECLASSIFICATION` dentro de su respectivo `details`: son solicitudes administrativas de M2 para que M1 actualice estado y mensaje, y **no** habilitan acciones de áreas externas. No se envían preguntas, formulario, ubicación sensible ni respuestas.
- Las solicitudes estructuradas se administran internamente en M2; una sola InformationRequest reúne preguntas, ubicación y evidencia requerida. El schema de eventos no representa esa estructura interna.
- `RESOLVED` operativo significa resultado efectivamente concluido. Las programaciones se comunican por `publicMessage` de STARTED o PROGRESS sin campos de calendario agregados al contrato.
- Se verifica `date-time` con `jsonschema.FormatChecker` y `rfc3339-validator` (incluido en `jsonschema[format]`); la validación de formato puede quedar silenciosamente deshabilitada si se omite la dependencia. La implementación del Core puede validar de otro modo y debe comprobarse.
- Los ejemplos de categorías, luminarias y áreas son ilustrativos; la asignación real la decide el catálogo `RequestType.responsibleAreaId`.
- Antes de congelar el contrato, validar con Core las garantías de entrega, reintentos, endpoint y soporte/semántica de JSON Schema para `data`. Si consumidores anteriores ya utilizan un esquema distinto, coordinar versión/compatibilidad para campos obligatorios en `ROUTED`.
