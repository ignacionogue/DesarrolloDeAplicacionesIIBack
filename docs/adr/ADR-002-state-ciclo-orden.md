# ADR-002 - State para el ciclo de OrdenTrabajo

- Estado: aceptada e implementada.
- Entidad central: `OrdenTrabajo`.

## Decisión

La entidad delega `schedule`, `start`, `pause`, `complete` y `validate` en objetos
`WorkOrderState`. `WorkOrderStates` reconstruye el State desde el `EstadoOT`
persistido. Cada State acepta únicamente sus transiciones y la operación inválida
lanza `InvalidStateTransitionException`, traducida a HTTP 409 Problem Details.

## Consecuencias

- Las transiciones dejan de estar dispersas en el service.
- Strategy decide cómo nace la orden y State gobierna su evolución.
- El enum continúa persistido, por lo que no cambia el contrato de base existente.
