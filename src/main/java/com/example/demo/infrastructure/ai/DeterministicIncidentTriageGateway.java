package com.example.demo.infrastructure.ai;

import com.example.demo.application.ai.IncidentTextSanitizer;
import com.example.demo.application.ai.IncidentTriageGateway;
import com.example.demo.dto.response.IncidentTriageSuggestion;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.example.demo.dto.response.IncidentTriageSuggestion.DamageCategory.*;
import static com.example.demo.dto.response.IncidentTriageSuggestion.RecommendedResourceType.*;
import static com.example.demo.dto.response.IncidentTriageSuggestion.SuggestedPriority.*;

/** Deterministic delivery provider. It has no network, API key or side effects. */
@Component
@Primary
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "mock", matchIfMissing = true)
public class DeterministicIncidentTriageGateway implements IncidentTriageGateway {
    private final IncidentTextSanitizer sanitizer;

    public DeterministicIncidentTriageGateway(IncidentTextSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    @Override
    public IncidentTriageSuggestion suggest(String sanitizedDescription, String promptVersion) {
        String text = sanitizer.normalized(sanitizedDescription);
        if (has(text, "sin descripcion", "sin ubicacion")
                && !has(text, "vereda", "luminaria", "lampara", "sumidero", "drenaje", "arbol",
                "rama", "fisura", "pared", "grieta", "bache", "pozo", "asfalto", "hundimiento")) {
            return IncidentTriageSuggestion.manual("La descripcion no permite identificar el tipo de daño");
        }
        if ((has(text, "vereda", "baldosa", "cordon") && has(text, "luminaria", "lampara"))) {
            return suggestion("Daño de vereda con riesgo peatonal", SIDEWALK, HIGH, SIDEWALK_CREW, 0.90,
                    "Se prioriza el riesgo peatonal informado");
        }
        if (has(text, "luminaria", "lampara", "poste de alumbrado")) {
            var priority = has(text, "inclinado", "choque") ? CRITICAL : MEDIUM;
            return suggestion("Incidencia de alumbrado publico", STREETLIGHT, priority, ELECTRICAL_CREW, 0.93,
                    "La descripcion refiere a infraestructura de alumbrado");
        }
        if (has(text, "vereda", "baldosa", "cordon")) {
            var priority = has(text, "levantad", "tropiez", "riesgo peatonal") ? HIGH
                    : has(text, "sin riesgo inmediato", "cordon") ? LOW : MEDIUM;
            return suggestion("Daño en vereda", SIDEWALK, priority, SIDEWALK_CREW, 0.91,
                    "La superficie peatonal requiere revision");
        }
        if (has(text, "sumidero", "drenaje", "rejilla", "pluvial")) {
            var priority = has(text, "avenida", "muy transitada", "rejilla rota") ? CRITICAL : HIGH;
            return suggestion("Incidencia de drenaje urbano", DRAINAGE, priority, DRAINAGE_CREW, 0.92,
                    "La descripcion refiere al sistema pluvial");
        }
        if (has(text, "arbol", "rama", "poda")) {
            var priority = has(text, "quebrada", "senda peatonal") ? CRITICAL : LOW;
            return suggestion("Incidencia de arbolado urbano", TREE, priority, ARBORIST, 0.90,
                    "La intervencion requiere personal de arbolado");
        }
        if (has(text, "fisura", "pared", "estructura", "grieta")) {
            var priority = has(text, "fisura", "edificio municipal") ? HIGH : MEDIUM;
            return suggestion("Posible daño estructural", STRUCTURE, priority, STRUCTURAL_INSPECTION, 0.86,
                    "Debe revisarlo personal tecnico antes de decidir");
        }
        if (has(text, "bache", "pozo", "asfalto", "hundimiento")) {
            var priority = has(text, "profundo", "escuela", "hundimiento", "agua alrededor") ? HIGH : MEDIUM;
            return suggestion("Daño en pavimento", PAVEMENT, priority, ROAD_CREW, 0.91,
                    "La descripcion refiere a la calzada");
        }
        return IncidentTriageSuggestion.manual("La descripcion no permite identificar el tipo de daño");
    }

    private static IncidentTriageSuggestion suggestion(String summary,
                                                        IncidentTriageSuggestion.DamageCategory category,
                                                        IncidentTriageSuggestion.SuggestedPriority priority,
                                                        IncidentTriageSuggestion.RecommendedResourceType resource,
                                                        double confidence,
                                                        String reason) {
        return new IncidentTriageSuggestion(summary, category, priority, resource, confidence, true, List.of(reason));
    }

    private static boolean has(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }
}
