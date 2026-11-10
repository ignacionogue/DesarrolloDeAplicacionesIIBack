# ADR-001 - Strategy para crear órdenes

- Estado: aceptada e implementada.
- Contexto: una orden puede originarse manualmente, desde un proyecto o desde un evento externo.

## Decisión

`WorkOrderCreationStrategyResolver` selecciona una implementación de
`WorkOrderCreationStrategy` según `OrigenOT`. Cada estrategia valida y construye
su variante sin agregar condicionales al servicio principal.

## Consecuencias

- Un origen nuevo se incorpora con una estrategia nueva.
- Las reglas se prueban aisladamente.
- El resolver falla si no existe exactamente una estrategia aplicable.
