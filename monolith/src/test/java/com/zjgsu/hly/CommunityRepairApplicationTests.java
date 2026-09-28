package com.zjgsu.hly;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CommunityRepairApplicationTests {

    @Autowired
    private ApplicationContext context;

    @LocalServerPort
    private int port;

    @Test
    void contextLoads() {
        assertThat(context.getBean(CommunityRepairApplication.class)).isNotNull();
    }

    @Test
    void statusEndpointReturnsApplicationAndUp() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/status"))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type").orElse(""))
                    .startsWith("application/json");
            var body = new ObjectMapper().readTree(response.body());
            assertThat(body.get("application").asString()).isEqualTo("community-repair");
            assertThat(body.get("status").asString()).isEqualTo("UP");
        }
    }

    @Test
    void helloEndpointReturnsProjectName() throws Exception {
        var body = getJson("/api/hello");
        assertThat(body.get("project").asString()).isEqualTo("社区报修与上门维修服务平台");
        assertThat(body.get("message").asString()).isEqualTo("欢迎使用社区报修平台！");
    }

    @Test
    void actuatorHealthReturnsUp() throws Exception {
        var body = getJson("/actuator/health");
        assertThat(body.get("status").asString()).isEqualTo("UP");
    }

    private tools.jackson.databind.JsonNode getJson(String path) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + path))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type").orElse(""))
                    .startsWith("application/json");
            return new ObjectMapper().readTree(response.body());
        }
    }
}
