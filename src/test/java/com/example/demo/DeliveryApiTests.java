package com.example.demo;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import com.example.demo.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.open-in-view=false")
class DeliveryApiTests {
    @LocalServerPort int port;
    @Autowired OrdenTrabajoRepository orders;
    @Autowired CuadrillaRepository crews;
    @Autowired StreetClosureRepository closures;
    @Autowired DashboardService dashboard;

    private HttpResponse<String> call(String method, String path, String body) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }
    private OrdenTrabajo order(Cuadrilla crew) {
        return orders.save(new OrdenTrabajo(null, OrigenOT.MANUAL, "Prueba entrega", "Calzada", "Lima 700",
                PrioridadOT.ALTA, 6, crew));
    }
    @Test
    void malformedClosuresNeverPersistAndSameDayIsAllowed() throws Exception {
        var id = order(null).getId();
        String body = "{\"workOrderId\":" + id + ",\"location\":\"Lima\",\"affectedSections\":[\"A\",\"B\"],"
                + "\"requestedFrom\":\"2026-09-15\",\"requestedTo\":\"2026-09-15\",\"reason\":\"Obra\"}";
        long count = closures.count();
        for (String invalid : new String[]{"{}", "null", "{", body.replace("[\"A\",\"B\"]", "[null]"),
                body.replace("[\"A\",\"B\"]", "[\" \" ]"), body.replace("2026-09-15", "2026-02-30"),
                body.replace("\"Lima\"", "\"" + "x".repeat(301) + "\""),
                body.replace("\"Obra\"", "\" \"")}) {
            var result = call("POST", "/api/public-works/street-closures", invalid);
            assertEquals(400, result.statusCode(), invalid);
            assertTrue(result.body().contains("VALIDATION_ERROR"));
        }
        assertEquals(count, closures.count());
        assertEquals(201, call("POST", "/api/public-works/street-closures", body).statusCode());
        var emptyPage = call("GET", "/api/public-works/street-closures?page=99999&size=1", null);
        assertEquals(200, emptyPage.statusCode());
        assertTrue(emptyPage.body().contains("\"content\":[]"));
        assertEquals(400, call("GET", "/api/public-works/street-closures?sort=doesNotExist,asc", null).statusCode());
    }

    @Test
    void schedulingWithoutCrewAndInvalidRequestsKeepState() throws Exception {
        var id = order(null).getId();
        var path = "/api/public-works/work-orders/" + id + "/schedule";
        assertEquals(200, call("PATCH", path, "{\"scheduledDate\":\"2026-09-15\"}").statusCode());
        assertEquals(EstadoOT.PROGRAMADA, orders.findById(id).orElseThrow().getStatus());
        assertEquals(400, call("PATCH", path, "{}").statusCode());
        assertEquals(404, call("PATCH", path, "{\"scheduledDate\":\"2026-09-20\",\"crew\":\"missing crew\"}").statusCode());
        assertEquals(LocalDate.of(2026, 9, 15), orders.findById(id).orElseThrow().getScheduledDate());
        var crew = crews.save(new Cuadrilla("Asignacion posterior"));
        assertEquals(200, call("PATCH", path, "{\"scheduledDate\":\"2026-09-20\",\"crew\":\"Asignacion posterior\"}").statusCode());
        assertEquals(crew.getId(), orders.findById(id).orElseThrow().getCuadrilla().getId());
    }
    @Test
    void closureValidatesAndPersistsInPendingState() throws Exception {
        var id = order(null).getId();
        String body = "{\"workOrderId\":" + id + ",\"location\":\"Lima 700\",\"affectedSections\":[\"Lima 700-760\"],"
                + "\"requestedFrom\":\"2026-09-15\",\"requestedTo\":\"2026-09-16\",\"reason\":\"Reparacion\"}";
        long before = closures.count();
        var created = call("POST", "/api/public-works/street-closures", body);
        assertEquals(201, created.statusCode());
        assertTrue(created.body().contains("\"status\":\"PENDIENTE\""));
        assertEquals(before + 1, closures.count());
        assertEquals(422, call("POST", "/api/public-works/street-closures", body.replace("2026-09-16", "2026-09-14")).statusCode());
        assertEquals(400, call("POST", "/api/public-works/street-closures", body.replace("[\"Lima 700-760\"]", "[]")).statusCode());
        assertEquals(404, call("POST", "/api/public-works/street-closures", body.replace("\"workOrderId\":" + id, "\"workOrderId\":999999999")).statusCode());
        assertEquals(before + 1, closures.count());
        var list = call("GET", "/api/public-works/street-closures?page=0&size=10", null);
        assertEquals(200, list.statusCode());
        assertTrue(list.body().contains("Lima 700-760"));
    }
    @Test
    void assignedOrderCanBeScheduledAndRescheduledWithoutLosingCrew() throws Exception {
        var crew = crews.save(new Cuadrilla("Cuadrilla test entrega"));
        var id = order(crew).getId();
        var path = "/api/public-works/work-orders/" + id;
        assertEquals(200, call("PATCH", path + "/schedule", "{\"scheduledDate\":\"2026-09-15\"}").statusCode());
        assertEquals(200, call("PATCH", path + "/schedule", "{\"scheduledDate\":\"2026-09-18\"}").statusCode());
        var saved = orders.findById(id).orElseThrow();
        assertEquals(EstadoOT.ASIGNADA, saved.getStatus());
        assertEquals(crew.getId(), saved.getCuadrilla().getId());
        assertEquals(LocalDate.of(2026, 9, 18), saved.getScheduledDate());
        assertEquals(400, call("PATCH", path + "/schedule", "{\"scheduledDate\":\"2026-09-18\",\"crew\":\" \"}").statusCode());
        assertEquals(200, call("PATCH", path + "/start", null).statusCode());
        assertEquals(409, call("PATCH", path + "/schedule", "{\"scheduledDate\":\"2026-09-19\"}").statusCode());
    }
    @Test
    void dashboardCountsOverdueOpenOrdersAndExcludesCompletedOnes() {
        var before = dashboard.summary();
        var pending = order(null);
        pending.programar(LocalDate.now().minusDays(1), null);
        orders.save(pending);
        var completed = order(null);
        completed.programar(LocalDate.now().minusDays(1), null);
        completed.completar("Terminado");
        orders.save(completed);
        var after = dashboard.summary();
        assertEquals(before.openWorkOrders() + 1, after.openWorkOrders());
        assertEquals(before.delayedWorkOrders() + 1, after.delayedWorkOrders());
    }
}
