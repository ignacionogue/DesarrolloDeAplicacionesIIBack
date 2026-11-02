package com.example.demo.infrastructure.integration;

import com.example.demo.application.events.*;
import com.networknt.schema.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class M3SchemaValidator implements OutgoingEventValidator {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(M3SchemaValidator.class);
    private final Map<String, Schema> schemas=new HashMap<>();
    private final EventCodec codec;
    public M3SchemaValidator(EventCodec codec) {
        this.codec=codec;
        var registry=SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        for(String type:List.of("workOrderScheduled","workOrderCompleted","streetClosureRequested",
                "publicWorksProjectApproved","publicWorksProjectCompleted")) {
            var schema=registry.getSchema(SchemaLocation.of("classpath:contracts/m3/"+type+".schema.json"));
            schema.initializeValidators(); schemas.put(type,schema);
        }
    }
    @Override public void validate(EventEnvelope event) {
        var schema=schemas.get(event.eventType());
        if(schema==null) throw new InvalidEventException();
        var errors=schema.validate(codec.encode(event),InputFormat.JSON,
                context->context.executionConfig(config->config.formatAssertionsEnabled(true)));
        if(!errors.isEmpty()) {
            log.warn("Outgoing event rejected by schema eventType={} errors={}",event.eventType(),errors);
            throw new InvalidEventException();
        }
    }
}
