# Defensa oral - aproximadamente dos minutos

Nuestra propuesta para Obras Públicas ayuda a interpretar solicitudes de mantenimiento que llegan en texto libre. Por ejemplo, ante un bache profundo frente a una escuela, puede sugerir reparación de pavimento y prioridad alta.

La IA solo propone. Una persona revisa la información y confirma o corrige la clasificación. El modelo no crea órdenes, no aprueba obras, no asigna cuadrillas y no ejecuta trabajos. Tampoco reemplaza una inspección profesional.

La arquitectura utiliza un AIGateway, una interfaz que separa el negocio del proveedor. Así podemos usar un proveedor simulado durante las pruebas y cambiarlo después sin acoplar todo el sistema a una empresa. El diseño incorpora un prompt versionado y un contrato JSON con valores permitidos. Si una respuesta no cumple ese contrato, se descarta y se continúa manualmente.

En el backend ya existe una base: gateway HTTP, timeout de cinco segundos y fallback. Probamos el código existente: se reportaron 117 pruebas, sin fallos ni errores, con dos omitidas por falta de Docker. Sin embargo, el asistente actual devuelve texto: no afirmamos que el clasificador estructurado ya esté implementado.

Preparamos veinte casos, incluidos cinco adversariales. Evaluaremos formato, categoría, prioridad, datos personales y resistencia a instrucciones maliciosas. La descripción se trata como dato no confiable; no basta con pedirle al modelo que se comporte bien. También necesitamos sanitización, validación y ausencia de permisos para ejecutar acciones.

No hace falta pagar una API para demostrar el gateway y el fallback: un servidor simulado permite reproducir respuestas y errores. Lo que no demuestra es la calidad de un modelo real. Esa calidad deberá medirse con el dataset antes de habilitarlo. El criterio central es mantener la decisión y la responsabilidad en una persona.
