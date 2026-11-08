package com.zjgsu.hly;

import com.zjgsu.hly.client.DependencyClient;
import com.zjgsu.hly.client.DependencyClient.*;
import com.zjgsu.hly.dto.PortalRequests.*;
import com.zjgsu.hly.error.BusinessException;
import java.util.List;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc
class PortalTests {
    @Autowired MockMvc mvc;
    @MockitoBean DependencyClient dependencies;

    @Test void servesDashboardWithoutExternalAssets() throws Exception {
        var html = mvc.perform(get("/index.html")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("工单工作台"));
        mvc.perform(get("/app.js")).andExpect(status().isOk());
    }
    @Test void listsResidentsThroughOwningService() throws Exception {
        when(dependencies.residents()).thenReturn(List.of(new ResidentInfo(7L,"住户","13900000007")));
        mvc.perform(get("/api/portal/residents")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7));
    }
    @Test void createsResidentWithLocationHeader() throws Exception {
        when(dependencies.createResident(any())).thenReturn(new ResidentInfo(8L,"住户","13900000008"));
        mvc.perform(post("/api/portal/residents").contentType("application/json")
                .content("{\"name\":\"住户\",\"phone\":\"13900000008\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location","/api/portal/residents/8"));
    }
    @Test void rejectsInvalidInputBeforeCallingDependency() throws Exception {
        mvc.perform(post("/api/portal/technicians").contentType("application/json")
                .content("{\"name\":\"\",\"phone\":\"bad\",\"skill\":\"UNKNOWN\",\"available\":true}"))
                .andExpect(status().isBadRequest());
        verify(dependencies, never()).createTechnician(any());
    }
    @Test void unavailableDependencyRemains503() throws Exception {
        when(dependencies.residents()).thenThrow(new BusinessException(503,"依赖服务没有可用实例: user-service"));
        mvc.perform(get("/api/portal/residents")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(containsString("user-service")));
    }
    @Test void duplicatePhoneRemains409() throws Exception {
        when(dependencies.createResident(any())).thenThrow(new BusinessException(409,"联系电话已存在"));
        mvc.perform(post("/api/portal/residents").contentType("application/json")
                .content("{\"name\":\"住户\",\"phone\":\"13900000008\"}"))
                .andExpect(status().isConflict());
    }
}
