package com.example.demo;

import com.example.demo.application.events.EventDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class IntegrationApiTests {
    @LocalServerPort int port;
    @Autowired TestUsers users;
    @Autowired EventDispatcher dispatcher;
    @Autowired ObjectMapper json;
    HttpResponse<String> post(String path,String role,String body) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");
        if(role!=null) request.header("Authorization","Bearer "+users.issueToken("integration",role));
        return HttpClient.newHttpClient().send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    UUID ticket() throws Exception {
        var e=(ObjectNode)json.readTree(Files.readString(Path.of("src/test/resources/contracts/m2-eventos-v1.78/examples/valid/ticketUpdated_ROUTED.json")));
        UUID id=UUID.randomUUID(); e.put("eventId",UUID.randomUUID().toString()); ((ObjectNode)e.path("data")).put("ticketId",id.toString());
        dispatcher.dispatch(e.toString().getBytes(StandardCharsets.UTF_8)); return id;
    }
    @Test void ticketDecisionsAreAuthorizedValidatedAndCannotResolveUnfinishedWork() throws Exception {
        var path="/api/public-works/tickets/"+ticket()+"/updates";
        String info="{\"updateType\":\"INFORMATION_REQUIRED\",\"details\":{\"informationRequest\":{\"messageForCitizen\":\"Indique la ubicacion exacta\"}}}";
        assertEquals(401,post(path,null,info).statusCode());
        assertEquals(403,post(path,"INSPECTOR_OBRA",info).statusCode());
        assertEquals(202,post(path,"PERSONAL_OBRAS",info).statusCode());
        for(String body:java.util.List.of("{\"updateType\":\"INFORMATION_REQUIRED\"}","{\"updateType\":\"RETURNED\"}","{\"updateType\":\"REJECTED\"}")) {
            var response=post(path,"PERSONAL_OBRAS",body); assertEquals(400,response.statusCode());
            assertTrue(json.readTree(response.body()).has("message")); assertTrue(json.readTree(response.body()).has("code")); assertTrue(json.readTree(response.body()).has("details"));
        }
        assertEquals(409,post(path,"PERSONAL_OBRAS","{\"updateType\":\"RESOLVED\",\"publicMessage\":\"Finalizado\",\"details\":{\"resolution\":{\"type\":\"ACTION_COMPLETED\"}}}").statusCode());
        assertEquals(202,post(path,"PERSONAL_OBRAS","{\"updateType\":\"RETURNED\",\"internalMessage\":\"Falta contexto\",\"details\":{\"returnInfo\":{\"reasonCode\":\"INSUFFICIENT_CONTEXT\"}}}").statusCode());
        assertEquals(202,post(path,"PERSONAL_OBRAS","{\"updateType\":\"REJECTED\",\"publicMessage\":\"Fuera del alcance de Obras\",\"details\":{\"cancellation\":{\"reasonCode\":\"OUT_OF_SCOPE\"}}}").statusCode());
    }
    @Test void m7UuidProposalCannotBeEnabledAccidentallyAndDateOnlyInputIsRejected() throws Exception {
        var path="/api/public-works/street-closures/integration";
        String body="{\"workOrderId\":1,\"reason\":\"Reparacion\",\"affectedSections\":[{\"street\":\"A\",\"from\":\"B\",\"to\":\"C\",\"direction\":\"BOTH\"}],\"requestedFrom\":\"2026-10-21T10:00:00-03:00\",\"requestedTo\":\"2026-10-21T11:00:00-03:00\"}";
        assertEquals(403,post(path,"OPERARIO_CONTRATISTA",body).statusCode());
        assertEquals(409,post(path,"PERSONAL_OBRAS",body).statusCode());
        assertEquals(400,post(path,"PERSONAL_OBRAS",body.replace("2026-10-21T10:00:00-03:00","2026-10-21")).statusCode());
        var invalid=(ObjectNode)json.readTree(body); invalid.putArray("affectedSections").addNull();
        assertEquals(400,post(path,"PERSONAL_OBRAS",invalid.toString()).statusCode());
    }
}
