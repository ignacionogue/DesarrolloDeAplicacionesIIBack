# ADR-IA-001: clasificación asistida mediante gateway

Estado: PROPOSED. Fecha: 11/11/2026. Responsables de aceptación: equipo M3.

La interpretación de reclamos en texto libre puede beneficiarse de IA, pero una clasificación errónea puede afectar la atención. Se elige una sugerencia revisable, nunca una acción administrativa automática.

Para la entrega se propone un proveedor simulado determinista, versión mock-v1, detrás de AiGateway. Una alternativa futura es un proveedor LLM HTTP reemplazable. La propuesta anterior de un modelo comercial permanece sin habilitar: modelo y versión concretos requieren disponibilidad, privacidad, presupuesto y evaluación antes de aprobarse. No se afirma haber contratado ni probado ese modelo.

El gateway concentra prompt versionado, validación y resiliencia; el adaptador traduce el protocolo. Evita acoplar negocio a un SDK. No se agrega una dependencia ni clave para esta entrega. Costo de inferencia del mock: cero; no implica costo cero de desarrollo o infraestructura. Para un proveedor real se estimará solicitudes × (tokens de entrada × tarifa de entrada + tokens de salida × tarifa de salida), usando tarifas verificadas al decidir.

Alternativas: reglas deterministas son económicas y útiles como baseline, pero cubren menos variantes; integración directa con SDK acopla negocio y proveedor; decisiones autónomas se descartan por impacto y falta de supervisión.

Se conserva como diseño el contrato incident-triage-response.schema.json y el dataset v1 del equipo. Sus nombres damageCategory y humanReviewRequired equivalen conceptualmente a suggestedType y requiresHumanReview del ejemplo de consigna; no son el contrato del endpoint actual. No se cambia ningún contrato externo.

Privacidad: enviar únicamente texto saneado; no reutilizar datos reales en evals. Riesgos: inyección, datos personales, clasificación errónea, consumo y confianza excesiva. Se mitigan con validación, cuotas, revisión y fallback; no se declaran eliminados.

Confirmación humana obligatoria: tipo definitivo, prioridad definitiva, creación y aprobación de obra, asignación de recursos y ejecución. El modelo no posee herramientas para estas operaciones.

Apagado futuro: AI_TRIAGE_ENABLED=false evita llamadas y conserva selección manual. Apagado disponible hoy: AI_GATEWAY_URL vacía. El mock permite demostrar integración y fallos sin dinero ni datos externos; no demuestra calidad de un LLM real. Antes de habilitarlo se exige eval set aprobado, revisión humana y CI verde.
