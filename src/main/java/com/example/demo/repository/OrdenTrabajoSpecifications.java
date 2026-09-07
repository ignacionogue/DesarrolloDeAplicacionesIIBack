package com.example.demo.repository;

import com.example.demo.model.EstadoOT;
import com.example.demo.model.OrdenTrabajo;
import com.example.demo.model.OrigenOT;
import com.example.demo.model.PrioridadOT;
import org.springframework.data.jpa.domain.Specification;

public final class OrdenTrabajoSpecifications {

    private OrdenTrabajoSpecifications() {
    }

    public static Specification<OrdenTrabajo> withFilters(String search, String status,
                                                            String priority, String origin) {
        Specification<OrdenTrabajo> spec = Specification.allOf();
        if (search != null && !search.isBlank()) {
            String like = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("description")), like),
                    cb.like(cb.lower(root.get("location")), like)
            ));
        }

        EstadoOT estado = parseOrNull(EstadoOT.class, status);
        if (estado != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), estado));
        }

        PrioridadOT prioridad = parseOrNull(PrioridadOT.class, priority);
        if (prioridad != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("priority"), prioridad));
        }

        OrigenOT origenOT = parseOrNull(OrigenOT.class, origin);
        if (origenOT != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("origin"), origenOT));
        }

        return spec;
    }

    private static <E extends Enum<E>> E parseOrNull(Class<E> enumClass, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumClass, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            // filtro con valor desconocido: se ignora en vez de romper el listado
            return null;
        }
    }
}
