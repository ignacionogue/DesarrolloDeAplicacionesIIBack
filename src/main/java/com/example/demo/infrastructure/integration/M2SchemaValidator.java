package com.example.demo.infrastructure.integration;

import com.example.demo.application.events.*;
import com.networknt.schema.*;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class M2SchemaValidator implements TicketEventValidation {
    private final Map<String, Schema> schemas;
    private final EventCodec codec;
    public M2SchemaValidator(EventCodec codec) {
        this.codec = codec;
        var registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemaIdResolvers(resolvers -> resolvers.mapPrefix(
                        "https://municipalidad-uade.example/m2/events/v1.78/", "classpath:contracts/m2-v1.78/")));
        var types = new java.util.HashMap<String, Schema>();
        for (String type : java.util.List.of("ticketCreated", "ticketUpdated", "updateTicketStatus")) {
            var schema = registry.getSchema(SchemaLocation.of("https://municipalidad-uade.example/m2/events/v1.78/" + type + ".schema.json"));
            schema.initializeValidators();
            types.put(type, schema);
        }
        schemas = Map.copyOf(types);
    }
    public void validate(EventEnvelope event) {
        var schema = schemas.get(event.eventType());
        if (schema == null || !schema.validate(codec.encode(event), InputFormat.JSON,
                context -> context.executionConfig(config -> config.formatAssertionsEnabled(true))).isEmpty())
            throw new InvalidEventException();
    }
}
