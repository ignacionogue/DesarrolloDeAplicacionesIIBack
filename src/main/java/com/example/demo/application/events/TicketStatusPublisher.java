package com.example.demo.application.events;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Validates the agreed M2 contract before appending to the business transaction. */
@Service
public class TicketStatusPublisher {
    private final ObjectMapper json;
    private final EventActor actor;
    private final TicketEventValidation validation;
    private final EventOutbox outbox;
    public TicketStatusPublisher(ObjectMapper json, EventActor actor, TicketEventValidation validation, EventOutbox outbox) {
        this.json=json; this.actor=actor; this.validation=validation; this.outbox=outbox;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(UUID ticketId, String updateType, ObjectNode contents, UUID correlationId, UUID cause) {
        // Callers may provide only contract content, not impersonate an actor or replace identity.
        if (contents != null && contents.propertyNames().stream().anyMatch(name ->
                !java.util.Set.of("progress", "publicMessage", "internalMessage", "details", "attachments").contains(name)))
            throw new InvalidEventException();
        ObjectNode data=contents == null ? json.createObjectNode() : contents.deepCopy();
        data.put("ticketId", ticketId.toString());
        data.put("updateType", updateType);
        data.put("updateOccurredAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        var person=actor.currentPersonId();
        var updatedBy=data.putObject("updatedBy");
        updatedBy.put("type", person == null ? "SYSTEM" : "EXTERNAL_USER");
        if(person == null) updatedBy.putNull("id"); else updatedBy.put("id",person.toString());
        var event=EventEnvelope.outgoing("updateTicketStatus", data, correlationId, cause);
        validation.validate(event);
        outbox.append(event, "ticket:" + ticketId);
    }
}
