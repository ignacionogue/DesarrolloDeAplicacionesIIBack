# ADR-003 - Inbox/Outbox para mensajería confiable

- Estado: aceptada e implementada.
- Contexto: RabbitMQ entrega al menos una vez y puede estar temporalmente caído.

## Decisión

Los eventos entrantes se registran en Inbox por `eventId` antes de aplicar efectos.
Los eventos salientes se escriben en Outbox dentro de la misma transacción de
negocio y se publican posteriormente, conservando el `eventId` durante reintentos.
Los fallos técnicos hacen `nack(requeue=false)` para que Core aplique su política y DLQ.

## Consecuencias

- Un duplicado no repite efectos.
- Una caída del broker no elimina eventos confirmados en la base.
- El orden por clave de negocio y la trazabilidad se mantienen durante reintentos.
