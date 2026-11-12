# ADR-IA-001: clasificación asistida mediante gateway

Estado: ACCEPTED e implementado. Fecha: 11/11/2026. Responsables de aceptación: equipo M3.

La interpretación de reclamos en texto libre puede beneficiarse de IA, pero una clasificación errónea puede afectar la atención. Se elige una sugerencia revisable, nunca una acción administrativa automática.

Se implementan dos adaptadores detrás de `IncidentTriageGateway`: el baseline determinista `mock-v1` para CI y una integración HTTPS con Gemini para Azure Test. La configuración actual usa el alias `gemini-3.5-flash`; el smoke test del 11/11/2026 informó como versión efectiva `gemini-3.6-flash`. El proveedor puede variar la versión detrás del alias, por lo que el equipo debe repetir evaluaciones ante cambios observados.

El gateway concentra prompt versionado, validación y resiliencia; el adaptador traduce el protocolo REST sin acoplar negocio a un SDK. La API key se inyecta como secreto y nunca se versiona. El mock no tiene costo de inferencia; Gemini puede consumir cuota o generar costo según el plan de la clave. El límite por usuario, el apagado y la salida acotada reducen el consumo accidental.

Alternativas: reglas deterministas son económicas y útiles como baseline, pero cubren menos variantes; integración directa con SDK acopla negocio y proveedor; decisiones autónomas se descartan por impacto y falta de supervisión.

Se conserva como diseño el contrato incident-triage-response.schema.json y el dataset v1 del equipo. Sus nombres damageCategory y humanReviewRequired equivalen conceptualmente a suggestedType y requiresHumanReview del ejemplo de consigna; no son el contrato del endpoint actual. No se cambia ningún contrato externo.

Privacidad: enviar únicamente texto saneado; no reutilizar datos reales en evals. Riesgos: inyección, datos personales, clasificación errónea, consumo y confianza excesiva. Se mitigan con validación, cuotas, revisión y fallback; no se declaran eliminados.

Confirmación humana obligatoria: tipo definitivo, prioridad definitiva, creación y aprobación de obra, asignación de recursos y ejecución. El modelo no posee herramientas para estas operaciones.

Apagado: `AI_TRIAGE_ENABLED=false` evita llamadas y conserva selección manual. `AI_PROVIDER=mock` permite volver al baseline sin retirar código. Si Gemini falla, el timeout, circuit breaker y fallback devuelven revisión manual; no se ejecutan acciones de negocio.
