package com.example.demo.application.events;

import tools.jackson.databind.JsonNode;

final class ClosureEventValidation {
    static void base(EventEnvelope e) {EnvironmentEventFields.uuid(e.data(),"closureRequestId");}
    static void responseToRequest(EventEnvelope e) {
        base(e);
        if(e.correlationId()==null || e.causationId()==null) throw new InvalidEventException();
    }
    static void sections(JsonNode array) {
        if(!array.isArray() || array.isEmpty()) throw new InvalidEventException();
        for(var section:array) {
            for(String field:java.util.List.of("street","from","to")) EnvironmentEventFields.text(section,field);
            if(!java.util.Set.of("FORWARD","REVERSE","BOTH").contains(section.path("direction").asString())) throw new InvalidEventException();
        }
    }
    static boolean relevant(EventEnvelope e) {return !e.data().has("requestingModule") || e.data().path("requestingModule").asString().equals("obras");}
}
