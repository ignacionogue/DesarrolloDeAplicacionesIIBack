# Declaración de uso de inteligencia artificial

**Proyecto:** M3 Obras Públicas  
**Fecha:** 3 de noviembre de 2026  
**Herramientas utilizadas:** Claude y Codex.

## Uso realizado

- Claude se utilizó como apoyo para analizar alternativas, explicar conceptos y proponer código o documentación.
- Codex se utilizó para inspeccionar repositorios, preparar pruebas, automatizar CI, redactar documentación y ejecutar verificaciones reproducibles.
- Las herramientas no tuvieron autoridad para aprobar entregas, aceptar resultados, fusionar cambios ni desplegar por sí solas.

## Controles para evitar cambios incorrectos o peligrosos

1. Una persona revisa cada diff antes de fusionarlo.
2. Los cambios deben superar pruebas unitarias, integración, navegador y cobertura según corresponda.
3. GitHub Actions repite las verificaciones en un entorno limpio.
4. Gitleaks revisa toda la historia para evitar secretos. Trivy, auditorías de dependencias y ZAP controlan vulnerabilidades.
5. Las credenciales se guardan como secretos o variables locales y nunca se copian en prompts, repositorios, logs ni evidencias.
6. Los contratos externos se comparan con la documentación de cada equipo. Una propuesta de IA no se registra como acuerdo.
7. Los resultados se declaran como PASS, FAIL, BLOQUEADO o NO EJECUTADO según evidencia. La IA no puede convertir una prueba pendiente en aprobada.
8. Los despliegues y la creación del tag final requieren aprobación de DevOps.

## Ejemplo de corrección humana

Una respuesta automática podía concluir que la cantidad de pruebas era suficiente para aprobar. El equipo rechazó esa conclusión porque la consigna también exige mutación, seguridad, resiliencia, performance, trazabilidad, integración real y evidencia en CI. Se agregaron esos controles y se mantuvieron visibles los pendientes.

## Responsabilidad

El equipo conserva la responsabilidad por el código, las pruebas, la documentación y las decisiones entregadas. Claude y Codex funcionaron como herramientas de apoyo y no como revisores finales.
