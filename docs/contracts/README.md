# Contratos de integración vigentes

## Autoritativos

- M3 ↔ M7: `M3-M7-cortes-accidentes-v1.0.md`, aceptado el 28/10/2026.
- M3 → M6: schemas JSON Schema 2020-12 en
  `src/main/resources/contracts/m3/workOrderScheduled*.schema.json` y
  `workOrderCompleted*.schema.json`.
- M3 → M7: schemas JSON Schema 2020-12 en
  `src/main/resources/contracts/m3/streetClosureRequested*.schema.json`.
- M2: paquete v1.78 copiado sin cambios en `src/test/resources/contracts/m2-eventos-v1.78`.
- M3 integral: `contrato-m3-v0.7.md`, vigente para revisión intermodular.

Los archivos `*.data.schema.json` son los que se registran como `jsonSchema` del tipo
de evento en Core. Los archivos `*.schema.json` sin `.data` validan el evento completo
en pruebas locales.

## Histórico de revisión

La versión v0.6 queda reemplazada por `contrato-m3-v0.7.md`. En caso
de diferencia para M7, prevalece el contrato final v1.0. En operación prevalece
`GET /api/v1/topology` del Core para topología, TTL y `maxAttempts`.
