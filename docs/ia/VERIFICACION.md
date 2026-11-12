# Verificación de la actividad de IA

Grupo 5 - Módulo 3 Obras Públicas. Actualizado el 11 de noviembre de 2026.

## Estado implementado

- Endpoint `POST /api/public-works/assistant/triage`, habilitado solo para `PERSONAL_OBRAS` e `INGENIERO_ARQUITECTO`.
- Entrada acotada, saneada y sin persistencia de prompts o respuestas.
- Gateway desacoplado, prompt v1 cargado desde recursos y salida validada contra JSON Schema estricto.
- Proveedor `mock-v1` determinista, sin red, API key, costo de inferencia ni capacidad de ejecutar acciones.
- Timeout, circuit breaker, fallback manual, métricas, cuota por usuario y flag `AI_TRIAGE_ENABLED` apagado por defecto.
- Interfaz de revisión editable. Confirmar la revisión no crea ni modifica órdenes.

## Evidencia reproducible

`mvnw.cmd clean verify`

Resultado local: `BUILD SUCCESS`; 122 pruebas, 0 fallos, 0 errores y 2 omitidas porque Docker no estaba disponible. JaCoCo confirmó los umbrales. El reporte de las 20 evaluaciones queda en `target/ai-evals/incident-triage-v1-report.json` y se publica como artefacto de CI.

Los 20 casos aprobaron categoría, prioridad, recurso, revisión humana y controles de contenido. Cinco son adversariales: inyección de instrucciones, datos personales, HTML/script, comando SQL y pedido de acción por autoridad aparente.

Pruebas específicas verifican roles 401/403, respuesta 200 para roles habilitados, rate limit 429, timeout, apertura de circuito y fallback manual. Esto no acredita la calidad de un LLM comercial: la entrega usa un baseline determinista y documenta esa limitación.

## Seguridad y gobernanza

El clasificador no tiene acceso a base de datos, RabbitMQ, repositorios de escritura ni endpoints de negocio. La salida siempre lleva `humanReviewRequired=true`; el operador puede corregir categoría, prioridad y recurso. Los logs registran métricas técnicas, no el texto crudo.

Riesgos cubiertos: prompt injection, divulgación de información, salida insegura, agencia excesiva, confianza excesiva y consumo sin límites. Las mitigaciones se prueban, pero no se presentan como eliminación total del riesgo.

## Pendientes explícitos

- Un proveedor LLM real queda fuera de alcance hasta aprobar privacidad, presupuesto y nuevas evaluaciones.
- La publicación de resultados en Core no tiene contrato acordado y no se implementa.
- Las dos pruebas PostgreSQL con Testcontainers deben repetirse en CI con Docker disponible.
