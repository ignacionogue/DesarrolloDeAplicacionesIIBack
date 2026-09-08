package com.example.demo.dto.response;

import java.util.List;

/**
 * Respuesta del unico endpoint de recursos pedido explicitamente (punto 16):
 * GET /api/public-works/resources. Agrupa los catalogos de cuadrillas,
 * materiales y maquinaria disponibles para asignar a Ordenes de Trabajo.
 */
public record ResourcesSummaryResponse(
        List<CuadrillaResponse> crews,
        List<MaterialResponse> materials,
        List<MaquinariaResponse> machinery
) {
}
