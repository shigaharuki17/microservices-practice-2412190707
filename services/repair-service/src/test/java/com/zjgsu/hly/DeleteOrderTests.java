package com.zjgsu.hly;

import com.zjgsu.hly.client.DependencyClient;
import com.zjgsu.hly.client.DependencyClient.*;
import com.zjgsu.hly.dto.RepairRequests.*;
import com.zjgsu.hly.repository.RepairOrderRepository;
import com.zjgsu.hly.service.RepairService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class DeleteOrderTests {
    @Autowired MockMvc mvc;
    @Autowired RepairService service;
    @Autowired RepairOrderRepository orders;
    @MockitoBean DependencyClient dependency;

    @BeforeEach void setup() {
        when(dependency.resident(1L)).thenReturn(new ResidentInfo(1L,"测试住户","13900000101"));
        when(dependency.technician(1L)).thenReturn(new TechnicianInfo(1L,"测试师傅","13800000101","PLUMBING",true));
    }
    private Long create() { return service.create(new Create(1L,"1幢101","删除测试","PLUMBING")).id; }

    @Test void deletesOneOrderAndReturnsEmpty204Then404() throws Exception {
        Long id = create();
        mvc.perform(delete("/api/repair-orders/{id}",id))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertFalse(orders.existsById(id));
        mvc.perform(get("/api/repair-orders/{id}",id)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/repair-orders/{id}",id)).andExpect(status().isNotFound());
    }
    @Test void deletesAcceptedOrderWithoutTouchingOtherOrdersOrRemoteServices() throws Exception {
        Long id = create();
        Long other = create();
        service.review(id,new Review(true,"同意"));
        service.assign(id,new Assign(1L));
        service.accept(id,new Assign(1L));
        clearInvocations(dependency);
        mvc.perform(delete("/api/repair-orders/{id}",id)).andExpect(status().isNoContent());
        assertFalse(orders.existsById(id));
        assertTrue(orders.existsById(other));
        assertEquals(1,orders.count());
        verifyNoInteractions(dependency);
    }
    @Test void missingOrderDoesNotDeleteExistingData() throws Exception {
        Long id = create();
        mvc.perform(delete("/api/repair-orders/{id}",Long.MAX_VALUE)).andExpect(status().isNotFound());
        assertTrue(orders.existsById(id));
        assertEquals(1,orders.count());
    }
}
