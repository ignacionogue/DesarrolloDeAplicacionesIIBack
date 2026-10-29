# Contrato de eventos M3 Obras Públicas v0.6

**Fecha:** 20 de octubre de 2026, revisión M2 v1.78

**Estado:** revisión bilateral

**Versión de eventos:** `1.0`

**Propietario:** M3 Obras Públicas, Infraestructura y Mantenimiento Urbano

Este documento es la versión vigente de M3 para revisión con M1, M2, M6, M7 y Core. Reemplaza como documento externo de trabajo a v0.5. Distingue contratos acordados de ajustes propuestos; no declara completada la implementación RabbitMQ.

## 1. Convenciones del Core

Todo evento publicado por M3 utiliza este envelope, sin campos extra:

```json
{
  "eventId": "0cde6a3a-6e47-4e53-b47c-4cf8a4b90612",
  "eventType": "nombreEnCamelCase",
  "eventVersion": "1.0",
  "occurredAt": "2026-10-20T12:00:00-03:00",
  "sourceModule": "obras",
  "correlationId": "df9684f7-5481-4cc0-93a5-5397f29403ea",
  "data": {}
}
```

- `eventId` es UUID v4 y se reutiliza al reenviar el mismo hecho.
- `eventType` usa inglés y `camelCase`.
- `eventVersion` es `1.0`.
- `occurredAt` usa ISO 8601/RFC 3339 con `Z` u offset.
- `sourceModule` es el slug del productor. Para M3 es `obras`.
- `correlationId` se publica siempre: se hereda del evento recibido o se crea un UUID nuevo para una acción propia.
- `causationId` se incluye únicamente cuando el evento nace directamente del handler de otro evento. Su valor es el `eventId` causal. Si no hay causa, la clave se omite; no se envía `null`.
- Todo identificador empleado para correlacionar o buscar una entidad entre módulos es UUID.
- Una referencia humana se transporta en un campo diferente y opcional, por ejemplo `workOrderNumber`.
- La entrega es `at-least-once`, no garantiza orden y exige deduplicar por `eventId`.
- El consumidor compara tiempos de negocio y estado persistido, no el orden de llegada.

## 2. Catálogo de M3

### Eventos recibidos

- M2: `ticketUpdated`.
- M6: `infrastructureRepairRequested`, `containerDamaged`, `treeRiskDetected`.
- M7: `streetClosureApproved`, `streetClosureRejected`, `streetClosureEnded`, `roadAccidentRegistered`.
- M1: `casefileResolved`, con casing todavía pendiente frente a `caseFileResolved` registrado por Core.

### Eventos publicados

- Hacia M2: `updateTicketStatus`.
- Hacia M6: `workOrderScheduled`, `workOrderCompleted`.
- Hacia M7: `streetClosureRequested`.
- Hacia M1: `publicWorksProjectApproved`, `publicWorksProjectCompleted`.

## 3. Integración con M2 Atención Ciudadana

### 3.1 Consumo de ticketUpdated

M3 recibe el evento desde `q.obras`, valida el envelope y `data`, y aplica estas reglas:

- Ejecuta lógica únicamente si `data.responsibleAreaId` es `M3`.
- Un evento válido de otra área se confirma e ignora sin persistir su contenido de negocio.
- `ROUTED` abre o actualiza la gestión del mismo `ticketId`; no crea un segundo ticket.
- En `ROUTED` se leen `category`, `subcategory` y `details.routing.reassignment`.
- Los mensajes de reclasificación con `purpose=RECLASSIFICATION` no habilitan trabajo de M3; se espera el `ROUTED` posterior.
- `INFORMATION_PROVIDED` sin `purpose=RECLASSIFICATION` incorpora la respuesta y sus adjuntos a la gestión existente y reanuda la evaluación sin crear otro ticket u orden.
- `CANCELLED` actualiza la proyección local del ticket y bloquea nuevas acciones originadas por ese reclamo; una orden ya iniciada no se cancela automáticamente sin aplicar las reglas propias de M3.
- `REOPENED` reactiva la gestión vinculada y reutiliza el mismo `ticketId`; no crea una segunda orden por el mismo caso.
- `PRIORITY_CHANGED` actualiza la prioridad de la proyección local y dispara la reevaluación de planificación cuando corresponda.
- `ESCALATION_CHANGED` actualiza la información de escalamiento y la visibilidad operativa sin cambiar por sí solo el estado de una orden.
- Se deduplica por `eventId` y se compara `data.updatedAt` con la última actualización aplicada.
- M3 no consume `ticketCreated`.

Ejemplo recibido de M2:

```json
{
  "eventId": "61e12153-345e-4c9c-8b1a-724da1c75191",
  "eventType": "ticketUpdated",
  "eventVersion": "1.0",
  "occurredAt": "2026-10-20T11:10:02-03:00",
  "sourceModule": "atencion-ciudadana",
  "correlationId": "f80baa55-02b2-4cb0-bd51-d97c0c54e948",
  "data": {
    "ticketId": "550e8400-e29b-41d4-a716-446655440000",
    "publicId": "TK-2026-000123",
    "citizenId": "8f14e45f-ea5e-4e5d-bd3f-1c71f4f6a001",
    "isAnonymous": false,
    "responsibleAreaId": "M3",
    "updateType": "ROUTED",
    "currentStatus": "ROUTED",
    "currentPriority": "HIGH",
    "publicMessage": "El ticket fue derivado a Obras Públicas.",
    "details": {
      "routing": {
        "requestType": "Informar un bache",
        "category": "Calles, veredas e infraestructura urbana",
        "subcategory": "Calles y Pavimento",
        "ticketType": "COMPLAINT",
        "summary": "Bache profundo en la calzada",
        "description": "Bache que representa un riesgo para el tránsito.",
        "formData": {},
        "location": {"addressLine": "Av. Libertad 1240"},
        "reassignment": false
      }
    },
    "updatedAt": "2026-10-20T11:10:00-03:00"
  }
}
```

### 3.2 Publicación de updateTicketStatus

M2 es el único consumidor funcional y la única autoridad sobre estado, prioridad y categoría. M3 informa hechos mediante `STARTED`, `PROGRESS`, `INFORMATION_REQUIRED`, `RETURNED`, `RESOLVED` o `REJECTED`.

Campos comunes obligatorios de `data`: `ticketId`, `updateType`, `updatedBy` y `updateOccurredAt`.

- `STARTED` debe preceder a `PROGRESS` cuando el ticket está en `ROUTED`.
- `PROGRESS` aporta `progress`, un mensaje o adjuntos.
- `INFORMATION_REQUIRED` exige `details.informationRequest.messageForCitizen`.
- `RETURNED` exige `details.returnInfo.reasonCode` y `publicMessage` o `internalMessage`.
- `RESOLVED` exige `details.resolution.type` y `publicMessage`; se emite solo cuando la intervención terminó.
- `REJECTED` exige `details.cancellation.reasonCode` y `publicMessage` o `internalMessage`.
- `updatedBy.id` es UUID cuando `updatedBy.type=EXTERNAL_USER`; únicamente puede ser `null` para `SYSTEM` o cuando no existe una persona concreta identificable.
- La agenda o reprogramación se informa mediante `publicMessage`; v1.78 no define calendario estructurado.

Ejemplo publicado por M3:

```json
{
  "eventId": "6f2cd81f-3593-46ff-b9ed-9896dc4f157c",
  "eventType": "updateTicketStatus",
  "eventVersion": "1.0",
  "occurredAt": "2026-10-20T11:45:03-03:00",
  "sourceModule": "obras",
  "correlationId": "31b1e59e-17c3-401d-b996-8be08f8c3530",
  "data": {
    "ticketId": "550e8400-e29b-41d4-a716-446655440000",
    "updateType": "RESOLVED",
    "publicMessage": "El bache fue reparado y la calzada quedó habilitada.",
    "details": {"resolution": {"type": "ACTION_COMPLETED"}},
    "attachments": [{
      "fileName": "reparacion-final.jpg",
      "contentType": "image/jpeg",
      "url": "https://files.example/obras/reparacion-final.jpg",
      "sizeBytes": 251904
    }],
    "updatedBy": {"type": "EXTERNAL_USER", "id": "3b9f1c52-6a7e-4d08-9c41-5e2a8d7f0b13"},
    "updateOccurredAt": "2026-10-20T11:45:00-03:00"
  }
}
```

## 4. Integración con M6 Ambiente

El contrato funcional M3-M6 está acordado. M6 usa `sourceModule=ambiente`, publica `correlationId` siempre y omite `causationId` cuando no existe una causa directa.

### 4.1 infrastructureRepairRequested

Campos obligatorios: `requestId` UUID, `damageType`, `severity`, `location`, `detectedIn`, `publicSafetyRisk` y `requestedAt`. `ticketId` es opcional.

```json
{
  "eventId": "6f7a8b9c-0d1e-4f2a-b14c-5d6e7f8091a2",
  "eventType": "infrastructureRepairRequested",
  "eventVersion": "1.0",
  "occurredAt": "2026-10-20T10:10:00-03:00",
  "sourceModule": "ambiente",
  "correlationId": "b6c7d8e9-f0a1-4b2c-9d4e-5f60718293a4",
  "data": {
    "requestId": "4f5a6b7c-8d9e-4f0a-9b1c-d3e4f5a6b7c8",
    "damageType": "BLOCKED_DRAIN",
    "severity": "HIGH",
    "location": {"street": "Calle Belgrano 560", "lat": -34.61, "lng": -58.38},
    "detectedIn": "7e8f9a0b-1c2d-4e3f-a4b5-c6d7e8f9a0b1",
    "publicSafetyRisk": true,
    "requestedAt": "2026-10-20T10:10:00-03:00"
  }
}
```

M3 conserva `requestId` como `sourceRequestId` y puede responder con `workOrderScheduled` y `workOrderCompleted`.

### 4.2 containerDamaged

M3 procesa únicamente `requiresPublicWorks=true`. Es un aviso sin respuesta hacia M6.

```json
{
  "eventId": "a1b2c3d4-0001-4b8c-9d0e-1f2a3b4c5d01",
  "eventType": "containerDamaged",
  "eventVersion": "1.0",
  "occurredAt": "2026-10-20T11:40:00-03:00",
  "sourceModule": "ambiente",
  "correlationId": "d2e3f4a5-b6c7-4d8e-9f0a-1b2c3d4e5f60",
  "data": {
    "containerId": "8f9a0b1c-2d3e-4f4a-b5c6-d7e8f9a0b1c2",
    "containerCode": "CT-0457",
    "zoneId": "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e",
    "location": {"street": "Av. Libertador 1200", "lat": -34.58, "lng": -58.41},
    "damageType": "STRUCTURAL",
    "severity": "HIGH",
    "requiresPublicWorks": true,
    "detectedAt": "2026-10-20T11:40:00-03:00"
  }
}
```

### 4.3 treeRiskDetected

M6 publica solo riesgos `HIGH` o `CRITICAL`; M3 actúa cuando `requiresPublicWorks=true`. Es un aviso sin respuesta hacia M6.

```json
{
  "eventId": "e2f3a4b5-c6d7-4e8f-9a10-b2c3d4e5f607",
  "eventType": "treeRiskDetected",
  "eventVersion": "1.0",
  "occurredAt": "2026-10-20T09:15:00-03:00",
  "sourceModule": "ambiente",
  "correlationId": "e3f4a5b6-c7d8-4e9f-8a1b-2c3d4e5f6071",
  "data": {
    "treeId": "9a0b1c2d-3e4f-4a5b-86c7-e8f9a0b1c2d3",
    "surveyCode": "REL-2026-0132",
    "zoneId": "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e",
    "location": {"street": "Calle Mitre 855"},
    "riskLevel": "HIGH",
    "riskType": "FALLING_BRANCH",
    "healthStatus": "WEAKENED",
    "suggestedIntervention": "SAFETY_PRUNING",
    "requiresStreetClosure": true,
    "requiresPublicWorks": true,
    "surveyedAt": "2026-10-20T09:15:00-03:00"
  }
}
```

### 4.4 workOrderScheduled

`sourceRequestId` es el `requestId` recibido. M3 propone UUID técnico en `workOrderId` y una referencia humana opcional en `workOrderNumber`; M6 debe validar este ajuste.

```json
{
  "eventId": "9c0d1e2f-3a4b-4c5d-8e7f-8091a2b3c4d5",
  "eventType": "workOrderScheduled",
  "eventVersion": "1.0",
  "occurredAt": "2026-10-20T12:00:00-03:00",
  "sourceModule": "obras",
  "correlationId": "b6c7d8e9-f0a1-4b2c-9d4e-5f60718293a4",
  "causationId": "6f7a8b9c-0d1e-4f2a-b14c-5d6e7f8091a2",
  "data": {
    "sourceRequestId": "4f5a6b7c-8d9e-4f0a-9b1c-d3e4f5a6b7c8",
    "workOrderId": "1bb68ec6-56b2-43a8-956b-6949ac740b8a",
    "workOrderNumber": "OT-2026-0098",
    "estimatedDuration": "PT4H"
  }
}
```

### 4.5 workOrderCompleted

Se emite exclusivamente para `infrastructureRepairRequested`, no para `containerDamaged` ni `treeRiskDetected`.

```json
{
  "eventId": "0d1e2f3a-4b5c-4d6e-9f8a-91a2b3c4d5e6",
  "eventType": "workOrderCompleted",
  "eventVersion": "1.0",
  "occurredAt": "2026-10-20T15:30:00-03:00",
  "sourceModule": "obras",
  "correlationId": "b6c7d8e9-f0a1-4b2c-9d4e-5f60718293a4",
  "data": {
    "sourceRequestId": "4f5a6b7c-8d9e-4f0a-9b1c-d3e4f5a6b7c8",
    "workOrderId": "1bb68ec6-56b2-43a8-956b-6949ac740b8a",
    "workOrderNumber": "OT-2026-0098",
    "outcome": "Se destapó el sumidero y se verificó el escurrimiento.",
    "completedAt": "2026-10-20T15:30:00-03:00",
    "attachments": [{
      "fileName": "sumidero-destapado.jpg",
      "contentType": "image/jpeg",
      "url": "https://files.example/obras/sumidero-destapado.jpg"
    }]
  }
}
```

## 5. Integración con M7 Tránsito

El contrato funcional v1.0 está acordado. Los cambios UUID de esta versión requieren confirmación bilateral.

### 5.1 streetClosureRequested

El envelope usa `sourceModule=obras`. Dentro de `data`, `sourceModule=M3`. `sourceRef` contiene el UUID de la orden; `workOrderNumber` es visible y opcional.

```json
{
  "eventId": "89cb2fd1-1af7-45d5-a2ab-c538d14f5210",
  "eventType": "streetClosureRequested",
  "eventVersion": "1.0",
  "occurredAt": "2026-10-20T15:00:00-03:00",
  "sourceModule": "obras",
  "correlationId": "78cff4cc-3cd0-4f3f-b0f7-78ec2b15cf0b",
  "data": {
    "closureRequestId": "5a51284e-8fbe-4ab2-8db4-823d37c27d12",
    "sourceModule": "M3",
    "sourceRef": "1bb68ec6-56b2-43a8-956b-6949ac740b8a",
    "workOrderNumber": "OT-2026-0098",
    "reason": "Reparación de calzada",
    "affectedSections": [{
      "street": "Av. Rivadavia",
      "from": "Av. La Plata",
      "to": "Muñiz",
      "direction": "BOTH"
    }],
    "requestedFrom": "2026-10-21T08:00:00-03:00",
    "requestedTo": "2026-10-21T18:00:00-03:00",
    "closureType": "TOTAL",
    "requestedAt": "2026-10-20T15:00:00-03:00"
  }
}
```

Una prórroga agrega `previousClosureRequestId` con el UUID de la solicitud anterior y utiliza un nuevo `closureRequestId` y `eventId`.

### 5.2 streetClosureApproved

M7 conserva el `correlationId` y usa el `eventId` solicitado como `causationId`. `endDate` autorizado prevalece sobre `requestedTo`.

```json
{
  "eventId": "7acd8bce-adc9-4310-a390-28daf0956e8b",
  "eventType": "streetClosureApproved",
  "eventVersion": "1.0",
  "occurredAt": "2026-10-20T15:10:00-03:00",
  "sourceModule": "transito",
  "correlationId": "78cff4cc-3cd0-4f3f-b0f7-78ec2b15cf0b",
  "causationId": "89cb2fd1-1af7-45d5-a2ab-c538d14f5210",
  "data": {
    "streetClosureId": "c819cfbc-9848-43cc-9172-e79201009126",
    "closureRequestId": "5a51284e-8fbe-4ab2-8db4-823d37c27d12",
    "requestingModule": "obras",
    "startDate": "2026-10-21T08:00:00-03:00",
    "endDate": "2026-10-21T18:00:00-03:00",
    "affectedSections": [{"street": "Av. Rivadavia", "from": "Av. La Plata", "to": "Muñiz", "direction": "BOTH"}],
    "detours": [{"street": "Av. Rivadavia", "alternateRoute": "Av. Independencia"}],
    "conditions": ["Mantener acceso para emergencias"]
  }
}
```

### 5.3 streetClosureRejected

```json
{
  "eventId": "4253152c-2733-465d-b90c-c45a60921fb8",
  "eventType": "streetClosureRejected",
  "eventVersion": "1.0",
  "occurredAt": "2026-10-20T15:20:00-03:00",
  "sourceModule": "transito",
  "correlationId": "78cff4cc-3cd0-4f3f-b0f7-78ec2b15cf0b",
  "causationId": "89cb2fd1-1af7-45d5-a2ab-c538d14f5210",
  "data": {
    "closureRequestId": "5a51284e-8fbe-4ab2-8db4-823d37c27d12",
    "rejectionReason": "El corte afectaría una vía principal durante horario pico.",
    "requestingModule": "obras"
  }
}
```

### 5.4 streetClosureEnded

El nombre correcto es `streetClosureEnded`. Core todavía debe corregir o confirmar la declaración histórica `streetClousureEnded`. El evento conserva el hilo y omite `causationId` cuando el cierre es posterior o automático.

```json
{
  "eventId": "e7ad58f0-b46d-4760-b68a-d88e04bdc09d",
  "eventType": "streetClosureEnded",
  "eventVersion": "1.0",
  "occurredAt": "2026-10-21T18:00:00-03:00",
  "sourceModule": "transito",
  "correlationId": "78cff4cc-3cd0-4f3f-b0f7-78ec2b15cf0b",
  "data": {
    "streetClosureId": "c819cfbc-9848-43cc-9172-e79201009126",
    "closureRequestId": "5a51284e-8fbe-4ab2-8db4-823d37c27d12",
    "completionDateTime": "2026-10-21T18:00:00-03:00",
    "notes": "La circulación fue restablecida normalmente."
  }
}
```

### 5.5 roadAccidentRegistered

M7 lo entrega a M3 únicamente cuando existe daño de infraestructura. M3 solicita que `accidentId` sea UUID; una referencia visible puede agregarse como `accidentNumber`.

Los campos `inspectorId`, `ownerNationalId` y `nationalId` permanecen con el formato recibido de M7. Antes de congelar este payload, M7 debe confirmar si `inspectorId` será UUID y justificar la necesidad de transportar los DNI. M3 no los usa como claves técnicas de correlación y debe minimizar su persistencia y exposición en logs.

```json
{
  "eventId": "b95adb2d-82e8-4f71-96aa-c85804759eac",
  "eventType": "roadAccidentRegistered",
  "eventVersion": "1.0",
  "occurredAt": "2026-10-20T18:40:00-03:00",
  "sourceModule": "transito",
  "correlationId": "a450c5f5-b2f8-4de1-b4d2-acde1e84fe38",
  "data": {
    "accidentId": "8f8d5a28-26d9-431f-9d31-ded3fc114ec6",
    "accidentNumber": "ACC-5021",
    "accidentDateTime": "2026-10-20T18:30:00-03:00",
    "location": {"street": "Av. Rivadavia", "crossStreet": "Medrano", "latitude": -34.6118, "longitude": -58.4214},
    "involvesInfrastructureDamage": true,
    "damageDescription": "Semáforo dañado por impacto.",
    "vehicles": [{"licensePlate": "AC456EF", "ownerNationalId": "38765432"}],
    "persons": [{"nationalId": "38765432", "injuryLevel": "MINOR"}],
    "inspectorId": "INS-124"
  }
}
```

## 6. Integración con M1 Expedientes

M1 confirmó que consume `publicWorksProjectApproved` y `publicWorksProjectCompleted`. El nombre `casefileResolved` y su envelope continúan pendientes frente a `caseFileResolved` registrado por Core. Los ejemplos siguientes son propuesta M3 alineada al envelope y a UUID; requieren confirmación de M1.

### 6.1 publicWorksProjectApproved

```json
{
  "eventId": "d2b5f98d-856f-49b9-8857-8f58952d6aae",
  "eventType": "publicWorksProjectApproved",
  "eventVersion": "1.0",
  "occurredAt": "2026-10-20T13:00:00-03:00",
  "sourceModule": "obras",
  "correlationId": "9a21f78c-dc9e-4a76-93c6-d9b3eef0805c",
  "data": {
    "publicWorksProjectId": "7b745d35-536d-4387-a722-cb690586305d",
    "projectNumber": "OBRA-8832",
    "name": "Renovación Plaza Central",
    "approvedAt": "2026-10-20T13:00:00-03:00"
  }
}
```

### 6.2 publicWorksProjectCompleted

```json
{
  "eventId": "c29f0465-eacf-47d2-b149-a3d7b4afe785",
  "eventType": "publicWorksProjectCompleted",
  "eventVersion": "1.0",
  "occurredAt": "2026-12-20T17:00:00-03:00",
  "sourceModule": "obras",
  "correlationId": "9a21f78c-dc9e-4a76-93c6-d9b3eef0805c",
  "data": {
    "publicWorksProjectId": "7b745d35-536d-4387-a722-cb690586305d",
    "projectNumber": "OBRA-8832",
    "completedAt": "2026-12-20T17:00:00-03:00",
    "finalBudget": 19500000
  }
}
```

### 6.3 casefileResolved recibido

```json
{
  "eventId": "74bad23f-80bc-4fd4-9c61-8f514a60cc5e",
  "eventType": "casefileResolved",
  "eventVersion": "1.0",
  "occurredAt": "2026-10-20T11:45:00-03:00",
  "sourceModule": "ciudadanos",
  "correlationId": "9a21f78c-dc9e-4a76-93c6-d9b3eef0805c",
  "data": {
    "caseFileId": "d40e20ba-e2a8-4dd0-a11f-b6d6b58c68a8",
    "caseFileNumber": "EXP-2026-001042",
    "state": "resolved",
    "resolution": "approved",
    "externalReference": {
      "type": "PUBLIC_WORKS_PROJECT",
      "id": "7b745d35-536d-4387-a722-cb690586305d"
    },
    "resolutionSummary": "Se autoriza la ejecución de la obra."
  }
}
```

## 7. Reglas de procesamiento

- Validar envelope, `eventType`, `eventVersion` y `data` antes de ejecutar lógica.
- Deduplicar por `eventId` con Inbox persistente.
- Publicar mediante Outbox transaccional.
- Confirmar e ignorar los eventos válidos que no correspondan a M3.
- Registrar y confirmar las transiciones inválidas; no repetir indefinidamente un error de negocio.
- No depender del orden de entrega.
- No incluir binarios en eventos; los adjuntos transportan URL y metadatos.
- No exponer secretos, tokens, notas privadas ni datos internos fuera del contrato.
- Frontend no se conecta a Core ni a RabbitMQ; opera únicamente mediante la API de M3.

## 8. Confirmaciones solicitadas

### M2

- Contrato funcional `AGREED` contra Eventos v1.78. M2 validó los ejemplos `ticketUpdated/ROUTED` y `updateTicketStatus/RESOLVED` luego de corregir `updatedBy.id` a UUID.
- El alcance del `jsonSchema` registrado y los metadatos agregados al mensaje continúan pendientes de Core, no de M2.

### M6

- Validar los schemas M3 de `workOrderScheduled` y `workOrderCompleted`.
- Confirmar `workOrderId` UUID y `workOrderNumber` opcional.
- Ejecutar la prueba `requestId -> sourceRequestId`.

### M7

- Confirmar `sourceRef` UUID y `workOrderNumber` opcional.
- Confirmar `accidentId` UUID y `accidentNumber` opcional.
- Confirmar `inspectorId` como UUID o documentar su tipo definitivo, y justificar si `ownerNationalId` y `nationalId` deben circular por el evento conforme al principio de minimización de datos.
- Confirmar con Core el registro correcto de `streetClosureEnded`.

### M1

- Confirmar `casefileResolved` o `caseFileResolved`.
- Confirmar los tres payloads adaptados a Core y UUID.

### Core

- Entregar conexiones y secretos del ambiente compartido de Test por canal privado.
- Confirmar si el schema registrado valida `data` o el evento completo.
- Resolver `ticketCreated`, el casing de M1 y la declaración histórica `streetClousureEnded` en `q.obras`.
- Ejecutar la prueba real por `muni.inbox` y `q.obras`.

## 9. Estado de implementación

Este documento define contratos. Al 20 de octubre, el backend M3 todavía no posee el adaptador RabbitMQ/Core, Inbox/Outbox ni los handlers de todos estos eventos. La publicación del contrato no equivale a una integración desplegada o probada.

## 10. Fuentes y cambios de v0.6

- M2 Eventos v1.78 y paquete de JSON Schema recibido el 20 de octubre.
- Aclaración M2 sobre UUID, correlación, causalidad y autoridad del ticket.
- M6 Acuerdo de Eventos y Ejemplos de sobres, versión del 20 de octubre.
- M7 Payloads actualizados Core v4, vigente desde el 19 de octubre.
- Acuerdos M1-M3 recibidos el 11 de octubre.

Cambios principales: sustitución de M2 v1.75 por v1.78; `correlationId` obligatorio al publicar; omisión de `causationId` sin causa; UUID para referencias intermodulares y para `updatedBy.id` cuando identifica una persona; unificación del ejemplo `workOrderNumber=OT-2026-0098`; comportamiento de M3 ante `INFORMATION_PROVIDED`, `CANCELLED`, `REOPENED`, `PRIORITY_CHANGED` y `ESCALATION_CHANGED`; revisión de identificadores y DNI del payload de M7; nuevos pendientes bilaterales claramente identificados.
