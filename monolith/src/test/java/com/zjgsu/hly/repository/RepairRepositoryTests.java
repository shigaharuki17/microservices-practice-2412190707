package com.zjgsu.hly.repository;

import com.zjgsu.hly.model.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class RepairRepositoryTests {
    @Autowired TechnicianRepository technicians;
    @Autowired RepairOrderRepository orders;
    @Autowired EntityManager entityManager;

    @Test void writesAndReloadsOrderAndTechnicianFromDatabase() {
        var tech = technicians.saveAndFlush(new Technician("王师傅", "13800001001", RepairCategory.PLUMBING, true));
        var order = new RepairOrder("住户甲", "13900001001", "1栋101", "水管漏水", RepairCategory.PLUMBING);
        order.review(true, "同意维修");
        order.assign(tech);
        orders.saveAndFlush(order);
        Long id = order.getId();
        entityManager.clear(); // Clear first-level cache to verify a database read.
        var loaded = orders.findById(id).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(loaded.getTechnician().getName()).isEqualTo("王师傅");
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test void databaseRejectsDuplicateTechnicianPhone() {
        technicians.saveAndFlush(new Technician("甲", "13800001002", RepairCategory.LOCK, true));
        assertThatThrownBy(() -> technicians.saveAndFlush(
            new Technician("乙", "13800001002", RepairCategory.LOCK, true)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
