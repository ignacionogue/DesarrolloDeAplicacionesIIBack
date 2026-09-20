# Creación de órdenes y defensa de Strategy

El caso de uso recibe órdenes manuales, ligadas a proyectos y los dos orígenes HTTP
ya existentes. Las reglas cambian según el origen: MANUAL no tiene proyecto;
PROYECTO requiere una referencia válida. El estado inicial sigue dependiendo de
la cuadrilla: PENDIENTE sin asignación, ASIGNADA con ella.

## Responsabilidades

1. `OrdenTrabajoController` recibe HTTP y valida el DTO.
2. `OrdenTrabajoService` delimita la transacción, resuelve la cuadrilla y solicita
   al resolver la estrategia. Persiste con el repository y responde mediante mapper.
3. `WorkOrderCreationStrategyResolver` selecciona por `origin`. Al arrancar verifica
   que cada origen tenga exactamente una estrategia, evitando selecciones ambiguas.
4. `ManualWorkOrderCreationStrategy` valida que no haya projectId.
5. `ProjectWorkOrderCreationStrategy` exige projectId y consulta el proyecto;
   devuelve 404 si no existe. Crea la asociación JPA, sin guardar la orden.
6. `ExistingOriginWorkOrderCreationStrategy` conserva las altas HTTP de Atención
   Ciudadana e Inspección. No es un consumidor de eventos M6/M7.
7. `OrdenTrabajoMapper` convierte DTO/entidad/respuesta. El repository persiste.

PUT reutiliza la preparación y validación de la estrategia antes de copiar datos
al objeto existente. El objeto temporal no se guarda: se conservan ID, programación,
resultado y estado del flujo. Cambiar explícitamente a MANUAL con projectId null
desvincula el proyecto; mantener PROYECTO exige reenviar projectId.

## Por qué Strategy

Sin Strategy, el Service acumularía condicionales por cada origen, mezclando
consultas a proyectos, reglas manuales y futuras integraciones. Con la interfaz,
el Service invoca `resolve(origin).create(request, crew)` y mantiene estable su
coordinación. Cada variante puede probarse y evolucionar por separado; registrar
una nueva implementación amplía el comportamiento sin agregar otro condicional
al Service. El resolver concentra la selección, no las reglas de cada variante.

No se inventaron payloads ni estrategias de eventos M6/M7. Cuando sus contratos
estén confirmados se definirá cómo adaptar esos eventos al caso de uso y cómo
evitar duplicados; `sourceRequestId` conserva por ahora su semántica anterior.

Las transiciones se mantienen en el Service y en la entidad; Strategy resuelve
el alta según origen, no sustituye la máquina de estados ni la autorización HTTP.
La seguridad valida roles antes de llegar al Controller. Los errores de permisos
son 401/403, de datos 400/404 y de transición 409.

## Pruebas

`WorkOrderFlowTests` cubre alta manual, asociación persistida y visible en GET/lista,
proyecto inexistente, asociaciones inválidas sin escrituras, selección por origen,
compatibilidad anterior, PUT sin pérdida de estado, flujo completo con reapertura
y rechazo de todos los otros roles para cada operación de escritura.
`MigrationTests` verifica V2 → V3 con datos y restricciones sobre H2/PostgreSQL.
`AuthApprovalReviewTests` conserva las regresiones detectadas al revisar develop.
