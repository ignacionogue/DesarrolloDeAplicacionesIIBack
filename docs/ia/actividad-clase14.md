# Actividad integradora Clase 14 — IA aplicada al TPO

**Grupo:** 5 — M3 Obras Públicas  
**Feature propuesta:** Copiloto de clasificación de incidencias  
**Estado:** implementado; deshabilitado por defecto y habilitable en Azure Test mediante configuración segura.

## 1. Propósito y alcance

La feature recibe una descripción sanitizada de un daño urbano y propone categoría, prioridad y tipo de recurso. Un operador de Obras revisa y confirma o corrige la sugerencia. La IA nunca crea una orden, cambia un estado, aprueba una obra, asigna una cuadrilla ni publica un evento.

Usuarios: Personal de Obras e Ingeniero/Arquitecto. No se expone al ciudadano. Beneficio esperado: reducir tiempo de clasificación sin delegar decisiones administrativas.

## 2. Arquitectura

`Frontend → IncidentTriageController → IncidentTriageService → IncidentTriageGateway → Gemini REST API`

Componentes transversales: `PromptRepository`, sanitizador de PII, validador JSON Schema, métricas, timeout de 5 segundos, circuit breaker, fallback manual y feature flag `AI_TRIAGE_ENABLED=false`.

Modelo configurado en Test: alias `gemini-3.5-flash`, nivel de pensamiento mínimo, respuesta JSON estructurada y prompt `incident-triage.v1`. El smoke test informó `gemini-3.6-flash` como versión efectiva. Se mantiene `mock-v1` para evaluaciones deterministas y CI. Ante cualquier cambio de modelo, prompt o parámetros se repite el eval set.

## 3. Contrato

Entrada permitida: texto sanitizado y metadatos no personales mínimos. Salida: JSON estricto según `incident-triage-response.schema.json`. `humanReviewRequired` siempre vale `true`.

## 4. Evaluación

Dataset dorado: 20 casos, incluidos 5 adversariales. Graders deterministas para schema, enums, categoría, prioridad, recurso y ausencia de PII/instrucciones; revisión humana de una muestra para claridad de las razones.

Umbrales de merge: schema válido 100%; resistencia a los 5 casos adversariales 100%; fuga de PII 0; categoría ≥90%; prioridad ≥85%; recurso ≥90%; fallback 100%; p95 ≤5 segundos. Si falla un umbral, no se habilita ni se mergea un cambio del prompt/modelo.

## 5. Gobernanza

- Responsable funcional: PO de M3.
- Responsable técnico: Backend M3.
- Aprobación de cada sugerencia: operador autenticado.
- Datos enviados: descripción sin nombres, DNI, teléfono, correo, token, adjuntos ni coordenadas exactas innecesarias.
- Persistencia: guardar solo sugerencia final, aceptación/corrección, versión de prompt/modelo, latencia y tokens; no guardar el prompt con PII ni respuestas crudas innecesarias.
- Apagado: `AI_TRIAGE_ENABLED=false`; el formulario vuelve a selección manual sin bloquear el flujo.
- Proveedor/modelo: propuesta sujeta a ADR, disponibilidad y presupuesto. No se versionan API keys.

## 6. Riesgos OWASP LLM y mitigación

1. Prompt injection: datos delimitados, prompt de sistema separado, sin herramientas/acciones, evals adversariales.
2. Sensitive information disclosure: sanitización, minimización, lista de campos prohibidos, cero secretos y test de fuga.
3. Improper output handling / excessive agency: Structured Outputs, JSON Schema, enums cerrados, sin acceso a DB o RabbitMQ y confirmación humana.

## 7. Uso de IA durante el desarrollo

El equipo usó Claude y Codex como herramientas de apoyo. Cada diff se revisa, las afirmaciones se verifican con pruebas/CI y los secretos no se comparten. La IA no puede aprobar un resultado, mergear o desplegar sin intervención humana.
