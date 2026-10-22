package com.example.demo.infrastructure.integration;

import com.example.demo.application.events.*;
import com.networknt.schema.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class M3SchemaValidator {
    private final Map<String, Schema> schemas=new HashMap<>();
    private final EventCodec codec;
    public M3SchemaValidator(EventCodec codec) {
        this.codec=codec;
        var registry=SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        for(String type:List.of("workOrderScheduled","workOrderCompleted")) {
            var schema=registry.getSchema(SchemaLocation.of("classpath:contracts/m3/"+type+".schema.json"));
            schema.initializeValidators(); schemas.put(type,schema);
        }
    }
    public void validate(EventEnvelope event) {
        var schema=schemas.get(event.eventType());
        if(schema==null || !schema.validate(codec.encode(event),InputFormat.JSON,
                context->context.executionConfig(config->config.formatAssertionsEnabled(true))).isEmpty()) throw new InvalidEventException();
    }
}
