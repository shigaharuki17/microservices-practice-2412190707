package com.zjgsu.hly;

import com.sun.net.httpserver.HttpServer;
import com.zjgsu.hly.client.DependencyClient;
import com.zjgsu.hly.dto.PortalRequests.ResidentCreate;
import com.zjgsu.hly.error.BusinessException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortalHttpTests {
    @Test void forwardsListAndCreateUsingDiscoveredAddress() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var posted = new AtomicReference<String>();
        server.createContext("/api/residents", ex -> {
            boolean post = ex.getRequestMethod().equals("POST");
            if(post) posted.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String json = post ? "{\"id\":7,\"name\":\"Alice\",\"phone\":\"13900000007\"}" :
                    "[{\"id\":7,\"name\":\"Alice\",\"phone\":\"13900000007\"}]";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type","application/json");
            ex.sendResponseHeaders(post?201:200,bytes.length);
            try(var out = ex.getResponseBody()) {out.write(bytes);}
        });
        server.start();
        try {
            var discovery = mock(DiscoveryClient.class);
            when(discovery.getInstances("user-service")).thenReturn(List.of(
                    new DefaultServiceInstance("one","user-service","127.0.0.1",server.getAddress().getPort(),false)));
            var client = new DependencyClient(discovery,"user-service","technician-service",2000,3000);
            assertEquals(7L,client.residents().getFirst().id());
            assertEquals(7L,client.createResident(new ResidentCreate("Alice","13900000007")).id());
            assertTrue(posted.get().contains("13900000007"));
            verify(discovery,times(2)).getInstances("user-service");
        } finally {server.stop(0);}
    }
    @Test void remoteCreateConflictIsNotReportedAsSuccess() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/residents", ex -> {ex.getRequestBody().readAllBytes();ex.sendResponseHeaders(409,-1);ex.close();});
        server.start();
        try {
            var discovery = mock(DiscoveryClient.class);
            when(discovery.getInstances("user-service")).thenReturn(List.of(
                    new DefaultServiceInstance("one","user-service","127.0.0.1",server.getAddress().getPort(),false)));
            var client = new DependencyClient(discovery,"user-service","technician-service",2000,3000);
            assertEquals(409,assertThrows(BusinessException.class,
                    () -> client.createResident(new ResidentCreate("Alice","13900000007"))).status);
        } finally {server.stop(0);}
    }
}
