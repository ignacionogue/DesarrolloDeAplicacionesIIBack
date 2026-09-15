package com.example.demo.repository;

import com.example.demo.model.EstadoActividad;
import com.example.demo.model.EstadoAprobacion;
import com.example.demo.model.ProyectoObra;
import org.springframework.data.jpa.domain.Specification;

/**
 * Construye los filtros de GET /api/public-works/projects (punto 17).
 * 'status' filtra sobre el status combinado: si coincide con un valor de
 * EstadoAprobacion se filtra por ese campo; si coincide con un valor de
 * EstadoActividad se filtra por proyectos APROBADOS con ese activityStatus.
 */
public final class ProyectoObraSpecifications {

    private ProyectoObraSpecifications() {
    }

    public static Specification<ProyectoObra> withFilters(String search, String status) {
        Specification<ProyectoObra> spec = Specification.allOf();

        if (search != null && !search.isBlank()) {
            String like = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("description")), like)
            ));
        }

        if (status != null && !status.isBlank()) {
            spec = spec.and(byStatus(status));
        }

        return spec;
    }

    private static Specification<ProyectoObra> byStatus(String status) {
        String normalized = status.trim().toUpperCase();

        EstadoAprobacion estadoAprobacion = findEstadoAprobacion(normalized);
        if (estadoAprobacion != null) {
            return (root, query, cb) -> cb.equal(root.get("approvalStatus"), estadoAprobacion);
        }

        return findEstadoActividad(normalized)
                .<Specification<ProyectoObra>>map(estado -> (root, query, cb) -> cb.and(
                        cb.equal(root.get("approvalStatus"), EstadoAprobacion.APROBADO),
                        cb.equal(root.get("activityStatus"), estado)
                ))
                // status desconocido: no rompe la busqueda, simplemente no matchea nada
                .orElse((root, query, cb) -> cb.disjunction());
    }

    private static EstadoAprobacion findEstadoAprobacion(String value) {
        try {
            return EstadoAprobacion.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static java.util.Optional<EstadoActividad> findEstadoActividad(String value) {
        try {
            return java.util.Optional.of(EstadoActividad.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return java.util.Optional.empty();
        }
    }
}
