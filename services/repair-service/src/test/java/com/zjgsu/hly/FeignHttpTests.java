package com.zjgsu.hly;
import com.sun.net.httpserver.HttpServer;
import com.zjgsu.hly.client.DependencyClient;
import com.zjgsu.hly.dto.PortalRequests.ResidentCreate;
import com.zjgsu.hly.error.BusinessException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc
class FeignHttpTests {
    static final AtomicInteger mode=new AtomicInteger(200), calls=new AtomicInteger();
    static final AtomicReference<String> posted=new AtomicReference<>();
    static final HttpServer remote;
    static {
        try {
            remote=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            remote.createContext("/api/", ex -> {
                calls.incrementAndGet(); int status=mode.get();
                if(status==998) { try {Thread.sleep(600);} catch(InterruptedException ignored) {} status=200; }
                String request=new String(ex.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
                if(ex.getRequestMethod().equals("POST")) posted.set(request);
                if(ex.getRequestURI().getPath().endsWith("/999")) status=404;
                String json=ex.getRequestURI().getPath().contains("technicians") ?
                    "{\"id\":1,\"name\":\"Test plumber\",\"phone\":\"13800000101\",\"skill\":\"PLUMBING\",\"available\":true}" :
                    "{\"id\":1,\"name\":\"Test resident\",\"phone\":\"13900000101\"}";
                if(ex.getRequestURI().getPath().equals("/api/residents") && ex.getRequestMethod().equals("GET")) json="["+json+"]";
                if(status==997) {ex.sendResponseHeaders(204,-1);ex.close();return;}
                if(ex.getRequestMethod().equals("POST") && status==200) status=201;
                byte[] bytes=json.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type","application/json");
                ex.getResponseHeaders().set("X-Instance-ID","test-user-1");
                try {ex.sendResponseHeaders(status,bytes.length);ex.getResponseBody().write(bytes);}
                catch(java.io.IOException ignored) {} finally {ex.close();}
            });remote.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());remote.start();
        } catch(Exception e) {throw new ExceptionInInitializerError(e);}
    }
    @DynamicPropertySource static void urls(DynamicPropertyRegistry registry) {
        for(String service:new String[]{"user-service","technician-service"}) {
            registry.add("spring.cloud.openfeign.client.config."+service+".url",
                () -> "http://127.0.0.1:"+remote.getAddress().getPort());
            registry.add("spring.cloud.openfeign.client.config."+service+".readTimeout", () -> 200);
        }
    }
    @Autowired DependencyClient client;
    @Autowired MockMvc mvc;
    @BeforeEach void reset() {mode.set(200);calls.set(0);posted.set(null);}
    @AfterAll static void close() {remote.stop(0);}
    @Test void getsAndListsUsingActualFeignDecoder() {
        assertEquals(1L,client.resident(1L).id());
        assertEquals(1,client.residents().size());
        assertEquals("PLUMBING",client.technician(1L).skill());
    }
    @Test void postForwardsJsonAndDecodes201() {
        assertEquals(1L,client.createResident(new ResidentCreate("Test resident","13900000101")).id());
        assertTrue(posted.get().contains("13900000101"));
    }
    @Test void missingResourcePreserves404() {
        assertEquals(404,assertThrows(BusinessException.class,()->client.resident(999L)).status);
    }
    @Test void createConflictIsNotReportedAsSuccessOrRetried() {
        mode.set(409);
        assertEquals(409,assertThrows(BusinessException.class,
            ()->client.createResident(new ResidentCreate("Test resident","13900000101"))).status);
        assertEquals(1,calls.get());
    }
    @Test void unavailableIs503AndDoesNotRetry() {
        mode.set(503);
        assertEquals(503,assertThrows(BusinessException.class,()->client.resident(1L)).status);
        assertEquals(1,calls.get());
    }
    @Test void timeoutWarnsThatOutcomeMayBeUnknown() {
        mode.set(998);
        var error=assertThrows(BusinessException.class,()->client.resident(1L));
        assertEquals(503,error.status);assertTrue(error.getMessage().contains("结果可能未知"));
    }
    @Test void emptySuccessfulResponseIsNotBusinessSuccess() {
        mode.set(997);
        assertEquals(502,assertThrows(BusinessException.class,()->client.resident(1L)).status);
    }
    @Test void forwardsActualCalleeInstanceHeaderToPortalResponse() throws Exception {
        mvc.perform(get("/api/portal/residents/1")).andExpect(status().isOk())
            .andExpect(header().string("X-Dependency-Instance","test-user-1"))
            .andExpect(jsonPath("$.id").value(1));
    }
}
