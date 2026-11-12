package com.example.demo.application.ai;

import com.example.demo.dto.response.IncidentTriageSuggestion;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class IncidentTriageSchemaValidator {
    private final ObjectMapper json;
    private final Schema schema;

    public IncidentTriageSchemaValidator(ObjectMapper json) {
        this.json = json;
        try {
            var registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
            this.schema = registry.getSchema(SchemaLocation.of(
                    "classpath:ai/schemas/incident-triage-response.schema.json"));
            this.schema.initializeValidators();
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo cargar el schema de IA", error);
        }
    }

    public IncidentTriageSuggestion validateOrFallback(IncidentTriageSuggestion suggestion) {
        try {
            var errors = schema.validate(json.writeValueAsString(suggestion), InputFormat.JSON);
            return errors.isEmpty() ? suggestion
                    : IncidentTriageSuggestion.manual("La respuesta no cumplio el contrato estructurado");
        } catch (Exception invalid) {
            return IncidentTriageSuggestion.manual("La respuesta no pudo validarse");
        }
    }
}
