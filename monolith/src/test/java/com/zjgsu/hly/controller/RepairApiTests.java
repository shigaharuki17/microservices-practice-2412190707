package com.zjgsu.hly.controller;

import java.net.URI;
import java.net.http.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) @ActiveProfiles("test")
class RepairApiTests {
    @LocalServerPort int port;
    static final AtomicLong PHONE = new AtomicLong(13800003000L);
    ObjectMapper mapper = new ObjectMapper();
    HttpResponse<String> request(String method, String path, Object body) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").header("Accept", "application/json");
            builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
    Map<String, Object> orderRequest() {
        return Map.of("residentName", "接口住户", "contactPhone", "13900003000", "location", "3栋301",
            "description", "水管漏水", "category", "PLUMBING");
    }
    long id(HttpResponse<String> response) { return mapper.readTree(response.body()).get("id").asLong(); }
    @Test void normalWorkflowReturnsCreatedAndPersistsAcceptedOrder() throws Exception {
        var technician = request("POST", "/api/technicians", Map.of("name", "水管工",
            "phone", Long.toString(PHONE.incrementAndGet()), "skill", "PLUMBING", "available", true));
        assertThat(technician.statusCode()).isEqualTo(201);
        var created = request("POST", "/api/repair-orders", orderRequest());
        assertThat(created.statusCode()).isEqualTo(201);
        String path = "/api/repair-orders/" + id(created);
        assertThat(created.headers().firstValue("Location")).contains(path);
        assertThat(request("POST", path + "/review", Map.of("approved", true, "note", "通过")).statusCode()).isEqualTo(200);
        assertThat(request("POST", path + "/assign", Map.of("technicianId", id(technician))).statusCode()).isEqualTo(200);
        assertThat(request("POST", path + "/accept", Map.of("technicianId", id(technician))).statusCode()).isEqualTo(200);
        var query = request("GET", path, null);
        assertThat(query.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(query.body()).get("status").asString()).isEqualTo("ACCEPTED");
        assertThat(request("GET", "/api/technicians", null).statusCode()).isEqualTo(200);
        assertThat(request("GET", "/api/repair-orders", null).statusCode()).isEqualTo(200);
    }
    @Test void invalidInputReturns400() throws Exception {
        var response = request("POST", "/api/repair-orders", Map.of("residentName", ""));
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(mapper.readTree(response.body()).get("status").asInt()).isEqualTo(400);
    }
    @Test void unknownOrderReturns404() throws Exception {
        assertThat(request("GET", "/api/repair-orders/9223372036854775807", null).statusCode()).isEqualTo(404);
    }
    @Test void invalidWorkflowReturns409() throws Exception {
        var order = request("POST", "/api/repair-orders", orderRequest());
        var response = request("POST", "/api/repair-orders/" + id(order) + "/assign", Map.of("technicianId", 1));
        assertThat(response.statusCode()).isEqualTo(409);
    }
    @Test void invalidEnumReturns400() throws Exception {
        var response = request("POST", "/api/technicians", Map.of("name", "测试", "phone", "13800003999",
            "skill", "UNKNOWN", "available", true));
        assertThat(response.statusCode()).isEqualTo(400);
    }
}
